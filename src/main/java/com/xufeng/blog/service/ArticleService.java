package com.xufeng.blog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufeng.blog.common.BusinessException;
import com.xufeng.blog.dto.Dtos.ArticleRequest;
import com.xufeng.blog.dto.Dtos.PageResult;
import com.xufeng.blog.entity.Article;
import com.xufeng.blog.mapper.ArticleMapper;
import com.xufeng.blog.mapper.CommentMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 文章服务：业务逻辑层。Controller 只管接请求，真正的规则都写在这里。 */
@Service
public class ArticleService {

    private static final String LIST_KEY = "blog:list:";
    private static final String ARTICLE_KEY = "blog:article:";

    private record CachedPage(List<Article> items, long total) {}

    private final ArticleMapper articles;
    private final CommentMapper comments;
    private final CacheService cache;
    private final ObjectMapper json;

    public ArticleService(ArticleMapper articles, CommentMapper comments,
                          CacheService cache, ObjectMapper json) {
        this.articles = articles;
        this.comments = comments;
        this.cache = cache;
        this.json = json;
    }

    /** 分页 + 状态 / 分类 / 关键词筛选；第一次回源查库，之后命中缓存 */
    public PageResult<Article> page(String status, Long categoryId, String kw, int page, int size) {
        String st = blankToNull(status);
        if ("all".equalsIgnoreCase(st)) st = null;
        String k = blankToNull(kw);
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 50);
        int offset = (p - 1) * s;
        String key = LIST_KEY + st + ":" + categoryId + ":" + k + ":" + p + ":" + s;

        CachedPage cachedPage = null;
        String cached = cache.get(key);
        if (cached != null) {
            try {
                cachedPage = json.readValue(cached, CachedPage.class);
            } catch (Exception ignored) {
                cachedPage = null;   // 缓存内容坏了就当没命中，重新查库
            }
        }
        if (cachedPage == null) {
            long total = articles.countPage(st, categoryId, k);
            List<Article> items = articles.page(st, categoryId, k, s, offset);
            cachedPage = new CachedPage(items, total);
            try {
                cache.set(key, json.writeValueAsString(cachedPage));
            } catch (Exception ignored) {
                // 序列化失败不影响主流程
            }
        }
        long total = cachedPage.total();
        int pages = (int) Math.ceil((double) total / s);
        return new PageResult<>(cachedPage.items(), total, p, s, pages);
    }

    /** 文章详情：缓存命中直接返回，未命中才回源并把浏览量 +1 */
    public Article detail(Long id) {
        String key = ARTICLE_KEY + id;
        String cached = cache.get(key);
        if (cached != null) {
            try {
                return json.readValue(cached, Article.class);
            } catch (Exception ignored) {
                // 落到下面的回源逻辑
            }
        }
        Article a = articles.findById(id);
        if (a == null) throw new BusinessException(404, "文章不存在：" + id);
        articles.incrView(id);
        a.setViewCount((a.getViewCount() == null ? 0 : a.getViewCount()) + 1);
        try {
            cache.set(key, json.writeValueAsString(a));
        } catch (Exception ignored) {
            // 忽略缓存写入失败
        }
        return a;
    }

    public Article create(ArticleRequest req, Long authorId) {
        requireTitle(req);
        LocalDateTime now = LocalDateTime.now();
        Article a = new Article();
        a.setTitle(req.title().trim());
        a.setSummary(req.summary() == null ? "" : req.summary().trim());
        a.setContent(req.content() == null ? "" : req.content());
        a.setCategoryId(req.categoryId());
        a.setAuthorId(authorId);
        a.setStatus("published".equals(req.status()) ? "published" : "draft");
        a.setPublishedAt("published".equals(a.getStatus()) ? now : null);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        articles.insert(a);
        invalidate(a.getId());
        return articles.findById(a.getId());
    }

    public Article update(Long id, ArticleRequest req) {
        requireTitle(req);
        Article a = articles.findById(id);
        if (a == null) throw new BusinessException(404, "文章不存在：" + id);
        a.setTitle(req.title().trim());
        a.setSummary(req.summary() == null ? "" : req.summary().trim());
        a.setContent(req.content() == null ? "" : req.content());
        a.setCategoryId(req.categoryId());
        if (req.status() != null && !req.status().isBlank()) a.setStatus(req.status());
        a.setUpdatedAt(LocalDateTime.now());
        if ("published".equals(a.getStatus()) && a.getPublishedAt() == null) {
            a.setPublishedAt(a.getUpdatedAt());
        }
        articles.update(a);
        invalidate(id);
        return articles.findById(id);
    }

    public void delete(Long id) {
        Article a = articles.findById(id);
        if (a == null) throw new BusinessException(404, "文章不存在：" + id);
        comments.deleteByArticle(id);
        articles.delete(id);
        invalidate(id);
    }

    public Article publish(Long id) {
        Article a = articles.findById(id);
        if (a == null) throw new BusinessException(404, "文章不存在：" + id);
        articles.publish(id, LocalDateTime.now());
        invalidate(id);
        return articles.findById(id);
    }

    public Article unpublish(Long id) {
        Article a = articles.findById(id);
        if (a == null) throw new BusinessException(404, "文章不存在：" + id);
        articles.unpublish(id, LocalDateTime.now());
        invalidate(id);
        return articles.findById(id);
    }

    /** 数据一变，相关缓存全部失效，否则页面会一直显示旧内容 */
    private void invalidate(Long id) {
        cache.evictPrefix(LIST_KEY);
        cache.evictPrefix(ARTICLE_KEY + id);
        cache.evictPrefix("blog:comments:" + id);
    }

    private void requireTitle(ArticleRequest req) {
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new BusinessException(400, "文章标题不能为空");
        }
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
