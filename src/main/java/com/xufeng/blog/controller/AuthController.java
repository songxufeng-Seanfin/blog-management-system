package com.xufeng.blog.controller;

import com.xufeng.blog.common.ApiResult;
import com.xufeng.blog.dto.Dtos.LoginRequest;
import com.xufeng.blog.dto.Dtos.LoginVO;
import com.xufeng.blog.dto.Dtos.RegisterRequest;
import com.xufeng.blog.dto.Dtos.UserVO;
import com.xufeng.blog.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 认证接口：注册 / 登录 / 我是谁 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/health/")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("app", "在线博客管理系统");
        m.put("java", System.getProperty("java.version"));
        m.put("db", "H2 (MySQL 兼容模式)");
        return ApiResult.ok(m);
    }

    @PostMapping("/auth/register/")
    public ApiResult<LoginVO> register(@RequestBody RegisterRequest req) {
        return ApiResult.ok(auth.register(req));
    }

    @PostMapping("/auth/login/")
    public ApiResult<LoginVO> login(@RequestBody LoginRequest req) {
        return ApiResult.ok(auth.login(req));
    }

    @GetMapping("/auth/me/")
    public ApiResult<UserVO> me(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) return ApiResult.fail(401, "请先登录");
        return ApiResult.ok(auth.me(((Number) uid).longValue()));
    }
}
