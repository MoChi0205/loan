package com.loan.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 哈希工具（静态方法）。
 *
 * <p>目前提供手机号 SHA-256 哈希，用于密文手机号（AES 加密，不可 LIKE）的等值检索：
 * 列表关键字搜索时，对 11 位手机号取哈希后与 {@code phone_hash} 列精确匹配。
 *
 * @author loan-platform
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * 计算字符串的 SHA-256 十六进制（小写，64 位）。
     *
     * @param raw 原始串（如明文手机号）
     * @return 64 位十六进制哈希
     */
    public static String sha256Hex(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
