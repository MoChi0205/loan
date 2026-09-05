package com.loan.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析（jjwt 0.11.5，对齐 tse JwtUtil）。
 *
 * <p>JWT 载荷保持轻量：userId + userType + userNo + roleCode（完整用户存 Redis）。
 * secret 从配置读取，经 SHA-256 摘要保证 32 字节（HS256 要求 ≥ 256 bit），避免占位符长度不足。
 *
 * @author loan-platform
 */
@Slf4j
@Component
public class JwtService {

    /** JWT 密钥 */
    @Value("${jwt.secret:CHANGE_ME_JWT_SECRET}")
    private String secret;

    /** 过期时间（毫秒），默认 24 小时 */
    @Value("${jwt.expire:86400000}")
    private long expireMillis;

    /** 派生后的签名密钥 */
    private SecretKey key;

    /**
     * 初始化签名密钥：secret 经 SHA-256 摘要得到固定 32 字节。
     */
    @PostConstruct
    public void init() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = Keys.hmacShaKeyFor(digest);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 JWT 密钥失败", e);
        }
    }

    /**
     * 签发 JWT。
     *
     * @param userId   内部用户 ID
     * @param userType 用户类型（STAFF/CHANNEL/CUSTOMER）
     * @param userNo   用户编号
     * @param roleCode 角色（仅员工）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String userType, String userNo, String roleCode) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("userId", userId);
        claims.put("userType", userType);
        claims.put("userNo", userNo);
        claims.put("roleCode", roleCode);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userNo == null ? String.valueOf(userId) : userNo)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT（校验签名与过期，未通过返回 null）。
     *
     * @param token JWT
     * @return Claims，无效或过期返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 JWT 是否有效（未过期）。
     *
     * @param token JWT
     * @return true 有效
     */
    public boolean validate(String token) {
        return parseToken(token) != null;
    }

    /**
     * 从 JWT 取 userId。
     *
     * @param token JWT
     * @return userId，无效返回 null
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object v = claims.get("userId");
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }
}
