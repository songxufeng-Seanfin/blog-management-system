package com.xufeng.blog.service;

import com.xufeng.blog.entity.CacheEvent;
import com.xufeng.blog.mapper.CacheEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双后端缓存：启动时探测 Redis，能连上就用 Redis，连不上自动回退到 JVM 内存。
 * 看板上可以运行时切换，每次 命中 / 回源 / 写入 / 清除 都落一条事件流水。
 */
@Service
public class CacheService {

    private record Entry(String value, long expireAt) {}

    private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final CacheEventMapper events;
    private final long ttlSeconds;
    private final String prefer;
    private volatile String backend = "memory";
    // Redis 探测结果缓存 5 秒：没有 Redis 时不必每次刷新页面都等一次连接超时
    private volatile boolean redisOk = false;
    private volatile long lastProbeAt = 0L;

    public CacheService(StringRedisTemplate redis,
                        CacheEventMapper events,
                        @Value("${app.cache.ttl-seconds}") long ttlSeconds,
                        @Value("${app.cache.prefer}") String prefer) {
        this.redis = redis;
        this.events = events;
        this.ttlSeconds = ttlSeconds;
        this.prefer = prefer;
        detect();
    }

    /** auto = 探测；memory / redis = 强制指定（redis 连不上仍回退内存） */
    public void detect() {
        if ("memory".equalsIgnoreCase(prefer)) {
            backend = "memory";
        } else {
            backend = redisAvailable() ? "redis" : "memory";
        }
    }

    public boolean redisAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < 5000L) return redisOk;
        try {
            redis.hasKey("blog:__probe__");
            redisOk = true;
        } catch (Exception e) {
            redisOk = false;
        }
        lastProbeAt = now;
        return redisOk;
    }

    public String backend() { return backend; }

    /** 运行时切换；Redis 不可用时不硬切，返回实际生效的后端 */
    public String switchTo(String name) {
        lastProbeAt = 0L;   // 切换时强制重新探测一次
        if ("redis".equalsIgnoreCase(name) && redisAvailable()) {
            backend = "redis";
        } else {
            backend = "memory";
        }
        record("SWITCH", "-", "BACKEND", "缓存后端切换为 " + backend);
        return backend;
    }

    public String get(String key) {
        String value = backend.equals("redis") ? redisGet(key) : memoryGet(key);
        if (value != null) {
            record("GET", key, "HIT", backend + " 命中");
            return value;
        }
        record("GET", key, "MISS", backend + " 未命中，回源查库");
        return null;
    }

    public void set(String key, String value) {
        if (backend.equals("redis")) {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } else {
            memory.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
        }
        record("SET", key, "WRITE", backend + " 写入缓存");
    }

    /** 数据变了就删掉相关缓存，避免页面还显示旧内容（缓存失效） */
    public void evictPrefix(String prefix) {
        memory.keySet().removeIf(k -> k.startsWith(prefix));
        try {
            if (backend.equals("redis")) {
                Set<String> keys = redis.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) redis.delete(keys);
            }
        } catch (Exception ignored) {
            // Redis 临时不可用时，至少内存侧已经清干净
        }
        record("EVICT", prefix + "*", "EVICT", "清除前缀 " + prefix + "* 的缓存");
    }

    public void clearAll() {
        memory.clear();
        record("CLEAR", "*", "EVICT", "清空内存缓存");
    }

    public Map<String, Object> stats() {
        long hits = events.hitCount();
        long misses = events.missCount();
        long writes = events.writeCount();
        long reads = hits + misses;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("backend", backend);
        out.put("backend_label", backend.equals("redis") ? "Redis" : "JVM 内存（自动回退）");
        out.put("redis_available", redisAvailable());
        out.put("ttl_seconds", ttlSeconds);
        out.put("hits", hits);
        out.put("misses", misses);
        out.put("writes", writes);
        out.put("hit_rate", reads == 0 ? 0 : Math.round(hits * 1000.0 / reads) / 10.0);
        out.put("memory_keys", memory.size());
        return out;
    }

    private String memoryGet(String key) {
        Entry e = memory.get(key);
        if (e == null) return null;
        if (e.expireAt() < System.currentTimeMillis()) {
            memory.remove(key);
            return null;
        }
        return e.value();
    }

    private String redisGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            backend = "memory";   // 运行中 Redis 挂了，自动降级
            record("FALLBACK", key, "BACKEND", "Redis 异常，已自动回退内存");
            return memoryGet(key);
        }
    }

    private void record(String kind, String key, String result, String detail) {
        CacheEvent e = new CacheEvent();
        e.setKind(kind);
        e.setCacheKey(key);
        e.setResult(result);
        e.setDetail(detail);
        e.setCreatedAt(LocalDateTime.now());
        events.insert(e);
    }
}
