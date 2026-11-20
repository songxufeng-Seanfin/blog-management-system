package com.xufeng.blog.controller;

import com.xufeng.blog.common.ApiResult;
import com.xufeng.blog.dto.Dtos.ArticleRequest;
import com.xufeng.blog.dto.Dtos.PageResult;
import com.xufeng.blog.entity.Article;
import com.xufeng.blog.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 文章接口：读公开，写需要 JWT（安检口在 AuthInterceptor 里统一拦） */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService service;

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping("/")
    public ApiResult<PageResult<Article>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String kw,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int size) {
        return ApiResult.ok(service.page(status, categoryId, kw, page, size));
    }

    @GetMapping("/{id}/")
    public ApiResult<Article> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @PostMapping("/")
    public ApiResult<Article> create(@RequestBody ArticleRequest req, HttpServletRequest request) {
        return ApiResult.ok(service.create(req, userId(request)));
    }

    @PutMapping("/{id}/")
    public ApiResult<Article> update(@PathVariable Long id, @RequestBody ArticleRequest req) {
        return ApiResult.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}/")
    public ApiResult<String> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok("已删除文章 " + id);
    }

    @PostMapping("/{id}/publish/")
    public ApiResult<Article> publish(@PathVariable Long id) {
        return ApiResult.ok(service.publish(id));
    }

    @PostMapping("/{id}/unpublish/")
    public ApiResult<Article> unpublish(@PathVariable Long id) {
        return ApiResult.ok(service.unpublish(id));
    }

    private Long userId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) return 1L;   // 理论上不会走到，安检口已拦
        return ((Number) uid).longValue();
    }
}
