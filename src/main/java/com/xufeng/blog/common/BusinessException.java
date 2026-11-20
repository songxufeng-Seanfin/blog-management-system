package com.xufeng.blog.common;

/** 业务异常：比如"文章不存在""没带 token"，由全局异常处理器翻译成统一响应 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }
}
