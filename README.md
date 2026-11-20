# 在线博客管理系统（项目 5）

> Spring Boot 3.2 + MyBatis + H2 + 手写 JWT + Vue 3 · Java 方向
> 请求从浏览器进来，先过安检口验通行证，Controller 收和回，Service 定规则，Mapper 翻成 SQL，H2 存数据；中间加了一层缓存，重复的问题不用每次都问数据库。

一个能真正跑起来、能看懂的在线博客后端：注册登录、文章草稿/发布/下架、分类、搜索分页、评论互动、统计概览，全部走真实 HTTP 接口；前端是一个 Vue 3 单页看板，带「一键演示」把整条链路当场跑给你看。

## 文档入口

| 文件 | 内容 |
|---|---|
| [docs/reader.html](./docs/reader.html) | **推荐先看**：技术文档 + 博客的双 Tab 离线阅读版，双击即开，不用联网 |
| [docs/technical-doc.md](./docs/technical-doc.md) | 技术文档 19 节：请求往返、三层架构、数据模型、JWT、MyBatis、双后端缓存、测试体系、8 条踩坑复盘、术语速查 |
| [docs/blog-post.md](./docs/blog-post.md) | 第一人称复盘：第一次用 Java 写后端时，把「请求怎么走一圈」重做一遍的过程 |
| [docs/build_reader.py](./docs/build_reader.py) | 由两份 Markdown 重新生成 reader.html 的脚本 |
| [reports/e2e_result.json](./reports/e2e_result.json) | 一次完整的 23 条端到端回归结果样例 |

## 快速开始

只需要 **JDK 17+**（本机实测 JDK 17 / 26 都可以）和 **Python 3**（用来跑一键脚本）。不需要装 Maven、不需要装 MySQL、不需要装 Redis。

```bash
python start.py                # 构建 + 启动，默认 http://127.0.0.1:8003/
python start.py --build-only   # 只打包出 target/blog-app.jar
```

`start.py` 做的事：自动找 JDK → 首次运行时把 Maven 3.9.9 下载解压到项目内 `tools/`（不入库、不改系统环境变量）→ `mvn package` 打成可执行 jar → 用命令行参数 `--server.port=8003` 启动（参数优先级最高，避免宿主环境变量把端口改掉）。

打开 **http://127.0.0.1:8003/** 就能看到看板；访问 **http://127.0.0.1:8003/?demo=1** 会自动执行 13 步一键演示。演示账号：`alice` / `123456`（另有 `bob`、`carol`、`demo`，密码相同）。

> 数据库是文件型的 H2（`runtime/blogdb`），首次启动自动建表并灌入 8 篇文章 / 4 分类 / 6 评论。`runtime/` 和 `target/` 都不入库。

## 验证怎么跑

```bash
python tests/e2e_api.py                       # 23 条真实 HTTP 端到端用例（零第三方依赖）
JAVA_HOME="<JDK路径>" tools/apache-maven-3.9.9/bin/mvn -B test    # JUnit 5 + MockMvc 11 条
```

三层验证的分工：JUnit 测 Java 内部（JWT 签发校验、密码哈希、Mapper SQL、异常映射），`e2e_api.py` 打真实 HTTP 验完整闭环（登录 / 401 拦截 / CRUD / 搜索 / 评论 / 缓存命中与失效 / 数据恢复），无头浏览器 `?demo=1` 验前端一键演示链路。

当前结果：**JUnit 11/11 通过、端到端 23/23 通过、浏览器演示 13 步 12 个 200 + 1 个预期 401**，演示创建的文章会自动清理，数据回到 8 篇。

## 目录结构（哪个文件管什么）

```
pom.xml                     依赖清单：SpringBoot / MyBatis / H2 / Redis(可选) / JUnit5
start.py                    一键启动：找 JDK → 备 Maven → 打包 → 起服务
src/main/resources/
  application.yml           配置：端口、H2 连接串、缓存策略、JWT 密钥
  schema.sql                5 张表的建表语句（user/category/article/comment/cache_event）
  static/                   Vue 3 单页：index.html + app.js + style.css + 本地 vue.global.prod.js
src/main/java/com/xufeng/blog/
  BlogApplication.java      程序入口，@MapperScan 扫 Mapper
  common/                   ApiResult 统一响应 / 全局异常 / 手写 JwtUtil / 安检口 AuthInterceptor / WebConfig / API 流水
  entity/ dto/              数据表对应的 Java 对象 / 前后端传递的数据包
  mapper/                   MyBatis 翻译员：注解式 SQL，把对象翻成数据库听得懂的话
  service/                  业务规则：认证、文章、评论、分类、统计、缓存双后端、演示数据
  controller/               收请求和回响应：认证 / 文章 / 查询 / 演示开关
src/test/                   JUnit 5 + MockMvc 单元测试
tests/e2e_api.py            真实 HTTP 端到端回归
docs/ reports/              文档四件套 / 回归结果样例
```

## 实现里的几个点

- **三层架构**：Controller 只管收和回，Service 只管规则，Mapper 只管 SQL。分三份不是为了好看，是为了改一处不牵动另外两处。
- **手写 JWT**：登录成功发一张带签名的通行证，之后每次请求带在 `Authorization: Bearer ...` 里。服务器只验签名、不存登录记录；`AuthInterceptor` 是那道安检口，写操作必须带票，读接口放行。
- **MyBatis**：不自动帮你生成 SQL，而是在注解里手写 SQL 片段，用 `<script>` 按条件拼出完整语句，参数用 `#{}` 绑定（不是字符串拼值），避免注入。
- **双后端缓存**：启动时会探测 Redis 是否可用，能用就用 `RedisTemplate`，不能用就自动回退到 JVM 内存 `ConcurrentHashMap`，页面顶部会标明当前用的是哪个。缓存事件表把每次 HIT / MISS / WRITE / EVICT 都记下来，看板能直接看见「省了几次数据库」。
- **H2 的 MySQL 兼容模式**：`MODE=MySQL;DATABASE_TO_LOWER=TRUE`，零安装、一个文件就是一整个库，SQL 写法贴近生产库。注意 `AUTO_SERVER` 与 `DB_CLOSE_ON_EXIT=FALSE` 不能同时开（见文档踩坑复盘）。
- **前端零构建**：Vue 3 用本地 `vue.global.prod.js`，没有 npm、没有打包步骤；桌面和 500px 手机端都适配。

## 离生产还差什么

JWT 密钥目前写在配置里（应换成环境变量）、密码哈希是演示级简化版（应换 BCrypt）、没有刷新令牌与注销黑名单、H2 应换 MySQL + Flyway、缓存没有空值防穿透。这些在[技术文档第 15 节](./docs/technical-doc.md)里逐条列了原因和改法，没有藏起来。
