package com.xufeng.blog.service;

import com.xufeng.blog.common.ApiLogService;
import com.xufeng.blog.entity.Article;
import com.xufeng.blog.entity.Category;
import com.xufeng.blog.entity.Comment;
import com.xufeng.blog.entity.User;
import com.xufeng.blog.mapper.ArticleMapper;
import com.xufeng.blog.mapper.CacheEventMapper;
import com.xufeng.blog.mapper.CategoryMapper;
import com.xufeng.blog.mapper.CommentMapper;
import com.xufeng.blog.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据：只在文章表为空时灌一次，所以重启不会冲掉用户自己写的内容。
 * 「重置演示」是看板上的一个显式按钮，才会清空重建。
 */
@Component
public class DemoSeeder {

    private final UserMapper users;
    private final CategoryMapper categories;
    private final ArticleMapper articles;
    private final CommentMapper comments;
    private final CacheEventMapper events;
    private final AuthService auth;
    private final CacheService cache;
    private final ApiLogService apiLog;

    @Value("${app.demo.seed-on-startup}")
    private boolean seedOnStartup;

    public DemoSeeder(UserMapper users, CategoryMapper categories, ArticleMapper articles,
                      CommentMapper comments, CacheEventMapper events, AuthService auth,
                      CacheService cache, ApiLogService apiLog) {
        this.users = users;
        this.categories = categories;
        this.articles = articles;
        this.comments = comments;
        this.events = events;
        this.auth = auth;
        this.cache = cache;
        this.apiLog = apiLog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (seedOnStartup && articles.countAll() == 0) seed();
    }

    /** 显式重置：清空文章 / 评论 / 事件流水，保留用户账号和分类 */
    public synchronized Map<String, Object> reset() {
        comments.clear();
        articles.clear();
        events.clear();
        apiLog.clear();
        cache.evictPrefix("blog:");
        seed();
        return summary();
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("categories", categories.count());
        m.put("articles", articles.countAll());
        m.put("published", articles.countPublished());
        m.put("drafts", articles.countDraft());
        m.put("comments", comments.count());
        return m;
    }

    private void seed() {
        if (users.count() == 0) {
            addUser("alice", "阿梨", "123456");
            addUser("bob", "小柏", "123456");
            addUser("carol", " Carol ", "123456");
            addUser("demo", "演示账号", "123456");
        }
        if (categories.count() == 0) {
            addCategory("技术笔记", "tech", 1);
            addCategory("项目复盘", "review", 2);
            addCategory("学习笔记", "notes", 3);
            addCategory("生活随笔", "life", 4);
        }
        Map<String, Long> catIds = new HashMap<>();
        for (Category c : categories.list()) catIds.put(c.getSlug(), c.getId());

        Long alice = users.findByUsername("alice").getId();
        Long bob = users.findByUsername("bob").getId();
        Long carol = users.findByUsername("carol").getId();
        Long tech = catIds.get("tech");
        Long review = catIds.get("review");
        Long notes = catIds.get("notes");
        Long life = catIds.get("life");

        // 用真实生成的主键，而不是写死 1~8：重置后 ID 不会回到 1
        List<Long> ids = new ArrayList<>();
        ids.add(addArticle("从零搭一个 SpringBoot 项目：我踩过的 5 个坑",
                "依赖、端口、扫描包、配置文件、打包，五个最容易被新手卡住的地方。",
                "第一次跑 SpringBoot 的时候，我以为只要写个 main 方法就够了。结果卡在依赖下载、端口占用、Mapper 扫描、配置读取和打包顺序上整整一天。这篇把五个坑按出现顺序写下来，每个坑都附上当时的报错和解决动作。",
                tech, alice, "published", 9, 128));
        ids.add(addArticle("MyBatis 动态 SQL 到底在拼什么",
                "把 <if> 和 <where> 翻译成一句普通 SQL，就再也不用背了。",
                "MyBatis 的 <script> 不是魔法，它只是在运行的时候按条件把 SQL 片段拼成一条完整语句，再把 ? 占位符交给 JDBC。理解这一点之后，动态 SQL 就只是字符串拼接 + 参数绑定。",
                tech, alice, "published", 7, 96));
        ids.add(addArticle("JWT 为什么不需要在服务器存登录状态",
                "三段式结构、签名校验、以及它和 Session 的取舍。",
                "Session 像前台的登记本，服务器要留一份；JWT 像盖了钢印的通行证，服务器只验钢印。代价是签发后不好撤销，所以有效期要短、敏感操作要二次校验。",
                tech, bob, "published", 5, 87));
        ids.add(addArticle("缓存穿透、击穿、雪崩：用生活例子讲清楚",
                "三个名字吓人、原理其实都是「没挡住」的问题。",
                "穿透是查一个根本不存在的东西；击穿是一个热点刚好过期；雪崩是一大片同时过期。对应办法分别是空值缓存 / 布隆过滤、互斥重建、过期时间加随机抖动。",
                review, carol, "published", 4, 64));
        ids.add(addArticle("接口测试怎么写出不脆弱的断言",
                "别断言整个响应体，断言你真正关心的那几个不变量。",
                "把断言写在业务契约上，而不是某次运行的具体 ID 或时间戳上。这样后端重构时，测试不会因为无关变化而红成一片。",
                review, bob, "published", 3, 53));
        ids.add(addArticle("用 Vue 3 做一个不刷新页面的管理后台",
                "响应式数据、列表渲染和 fetch 三件套就够了。",
                "管理后台不需要一上来就上路由和状态管理。一个 createApp、几个 ref、一个轮询或手动刷新的 fetch，就能把数据流讲得很清楚。",
                notes, alice, "published", 2, 41));
        ids.add(addArticle("两周项目复盘：从需求到交付",
                "还在整理，先把大纲记在这里。",
                "待补充：需求拆解、每日最小动作、踩坑清单、文档沉淀。",
                review, carol, "draft", 1, 0));
        ids.add(addArticle("读书笔记：领域驱动设计入门",
                "草稿，读到第三章。",
                "待补充：实体、值对象、聚合、限界上下文。",
                notes, life == null ? notes : life, "draft", 0, 0));

        // 评论挂在真实生成的文章 ID 上
        addComment(ids.get(0), "小林", "第五个坑我上周刚踩过，太真实了");
        addComment(ids.get(0), "阿泽", "打包那段能再展开讲讲吗");
        addComment(ids.get(1), "Mia", "「就是字符串拼接」这句一下就懂了");
        addComment(ids.get(2), "小柏", "补充一点：退出登录可以配合黑名单");
        addComment(ids.get(3), "阿梨", "随机抖动这个说法很形象");
        addComment(ids.get(4), "读者甲", "断言不变量这个原则很受用");

        cache.evictPrefix("blog:");
    }

    private void addUser(String username, String nickname, String rawPassword) {
        User u = new User();
        u.setUsername(username.trim());
        u.setPasswordHash(auth.hash(rawPassword));
        u.setNickname(nickname.trim());
        u.setRole("author");
        u.setCreatedAt(LocalDateTime.now().minusDays(30));
        users.insert(u);
    }

    private void addCategory(String name, String slug, int sort) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        c.setSortNo(sort);
        categories.insert(c);
    }

    private Long addArticle(String title, String summary, String content, Long categoryId,
                            Long authorId, String status, int daysAgo, int views) {
        LocalDateTime t = LocalDateTime.now().minusDays(daysAgo);
        Article a = new Article();
        a.setTitle(title);
        a.setSummary(summary);
        a.setContent(content);
        a.setCategoryId(categoryId);
        a.setAuthorId(authorId);
        a.setStatus(status);
        a.setCreatedAt(t);
        a.setUpdatedAt(t);
        a.setPublishedAt("published".equals(status) ? t : null);
        articles.insert(a);
        if (views > 0) articles.setViews(a.getId(), views);
        return a.getId();
    }

    private void addComment(Long articleId, String author, String content) {
        Comment c = new Comment();
        c.setArticleId(articleId);
        c.setAuthorName(author);
        c.setContent(content);
        c.setStatus("visible");
        c.setCreatedAt(LocalDateTime.now().minusHours(articleId * 3));
        comments.insert(c);
    }
}
