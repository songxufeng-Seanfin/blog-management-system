package com.xufeng.blog.service;

import com.xufeng.blog.common.BusinessException;
import com.xufeng.blog.common.JwtUtil;
import com.xufeng.blog.dto.Dtos.LoginRequest;
import com.xufeng.blog.dto.Dtos.LoginVO;
import com.xufeng.blog.dto.Dtos.RegisterRequest;
import com.xufeng.blog.dto.Dtos.UserVO;
import com.xufeng.blog.entity.User;
import com.xufeng.blog.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/** 认证服务：注册、登录、发 JWT。密码只存哈希，不存明文。 */
@Service
public class AuthService {

    private final UserMapper users;
    private final JwtUtil jwt;

    public AuthService(UserMapper users, JwtUtil jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public LoginVO register(RegisterRequest req) {
        if (req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().length() < 6) {
            throw new BusinessException(400, "用户名不能为空，密码至少 6 位");
        }
        if (users.findByUsername(req.username().trim()) != null) {
            throw new BusinessException(409, "该用户名已被注册");
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPasswordHash(hash(req.password()));
        u.setNickname(req.nickname() == null || req.nickname().isBlank()
                ? req.username().trim() : req.nickname().trim());
        u.setRole("author");
        u.setCreatedAt(LocalDateTime.now());
        users.insert(u);
        return buildLoginVO(u);
    }

    public LoginVO login(LoginRequest req) {
        if (req.username() == null || req.password() == null) {
            throw new BusinessException(400, "用户名和密码必填");
        }
        User u = users.findByUsername(req.username().trim());
        if (u == null || !hash(req.password()).equals(u.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return buildLoginVO(u);
    }

    public UserVO me(Long userId) {
        User u = users.findById(userId);
        if (u == null) throw new BusinessException(404, "用户不存在");
        return toVO(u);
    }

    /** 教学用的简化哈希：生产环境应换成 BCrypt / Argon2 这类带随机盐的慢哈希 */
    public String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("blog::fixed-salt::" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("哈希计算失败", e);
        }
    }

    private LoginVO buildLoginVO(User u) {
        return new LoginVO(jwt.issue(u.getId(), u.getUsername()), toVO(u));
    }

    private UserVO toVO(User u) {
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getRole());
    }
}
