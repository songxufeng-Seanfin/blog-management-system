package com.xufeng.blog.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 安检口：所有 /api/** 请求先过这里。
 * 读接口公开；写接口必须带 Authorization: Bearer <token>，验过签名和有效期才放行。
 * 同时把"方法 / 路径 / 状态码 / 耗时"记进请求流水。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    private final JwtUtil jwt;
    private final ApiLogService apiLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwt, ApiLogService apiLog) {
        this.jwt = jwt;
        this.apiLog = apiLog;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        START.set(System.nanoTime());
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (!needToken(method, path)) return true;

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        Map<String, Object> claims = jwt.verify(token);
        if (claims == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(mapper.writeValueAsString(
                    ApiResult.fail(401, "缺少或无效的访问令牌，请先登录")));
            apiLog.add(method, path, 401, costMs(), "JWT 校验未通过");
            START.remove();
            return false;
        }
        request.setAttribute("userId", ((Number) claims.get("uid")).longValue());
        request.setAttribute("username", String.valueOf(claims.get("sub")));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        apiLog.add(request.getMethod(), request.getRequestURI(), response.getStatus(),
                   costMs(), ex == null ? "正常" : "异常：" + ex.getClass().getSimpleName());
        START.remove();
    }

    private long costMs() {
        Long s = START.get();
        return s == null ? 0 : (System.nanoTime() - s) / 1_000_000;
    }

    private boolean needToken(String method, String path) {
        // 「我是谁」即使走 GET 也必须验 token，否则拦截器不会把 userId 放进请求
        if (path.startsWith("/api/auth/me")) return true;
        if (path.startsWith("/api/health")
                || path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
                || path.startsWith("/api/demo/") || path.startsWith("/api/cache/")
                || path.startsWith("/api/stats/")) {
            return false;
        }
        if (method.equals("GET") || method.equals("OPTIONS") || method.equals("HEAD")) {
            return false;
        }
        return true;   // POST / PUT / DELETE 需要登录
    }
}
