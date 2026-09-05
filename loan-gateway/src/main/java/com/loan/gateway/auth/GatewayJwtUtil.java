package com.loan.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 网关 JWT 解析（与 loan-service JwtService 同密钥派生逻辑）。
 *
 * <p>密钥从配置 {@code jwt.secret} 读取，SHA-256 摘要为 32 字节（HS256 要求 ≥ 256 bit），
 * 与服务端签名完全一致，网关仅解析不签发。
 *
 * @author loan-platform
 */
@Slf4j
@Component
public class GatewayJwtUtil {

    /** JWT 密钥（与服务端一致） */
    @Value("${jwt.secret:CHANGE_ME_JWT_SECRET}")
    private String secret;

    /** 派生后的签名密钥 */
    private SecretKey key;

    /**
     * 初始化签名密钥。
     */
    @PostConstruct
    public void init() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = Keys.hmacShaKeyFor(digest);
        } catch (Exception e) {
            throw new IllegalStateException("初始化网关 JWT 密钥失败", e);
        }
    }

    /**
     * 解析 JWT（校验签名与过期）。
     *
     * @param token JWT 字符串
     * @return Claims，无效或过期返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.debug("[Gateway] JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
