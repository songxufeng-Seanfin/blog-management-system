package com.xufeng.blog.common;

/** 统一响应外壳：所有接口都返回 { code, message, data }，前端只认这一种形状 */
public record ApiResult<T>(int code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
