package com.xufeng.blog.service;

import com.xufeng.blog.common.ApiLogService;
import com.xufeng.blog.mapper.ArticleMapper;
import com.xufeng.blog.mapper.CategoryMapper;
import com.xufeng.blog.mapper.CommentMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统计服务：把几张表的数字汇总成看板上的统计卡 */
@Service
public class StatsService {

    private final ArticleMapper articles;
    private final CommentMapper comments;
    private final CategoryMapper categories;
    private final CacheService cache;
    private final ApiLogService apiLog;

    public StatsService(ArticleMapper articles, CommentMapper comments,
                        CategoryMapper categories, CacheService cache, ApiLogService apiLog) {
        this.articles = articles;
        this.comments = comments;
        this.categories = categories;
        this.cache = cache;
        this.apiLog = apiLog;
    }

    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("articles", articles.countAll());
        m.put("published", articles.countPublished());
        m.put("drafts", articles.countDraft());
        m.put("views", articles.sumViews());
        m.put("comments", comments.count());
        m.put("categories", categories.count());
        m.put("cache", cache.stats());
        m.put("api_calls", apiLog.total());
        return m;
    }
}
