package com.xufeng.blog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufeng.blog.entity.Category;
import com.xufeng.blog.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 分类服务：分类很少变，非常适合放缓存，这里用来演示"第一次 MISS、后面 HIT" */
@Service
public class CategoryService {

    private static final String KEY = "blog:categories";

    private final CategoryMapper categories;
    private final CacheService cache;
    private final ObjectMapper json;

    public CategoryService(CategoryMapper categories, CacheService cache, ObjectMapper json) {
        this.categories = categories;
        this.cache = cache;
        this.json = json;
    }

    public List<Category> list() {
        String cached = cache.get(KEY);
        if (cached != null) {
            try {
                return json.readValue(cached, new TypeReference<List<Category>>() {});
            } catch (Exception ignored) {
                // 回源
            }
        }
        List<Category> rows = categories.list();
        try {
            cache.set(KEY, json.writeValueAsString(rows));
        } catch (Exception ignored) {
            // 忽略
        }
        return rows;
    }
}
