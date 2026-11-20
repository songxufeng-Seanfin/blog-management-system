package com.xufeng.blog.controller;

import com.xufeng.blog.common.ApiLogService;
import com.xufeng.blog.common.ApiResult;
import com.xufeng.blog.mapper.CacheEventMapper;
import com.xufeng.blog.service.ArticleService;
import com.xufeng.blog.service.CacheService;
import com.xufeng.blog.service.CategoryService;
import com.xufeng.blog.service.DemoSeeder;
import com.xufeng.blog.service.StatsService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 演示看板专用：一次拿齐状态、重置演示数据、切换缓存后端 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private final StatsService stats;
    private final CategoryService categories;
    private final ArticleService articles;
    private final CacheEventMapper events;
    private final ApiLogService apiLog;
    private final DemoSeeder seeder;
    private final CacheService cache;

    public DemoController(StatsService stats, CategoryService categories, ArticleService articles,
                          CacheEventMapper events, ApiLogService apiLog, DemoSeeder seeder,
                          CacheService cache) {
        this.stats = stats;
        this.categories = categories;
        this.articles = articles;
        this.events = events;
        this.apiLog = apiLog;
        this.seeder = seeder;
        this.cache = cache;
    }

    @GetMapping("/demo/state/")
    public ApiResult<Map<String, Object>> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overview", stats.overview());
        m.put("categories", categories.list());
        m.put("articles", articles.page(null, null, null, 1, 10));
        m.put("cache_events", events.recent(24));
        m.put("api_logs", apiLog.recent(24));
        m.put("cache", cache.stats());
        m.put("seed", seeder.summary());

        Map<String, String> stack = new LinkedHashMap<>();
        stack.put("后端框架", "Spring Boot 3.2.5");
        stack.put("持久层", "MyBatis 3.0.3（注解 + 动态 SQL）");
        stack.put("数据库", "H2 嵌入式 · MySQL 兼容模式");
        stack.put("认证", "手写 JWT · HMAC-SHA256");
        stack.put("缓存", "Redis 可选 · 不可用时自动回退 JVM 内存");
        stack.put("前端", "Vue 3（本地引入，离线可用）");
        m.put("stack", stack);
        m.put("java", System.getProperty("java.version"));
        return ApiResult.ok(m);
    }

    @PostMapping("/demo/reset/")
    public ApiResult<Map<String, Object>> reset() {
        return ApiResult.ok(seeder.reset());
    }

    @PostMapping("/cache/switch/")
    public ApiResult<Map<String, Object>> switchCache(@RequestBody(required = false) Map<String, String> body) {
        String want = body == null ? "memory" : body.getOrDefault("backend", "memory");
        String actual = cache.switchTo(want);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requested", want);
        m.put("backend", actual);
        m.put("redis_available", cache.redisAvailable());
        m.put("message", "redis".equals(actual)
                ? "已切换到 Redis" : "Redis 不可用（或指定为内存），当前使用 JVM 内存缓存");
        return ApiResult.ok(m);
    }
}
