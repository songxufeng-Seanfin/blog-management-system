# 在线博客管理系统 · 技术文档

> 项目 5 / 两周行动计划 · Java 方向
> v1.0 · 2026-11-20 · Spring Boot 3.2.5 + MyBatis 3.0.3 + H2 + 手写 JWT + Vue 3
> 请求从浏览器进来，先过安检口验通行证，Controller 收和回，Service 定规则，Mapper 翻成 SQL，H2 存数据；中间加了一层缓存，重复的问题不用每次都问数据库。

---

## 0. 这份文档怎么读

前四个项目用的是 Python / Django，这个项目第一次换到 Java / Spring Boot。但**骨架是同一套**：都是「路由 → 视图 → 模型 → 数据库」，只是每个零件的名字变了。

文档从用户能点什么开始，接着是请求怎么走、代码分几层，最后才进到 JWT、SQL、缓存这些细节。专有名词第一次出现时用括号解释。只想弄清「做了什么」，读第 1、2、3、14 节；要动手改代码再往下看。

---

## 1. 这个项目交付了什么

一个能真跑、能点、能写数据的在线博客系统，外加一块「把内部动作摊开给你看」的演示看板。

| 维度 | 成果 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Java 17 · 三层架构（Controller / Service / Mapper），30 个 Java 源文件 |
| 持久层 | MyBatis 3.0.3 注解 + 动态 SQL；H2 嵌入式数据库，开 MySQL 兼容模式；5 张表 |
| 认证 | 手写 JWT（HMAC-SHA256），不引入第三方库；读接口公开、写接口必须带 token |
| 缓存 | Redis 可选，探测不到就自动回退 JVM 内存；命中 / 回源 / 写入 / 失效全部留流水 |
| 前端 | Vue 3（本地引入，离线可用）+ 原生 CSS，深浅色自适应、手机端适配 |
| 看板 | 6 张统计卡、四层架构图、13 步一键演示、缓存事件时间线、API 请求流水 |
| 测试 | 11 个 JUnit 5 进程内测试 + 23 条真实 HTTP 端到端回归 + 无头浏览器自动演示 |
| 启动 | `python start.py` 一条命令：自动下载项目内 Maven、打包、起服务，不改系统环境变量 |

演示地址默认是 `http://127.0.0.1:8003/`。打开就能看到 8 篇演示文章（6 已发布 / 2 草稿）、4 个分类、6 条评论。

> **白话**：所谓「项目」，不是一堆代码文件，而是「有人点一下 → 有东西真的变了 → 还能看见它怎么变的」这条闭环。看板就是把这条闭环画出来。

---

## 2. 从点一下开始：一次请求的完整往返

以「点开一篇文章」为例，浏览器只发了一行：

```
GET /api/articles/7/
```

它走过这六站：

| 站 | 谁负责 | 做了什么 |
|---|---|---|
| ① 浏览器 | `static/app.js` 的 `request()` | 拼好 URL，用 `fetch` 发出去；带上 `Content-Type: application/json` |
| ② Tomcat | Spring Boot 内嵌服务器 | 监听 8003，把 HTTP 报文变成 Java 里的 `HttpServletRequest` |
| ③ 安检口 | `AuthInterceptor.preHandle()` | 判断这是否是需要登录的操作；GET 一律放行，写操作要验 JWT |
| ④ Controller | `ArticleController.detail()` | 从 URL 里取出 `id=7`，调用 Service，把结果包进 `ApiResult` |
| ⑤ Service | `ArticleService.detail()` | 先问缓存：有就直接返回（HIT）；没有就查库、浏览量 +1、写回缓存（MISS） |
| ⑥ Mapper → H2 | `ArticleMapper.findById()` | 把方法调用翻成一条 `SELECT ... LEFT JOIN ...`，H2 执行后把行填回 `Article` 对象 |

回来的时候倒着走一遍：对象 → `ApiResult` → Jackson 序列化成 JSON → HTTP 200 → `fetch` 解析 → Vue 的响应式数据更新 → 页面局部重绘，**整页不刷新**。

同时，安检口在 `afterCompletion()` 里记一条「方法 / 路径 / 状态码 / 耗时」，缓存层记一条「哪个 key、命中还是回源」。这两条流水就是看板下半部分的两张表。

> **白话**：`Controller` 是前台，`Service` 是后厨，`Mapper` 是采购单，`数据库` 是仓库。前台不切菜，后厨不接客。

---

## 3. 技术栈与白话对照

| 名词 | 在项目里 | 白话解释 |
|---|---|---|
| Spring Boot | 整个后端框架 | Java 版的 Django：把 Web 服务器、对象管理、配置读取都装配好，不用自己拼 |
| IoC / DI | 构造器注入 | 「我要用谁」不由自己 new，而是启动时由框架递进来；换实现只改一处 |
| `@RestController` | 三个 Controller 类 | 标记「这个类的方法返回值直接写成 JSON，不是网页」 |
| MyBatis | `mapper/` 下的接口 | 手写 SQL 的翻译员：接口方法 ↔ 一条 SQL，结果行 ↔ 一个 Java 对象 |
| H2 | `runtime/blogdb.mv.db` | 不用安装的数据库，一个文件就是一整个库；`MODE=MySQL` 让它说 MySQL 方言 |
| JWT | `JwtUtil` | 三段式通行证：`头.载荷.签名`。前两段只是 Base64 编码（能看懂），第三段才是防篡改的钢印 |
| HMAC-SHA256 | `Mac.getInstance("HmacSHA256")` | 用只有服务器知道的密钥给内容盖钢印；改一个字，钢印就对不上 |
| Interceptor | `AuthInterceptor` | 安检口：请求进 Controller 之前统一拦一道 |
| DTO / record | `dto/Dtos.java` | 数据在层与层之间传递的小盒子；Java 17 的 `record` 一行声明好字段且不可改 |
| Redis | 可选依赖 | 独立的缓存服务；本机没装，所以自动回退到 JVM 内存 |
| 缓存失效 | `evictPrefix()` | 数据一改就删掉旧缓存，否则页面会一直显示旧内容 |
| Vue 3 | `static/vendor/vue.global.prod.js` | 数据变了页面自己跟着变，不用手动操作 DOM |
| Maven | `tools/apache-maven-3.9.9` | Java 的依赖管家 + 打包工；只放在项目内，不装到系统 |

**为什么 H2 不是 MySQL？** 计划里写的是 MySQL + Redis，但演示项目要能「一条命令跑起来」。H2 开 `MODE=MySQL` 后，SQL 方言、自增主键、常用函数都和 MySQL 一致；将来换真 MySQL，只改 `application.yml` 里的 `spring.datasource` 四行即可（第 15 节给了示例）。Redis 同理，代码里已经写好，探测到就自动启用。

---

## 4. 目录结构：哪个文件管什么

```
blog-management-system/
├── pom.xml                         依赖清单（Spring Boot / MyBatis / H2 / Redis / JUnit）
├── start.py                        一键启动：找 JDK → 下载 Maven → 打包 → 起服务
├── docs/                           技术文档 / 博客 / 离线阅读版 / 生成脚本
├── tests/e2e_api.py                23 条真实 HTTP 端到端回归（标准库，零依赖）
├── reports/e2e_result.json         回归结果样例
├── src/main/resources/
│   ├── application.yml             端口、数据源、JWT、缓存、日志配置
│   ├── schema.sql                  5 张表的建表语句（CREATE TABLE IF NOT EXISTS）
│   └── static/
│       ├── index.html              单页看板（Vue 模板）
│       ├── app.js                  所有前端逻辑：请求封装、一键演示、增删改查
│       ├── style.css               自包含样式（深浅色 / 手机端）
│       └── vendor/vue.global.prod.js   本地 Vue 3，离线可用
├── src/main/java/com/xufeng/blog/
│   ├── BlogApplication.java        入口：main + @MapperScan
│   ├── common/                     安检口、统一响应、异常、JWT、请求流水
│   ├── dto/Dtos.java               请求 / 响应的小盒子（record）
│   ├── entity/                     5 个实体类，对应 5 张表
│   ├── mapper/                     5 个 Mapper 接口，SQL 都写在注解里
│   ├── service/                    业务规则：认证、文章、评论、分类、缓存、统计、种子
│   └── controller/                 3 个 Controller，共 18 个接口
└── src/test/                       11 个 JUnit 5 测试 + 独立内存库配置
```

> **白话**：`entity` 是「数据长什么样」，`mapper` 是「怎么把数据取出来」，`service` 是「取出来之后怎么办」，`controller` 是「外面怎么问」，`common` 是「大家都要用的工具」。

---

## 5. 环境与一键启动

本机实测：Python 3.13、JDK 17.0.12（另有 JDK 26 但没进 PATH）、Node 22、无 Redis。Maven 原本没装。

`start.py` 的处理方式：

1. `find_java_home()` 先看 `JAVA_HOME`，再试几个常见路径，最后退回 `which java`
2. `ensure_maven()` 检查 `tools/apache-maven-3.9.9`，没有就从 Maven Central 下载 9MB 的 zip 解压到项目内
3. 设置子进程 `JAVA_HOME`，执行 `mvn -B -q -DskipTests package`
4. 执行 `java -jar target/blog-app.jar --server.port=8003`

```bash
python start.py                # 构建并启动
python start.py --build-only   # 只打包
python tests/e2e_api.py        # 另开一个终端跑 23 条回归
```

**关键约束**：Maven 和 H2 数据库都不入库（`.gitignore` 排除 `tools/` 和 `runtime/`），换台机器 clone 下来重跑 `start.py` 即可；`runtime/blogdb` 只在表为空时灌演示数据，**重启不会冲掉自己写的文章**。

端口那行特意写成命令行参数 `--server.port=8003`，原因见第 16 节的踩坑复盘。

---

## 6. 数据模型：5 张表

| 表 | 关键字段 | 作用 |
|---|---|---|
| `blog_user` | `username`(唯一), `password_hash`, `nickname`, `role` | 账号；密码只存哈希，不存明文 |
| `category` | `name`, `slug`, `sort_no` | 文章分类 |
| `article` | `title`, `summary`, `content`, `category_id`, `author_id`, `status`, `view_count`, `published_at` | 文章主体；`status` 只有 `draft` / `published` |
| `comment` | `article_id`, `author_name`, `content`, `status` | 评论 |
| `cache_event` | `kind`, `cache_key`, `result`, `detail` | 缓存动作流水，看板据此画时间线 |

建表语句用 `CREATE TABLE IF NOT EXISTS`，`spring.sql.init.mode=always` 每次启动都执行但不会重建，所以数据能跨重启保留。`article` 表上有 `status` 索引，`comment` 表上有 `article_id` 索引。

`Article` 实体里还有 `categoryName` 和 `authorName` 两个字段，它们**不是表字段**，而是 `LEFT JOIN` 查出来的别名列。MyBatis 开了 `map-underscore-to-camel-case`，`category_name` 会自动填进 `categoryName`。

`DemoSeeder` 只在 `article` 表为空时灌 4 用户 / 4 分类 / 8 文章 / 6 评论；看板上的「重置演示数据」是显式按钮，才会清空文章、评论和事件流水（保留账号与分类）。

---

## 7. 三层架构：为什么非要分三份

以「发布文章」为例，请求 `POST /api/articles/7/publish/` 的三层分工：

```java
// Controller：只管接和回
@PostMapping("/{id}/publish/")
public ApiResult<Article> publish(@PathVariable Long id) {
    return ApiResult.ok(service.publish(id));
}

// Service：规则在这里
public Article publish(Long id) {
    Article a = articles.findById(id);
    if (a == null) throw new BusinessException(404, "文章不存在：" + id);
    articles.publish(id, LocalDateTime.now());   // status + published_at
    invalidate(id);                              // 缓存失效
    return articles.findById(id);
}

// Mapper：只写 SQL
@Update("UPDATE article SET status = 'published', published_at = #{now}, "
      + "updated_at = #{now} WHERE id = #{id}")
int publish(@Param("id") Long id, @Param("now") LocalDateTime now);
```

分开的好处很实际：

- 校验「文章是否存在」只在 Service 写一次，任何入口都绕不过
- 缓存失效只在 Service 写一次，不会出现「改了库忘了清缓存」
- 换数据库只动 Mapper，Controller 一行不改
- 测试可以只测 Service，不必起 Web 服务器

`GlobalExceptionHandler`（`@RestControllerAdvice`）兜住所有异常，把 `BusinessException(404, ...)` 统一变成 `{ "code": 404, "message": "..." }`。前端只认一种响应形状，不用到处写 try/catch。

---

## 8. 接口清单（18 个）

| 方法 | 路径 | 需要 token | 作用 |
|---|---|---|---|
| GET | `/api/health/` | 否 | 健康检查，返回 Java 版本与数据库类型 |
| POST | `/api/auth/register/` | 否 | 注册 |
| POST | `/api/auth/login/` | 否 | 登录，返回 JWT |
| GET | `/api/auth/me/` | **是** | 我是谁（唯一的「读也要 token」例外） |
| GET | `/api/articles/` | 否 | 分页 / 状态 / 分类 / 关键词筛选 |
| GET | `/api/articles/{id}/` | 否 | 详情，走缓存并 +1 浏览 |
| POST | `/api/articles/` | 是 | 新建文章（草稿或直接发布） |
| PUT | `/api/articles/{id}/` | 是 | 修改 |
| DELETE | `/api/articles/{id}/` | 是 | 删除（连带删评论） |
| POST | `/api/articles/{id}/publish/` | 是 | 草稿 → 已发布 |
| POST | `/api/articles/{id}/unpublish/` | 是 | 已发布 → 草稿 |
| GET | `/api/articles/{id}/comments/` | 否 | 评论列表 |
| POST | `/api/articles/{id}/comments/` | 是 | 新增评论 |
| GET | `/api/categories/` | 否 | 分类列表（缓存演示常用） |
| GET | `/api/stats/overview/` | 否 | 统计卡数据 |
| GET | `/api/demo/state/` | 否 | 看板一次拿齐所有面板数据 |
| POST | `/api/demo/reset/` | 否 | 重置演示数据 |
| POST | `/api/cache/switch/` | 否 | 切换缓存后端 |

统一响应外壳：

```json
{ "code": 0, "message": "ok", "data": { ... } }
```

`code = 0` 表示成功；业务错误用 `code = 400/401/404/409`，HTTP 状态仍可能是 200（由全局异常处理器返回）；只有安检口直接拒掉的未授权请求，HTTP 状态才是真正的 401。区分这两者是写测试时最容易踩的坑。

---

## 9. 安检口与手写 JWT

`AuthInterceptor` 拦所有 `/api/**`，规则是：

- `/api/health`、`/api/auth/login`、`/api/auth/register`、`/api/demo/**`、`/api/cache/**`、`/api/stats/**` 放行
- `/api/auth/me` 无论什么方法都要验 token（因为要往请求里塞 `userId`）
- 其余 GET 放行；POST / PUT / DELETE 必须带 `Authorization: Bearer <token>`

JWT 不引第三方库，用 JDK 自带类实现：

```java
// 1. 头：固定字符串做 Base64Url
// 2. 载荷：sub / uid / iat / exp
// 3. 签名：HMAC-SHA256(头 + "." + 载荷, 密钥)
String signingInput = HEADER + "." + body;
return signingInput + "." + sign(signingInput);
```

校验时做三件事：重新算一遍签名、`MessageDigest.isEqual()` 做常量时间比较（避免用响应时间猜签名）、检查 `exp` 是否过期。任一不过就返回 null，安检口写回 401 JSON。

> **白话**：Session 像前台的登记本，服务器要留一份；JWT 像盖了钢印的通行证，服务器只验钢印、不存登录状态。代价是签发之后不好单独作废，所以有效期要短。

**安全边界写清楚**：这是教学实现。密码用带固定盐的 SHA-256（生产应换 BCrypt / Argon2），密钥写在配置文件（生产应从环境变量注入并定期轮换），没有 refresh token 和黑名单。第 15 节列了补齐清单。

---

## 10. MyBatis：SQL 到底怎么拼出来的

Mapper 是接口，没有实现类；`@MapperScan` 让 MyBatis 在启动时生成代理对象。SQL 写在注解里，参数用 `#{...}` 占位（PreparedStatement 绑定，**不是字符串拼接**，因此不会被 SQL 注入）。

筛选列表用 `<script>` 动态 SQL：

```java
@Select({"<script>",
        COLUMNS, JOIN,
        "<where>",
        "  <if test='status != null'> AND a.status = #{status} </if>",
        "  <if test='categoryId != null'> AND a.category_id = #{categoryId} </if>",
        "  <if test='kw != null'> AND (LOWER(a.title) LIKE LOWER(CONCAT('%', #{kw}, '%')) ...</if>",
        "</where>",
        "ORDER BY a.id DESC LIMIT #{limit} OFFSET #{offset}",
        "</script>"})
List<Article> page(...);
```

`<where>` 会自动去掉第一个多余的 `AND`。Service 层把空字符串统一转成 `null`，所以 `<if>` 只需要判断 `!= null`，避开 OGNL 里引号嵌套的麻烦。

`@Options(useGeneratedKeys = true, keyProperty = "id")` 负责把数据库生成的主键回填到对象里 —— 种子数据里评论要挂到刚插入的文章上，靠的就是这个。

---

## 11. 双后端缓存：探测、命中、失效

`CacheService` 里同时住着两个后端：

| 后端 | 实现 | 什么时候用 |
|---|---|---|
| Redis | `StringRedisTemplate`，带 TTL | 启动时探测 `hasKey` 成功，或看板手动切换成功 |
| JVM 内存 | `ConcurrentHashMap` + 过期时间戳 | Redis 连不上（本项目默认状态），或运行中 Redis 挂了自动降级 |

`app.cache.prefer = auto` 时启动探测一次；探测结果缓存 5 秒，避免没有 Redis 的机器上每次刷新页面都等一次连接超时。

一次读取的动作序列：

1. `get(key)` 问当前后端
2. 有值 → 记 `HIT`，直接返回
3. 没值 → 记 `MISS`，回源查库，记 `WRITE` 写回缓存
4. 数据被改动 → `evictPrefix("blog:list:")` 之类记 `EVICT`，删掉相关 key

所有动作都写进 `cache_event` 表，`/api/demo/state/` 取最近 24 条给看板画时间线。统计卡上的「缓存命中率」就是 `HIT / (HIT + MISS)`。

> **白话**：缓存像便签纸。第一次不知道答案，翻账本（数据库）查完顺手抄在便签上；第二次直接看便签。但账本一改，便签就必须撕掉，否则会一直念旧答案。

`ArticleService` 里缓存的是 Jackson 序列化后的 JSON 字符串（注入 Spring 配好的 `ObjectMapper`，自带 `LocalDateTime` 支持）；反序列化失败就当没命中，重新查库 —— 坏缓存不会把页面搞崩。

---

## 12. 前端：Vue 3 与 13 步一键演示

页面只有一个 `index.html`，用本地 `vue.global.prod.js` 挂载，不引任何 CDN，断网也能打开。

`app.js` 里所有请求走同一个 `request()`：

```js
async function request(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (withToken && token.value) headers['Authorization'] = 'Bearer ' + token.value;
  const res = await fetch(path, { method, headers, body: ... });
  const json = await res.json();
  return { status: res.status, code: json.code, data: json.data, message: json.message };
}
```

`onMounted` 时并行拉 `/api/demo/state/`（统计、分类、事件、流水、技术栈）和 `/api/articles/`（列表）。Vue 的 `ref` 一变，模板里对应的数字、列表、徽章自动重绘。

「一键跑完整流程」是真的按次序发 13 个 HTTP 请求，不是写死的动画：

| 步 | 动作 | 想让人看见什么 |
|---|---|---|
| 1 | 登录 | 拿到 JWT，之后所有写请求都带上它 |
| 2 | 不带 token 写文章 | 安检口返回 401，安全规则真的生效 |
| 3-4 | 连读两次分类 | 第一次 MISS，第二次 HIT |
| 5-6 | 连读两次列表 | 同上，缓存键不同 |
| 7 | 新建草稿 | 写操作成功，相关缓存自动失效 |
| 8 | 发布 | `draft → published` |
| 9 | 再读列表 | 缓存已失效，重新回源，新文章立刻可见 |
| 10-11 | 写评论、读评论 | 评论缓存的写入与命中 |
| 12 | 读统计 | 数字来自几张表的 COUNT |
| 13 | 删除演示文章 | 把数据还原，可反复点击 |

带 `?demo=1` 打开页面会自动跑这一串，便于分享和自动化验证。无头浏览器实测：13 步全部渲染，12 个 200 + 1 个预期 401，结束后文章数回到 8。

---

## 13. 看板数据从哪来

`GET /api/demo/state/` 是专门为演示加的「聚合接口」，一次返回：

```
overview      统计卡（文章 / 发布 / 草稿 / 评论 / 浏览 / 分类 / 缓存 / API 调用数）
categories    分类下拉框
articles      文章列表第一页
cache_events  最近 24 条缓存事件
api_logs      最近 24 条请求流水（安检口在内存里维护的环形队列）
cache         缓存后端 / 是否可用 / 命中率 / 内存 key 数
seed          种子数据摘要
stack         技术栈白话对照
java          当前 JDK 版本
```

> **白话**：真实项目里前端会分别调五六个接口；演示项目为了「一眼看全」，把它们在服务端拼成一个大包裹。这叫 BFF（Backend For Frontend，给前端定制的聚合层），是常见做法，不是偷懒。

---

## 14. 测试体系：三层验证

| 层次 | 工具 | 数量 | 验证什么 |
|---|---|---|---|
| 进程内 | JUnit 5 + MockMvc | 11 | 上下文能起、登录/JWT、未授权拦截、分页筛选、文章完整生命周期、缓存 HIT、搜索、状态聚合 |
| 真实 HTTP | `tests/e2e_api.py`（标准库 urllib） | 23 | 端口、代理、JSON 序列化、拦截器写出的真实 401、CRUD、缓存失效、删除后 404、重置 |
| 浏览器 | 无头 Edge + `?demo=1` | 13 步 | Vue 挂载、fetch 真的发出去、DOM 真的更新、桌面与 500px 手机端布局 |

测试用独立内存库 `jdbc:h2:mem:blogtest`，**绝不碰运行中的 `runtime/blogdb`**；`src/test/resources/application.yml` 覆盖数据源与缓存策略。

`e2e_api.py` 里显式用了 `ProxyHandler({})` 绕开环境代理 —— 这条是跨项目复用的经验，见第 16 节。结果写入 `reports/e2e_result.json`，可作为交付证据。

最终实测：`Tests run: 11, Failures: 0`；`23 条 / 23 通过 / 0 失败`；桌面与手机端截图均无模板残留、无横向溢出、暗色标签清晰。

---

## 15. 离生产还差什么

这份代码是「能讲清楚原理」的教学实现，真要上线需要补：

| 项 | 现在 | 生产建议 |
|---|---|---|
| 数据库 | H2 文件库（MySQL 兼容模式） | 换真 MySQL，只改四行配置：<br>`url: jdbc:mysql://host:3306/blog?useUnicode=true&characterEncoding=utf8`<br>`driver-class-name: com.mysql.cj.jdbc.Driver`<br>`username` / `password`；并加 `mysql-connector-j` 依赖 |
| 密码 | 固定盐 SHA-256 | BCrypt / Argon2，随机盐 + 慢哈希 |
| JWT 密钥 | 配置文件默认值 | 环境变量注入、定期轮换、加 `jti` 黑名单 |
| 登录态 | 单 token，24 小时 | access token 短时效 + refresh token + 退出即失效 |
| 缓存 | 内存 / 可选 Redis | 强制 Redis，加空值缓存防穿透、互斥重建防击穿、过期时间加抖动防雪崩 |
| 分页 | `LIMIT / OFFSET` | 深分页改游标分页 |
| 可观测性 | 内存环形日志 | 结构化日志 + 链路追踪 + 指标打点 |
| 部署 | `java -jar` | 容器化、连接池参数、只读账号、备份策略 |
| 权限 | 只有 `author` 角色 | 区分作者 / 管理员，文章归属校验（现在任意登录用户可改他人文章） |

最后一条尤其重要：当前 `update/delete` 只校验「有没有登录」，没校验「是不是本人」。作为演示够用，写进简历时要主动说明这是已知边界 —— 能说清边界，比假装没有边界更专业。

---

## 16. 踩坑复盘（8 条，按出现顺序）

**① H2 的 `AUTO_SERVER` 与 `DB_CLOSE_ON_EXIT` 不能同时开。**
第一次启动直接失败：`Feature not supported: "AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE" [50100-224]`。单进程根本用不到 `AUTO_SERVER`（那是给多个 JVM 共享文件用的），去掉这两个参数即可。启动时报的错往往是最外层的一长串 `BeanCreationException`，**要一路翻到最底下 `Caused by` 才是根因**。

**② 环境变量 `SERVER__PORT=53555` 把端口改了。**
日志里 Tomcat 起在 53555 而不是 8003。查 `env` 发现宿主注入了 `SERVER__PORT`，Spring Boot 的宽松绑定把它当成了 `server.port`，优先级高于 `application.yml`。修法：启动命令显式加 `--server.port=8003`（命令行参数优先级最高），`start.py` 里也这么写。

**③ GET 一律放行，把 `/api/auth/me/` 也放过了。**
前 3 条 e2e 失败：无 token 返回 `{"code":401}` 但 HTTP 200，带 token 也返回「请先登录」。原因是 `needToken()` 里「GET 放行」的判断在白名单之后，导致拦截器压根没验 token、也没把 `userId` 塞进请求。加一条 `/api/auth/me` 的例外提前返回 true 即可。

**④ 环境代理会把 `127.0.0.1` 的请求转发出去。**
这是项目 4 踩过、这里提前防住的坑：机器上有全局 `HTTP_PROXY`，`requests` / `urllib` 默认走代理，本地接口会拿到代理返回的 404。`e2e_api.py` 用 `ProxyHandler({})`，`curl` 验证一律加 `--noproxy '*'`。

**⑤ 种子数据不能硬编码主键。**
「重置演示」会清空文章再重灌，但 H2 的 `AUTO_INCREMENT` 不会回到 1，第一版把评论写死挂在 `1L~5L` 上，重置后评论就挂到了不存在的文章。改成 `addArticle()` 返回真实生成的主键，用 `List<Long> ids` 收集后再挂评论。

**⑥ Redis 探测不能每次都做。**
没有 Redis 时，每次 `redisAvailable()` 都要等一次连接超时；而 `/api/demo/state/` 里会调两次，页面明显变慢。加了 5 秒的探测结果缓存，`switchTo()` 时强制重新探测一次。

**⑦ 暗色主题下的灰色标签。**
前几个项目踩过「白底白字」：暗色模式下 `.pill-muted` 如果只给背景不给文字色，就会变成浅底浅字。这次所有徽章变体都显式写了 `background` 和 `color` 两个变量，500px 手机截图复核通过。

**⑧ 运行的 jar 会锁住文件，改完必须重启才生效。**
Spring Boot 把静态资源打进 `target/blog-app.jar`，改了磁盘上的 `static/app.js` 不会立刻反映到页面上，Windows 上也不能直接覆盖正在运行的 jar。正确顺序是：停服务 → `mvn package` → 重启。这不是 bug，但很容易让人误以为「改了没用」。

---

## 17. 术语速查

| 术语 | 一句话 |
|---|---|
| Spring Boot | Java 的 Web 框架，负责装配和启动 |
| IoC / DI | 对象不由自己造，由框架递进来 |
| Controller / Service / Mapper | 接请求 / 定规则 / 写 SQL，三层各司其职 |
| ORM / MyBatis | 把表行和对象互相对上的翻译层 |
| DTO | 层与层之间传数据的小盒子 |
| JWT | 三段式通行证，服务器只验签名 |
| HMAC-SHA256 | 用密钥给内容盖钢印的算法 |
| Interceptor | 请求进 Controller 前的统一安检口 |
| `@RestControllerAdvice` | 全局异常兜底，统一转成 JSON |
| 缓存 MISS / HIT | 没找到要回源查库 / 直接命中便签 |
| 缓存失效 | 数据改了就撕掉旧便签 |
| 秒级 TTL | 缓存多久过期 |
| MockMvc | 不起真实端口，在进程内模拟 HTTP 测试 |
| 端到端测试 | 真的走网络、真的写库、真的读回来 |
| BFF | 给前端定制的聚合接口 |

---

## 18. 附录：一键复现

```bash
# 1. 启动（首次会自动下载项目内 Maven 并打包）
python start.py

# 2. 打开看板
#    http://127.0.0.1:8003/
#    http://127.0.0.1:8003/?demo=1   # 自动跑 13 步演示

# 3. 另开终端跑测试
python tests/e2e_api.py
JAVA_HOME="C:/Program Files/Java/jdk-17" tools/apache-maven-3.9.9/bin/mvn -B test

# 4. 常用接口
curl --noproxy '*' http://127.0.0.1:8003/api/health/
curl --noproxy '*' -X POST http://127.0.0.1:8003/api/auth/login/ \
     -H 'Content-Type: application/json' \
     -d '{"username":"alice","password":"123456"}'
```

演示账号 `alice / 123456`（另有 bob、carol、demo，密码相同）。

---

*文档随代码存于 `docs/`，`docs/reader.html` 是双击即开的离线双 Tab 阅读版，由 `docs/build_reader.py` 从本文与博客生成。*
