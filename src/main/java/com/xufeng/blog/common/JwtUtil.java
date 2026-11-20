package com.xufeng.blog.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手写 JWT：三段式 "头.载荷.签名"，签名用 JDK 自带的 HMAC-SHA256，不引入第三方库。
 * 头/载荷只是 Base64Url 编码（能看懂，不是加密），签名才是防篡改的关键。
 */
@Component
public class JwtUtil {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final String secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    /** 登录成功后发一张"通行证" */
    public String issue(Long userId, String username) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("uid", userId);
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        try {
            String body = ENC.encodeToString(mapper.writeValueAsBytes(payload));
            String signingInput = HEADER + "." + body;
            return signingInput + "." + sign(signingInput);
        } catch (Exception e) {
            throw new IllegalStateException("签发 token 失败", e);
        }
    }

    /** 校验通行证：签名不一致 / 已过期 → 返回 null */
    public Map<String, Object> verify(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String signingInput = parts[0] + "." + parts[1];
        String expected = sign(signingInput);
        // isEqual 做常量时间比较，避免攻击者用响应时间猜签名
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                   parts[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            Map<?, ?> claims = mapper.readValue(DEC.decode(parts[1]), Map.class);
            Object exp = claims.get("exp");
            if (exp == null || ((Number) exp).longValue() < Instant.now().getEpochSecond()) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            claims.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
