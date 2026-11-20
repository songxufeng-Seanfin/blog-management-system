package com.xufeng.blog.entity;

import java.time.LocalDateTime;

/** 缓存事件流水：每一次"命中 / 回源 / 写入 / 清除"都留一条，看板据此画时间线 */
public class CacheEvent {
    private Long id;
    private String kind;
    private String cacheKey;
    private String result;
    private String detail;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCacheKey() { return cacheKey; }
    public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
