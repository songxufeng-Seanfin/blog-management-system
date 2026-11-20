package com.xufeng.blog.controller;

import com.xufeng.blog.common.ApiResult;
import com.xufeng.blog.dto.Dtos.CommentRequest;
import com.xufeng.blog.entity.Category;
import com.xufeng.blog.entity.Comment;
import com.xufeng.blog.service.CategoryService;
import com.xufeng.blog.service.CommentService;
import com.xufeng.blog.service.StatsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 评论 / 分类 / 统计 的只读与互动接口 */
@RestController
@RequestMapping("/api")
public class BlogQueryController {

    private final CommentService commentService;
    private final CategoryService categoryService;
    private final StatsService statsService;

    public BlogQueryController(CommentService commentService, CategoryService categoryService,
                               StatsService statsService) {
        this.commentService = commentService;
        this.categoryService = categoryService;
        this.statsService = statsService;
    }

    @GetMapping("/articles/{id}/comments/")
    public ApiResult<List<Comment>> comments(@PathVariable Long id) {
        return ApiResult.ok(commentService.list(id));
    }

    @PostMapping("/articles/{id}/comments/")
    public ApiResult<Comment> addComment(@PathVariable Long id, @RequestBody CommentRequest req) {
        return ApiResult.ok(commentService.add(id, req));
    }

    @GetMapping("/categories/")
    public ApiResult<List<Category>> categories() {
        return ApiResult.ok(categoryService.list());
    }

    @GetMapping("/stats/overview/")
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(statsService.overview());
    }
}
