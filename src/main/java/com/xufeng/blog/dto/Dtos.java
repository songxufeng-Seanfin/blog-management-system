package com.xufeng.blog.dto;

import java.util.List;

/**
 * DTO = Data Transfer Object，数据在"浏览器 → Controller → Service"之间传递用的小盒子。
 * 用 Java 17 的 record：一行就声明好字段、构造器、getter，且不可修改。
 */
public final class Dtos {
    private Dtos() {}

    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String password, String nickname) {}
    public record ArticleRequest(String title, String summary, String content,
                                 Long categoryId, String status) {}
    public record CommentRequest(String authorName, String content) {}

    public record UserVO(Long id, String username, String nickname, String role) {}
    public record LoginVO(String token, UserVO user) {}

    public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {}

    /** 一键演示里每一步的结果，给看板画时间线用 */
    public record DemoStep(int step, String name, String method, String path,
                           int status, String note) {}
}
