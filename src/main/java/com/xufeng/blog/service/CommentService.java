package com.xufeng.blog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufeng.blog.common.BusinessException;
import com.xufeng.blog.dto.Dtos.CommentRequest;
import com.xufeng.blog.entity.Comment;
import com.xufeng.blog.mapper.ArticleMapper;
import com.xufeng.blog.mapper.CommentMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 评论服务：读走缓存，写完立刻让该文章的评论缓存失效 */
@Service
public class CommentService {

    private final CommentMapper comments;
    private final ArticleMapper articles;
    private final CacheService cache;
    private final ObjectMapper json;

    public CommentService(CommentMapper comments, ArticleMapper articles,
                          CacheService cache, ObjectMapper json) {
        this.comments = comments;
        this.articles = articles;
        this.cache = cache;
        this.json = json;
    }

    public List<Comment> list(Long articleId) {
        String key = "blog:comments:" + articleId;
        String cached = cache.get(key);
        if (cached != null) {
            try {
                return json.readValue(cached, new TypeReference<List<Comment>>() {});
            } catch (Exception ignored) {
                // 缓存坏了就回源
            }
        }
        List<Comment> rows = comments.listByArticle(articleId);
        try {
            cache.set(key, json.writeValueAsString(rows));
        } catch (Exception ignored) {
            // 忽略
        }
        return rows;
    }

    public Comment add(Long articleId, CommentRequest req) {
        if (articles.findById(articleId) == null) {
            throw new BusinessException(404, "文章不存在：" + articleId);
        }
        if (req == null || req.content() == null || req.content().isBlank()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        Comment c = new Comment();
        c.setArticleId(articleId);
        c.setAuthorName(req.authorName() == null || req.authorName().isBlank()
                ? "匿名读者" : req.authorName().trim());
        c.setContent(req.content().trim());
        c.setStatus("visible");
        c.setCreatedAt(LocalDateTime.now());
        comments.insert(c);
        cache.evictPrefix("blog:comments:" + articleId);
        return c;
    }
}
