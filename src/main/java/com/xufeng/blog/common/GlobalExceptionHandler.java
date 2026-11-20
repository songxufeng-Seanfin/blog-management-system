package com.xufeng.blog.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理器：任何 Controller 抛出的异常都在这里变成统一的 JSON，不会吐出错误堆栈页面 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusiness(BusinessException e) {
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegal(IllegalArgumentException e) {
        return ApiResult.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        return ApiResult.fail(500, "服务器内部错误：" + e.getMessage());
    }
}
