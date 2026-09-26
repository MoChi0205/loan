package com.loan.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 字段级加解密工具（静态方法，供 {@link AesTypeHandler} 及业务层直接调用）。
 *
 * <p>算法 AES/CBC/PKCS5Padding，密钥从配置 {@code aes.key} 读取（SHA-256 派生 32 字节），
 * 随机 IV 前缀拼接，密文 Base64 存储。
 *
 * @author loan-platform
 */
@Slf4j
@Component
public class AesUtils {

    /** 兼容历史迁移前以明文落库的手机号，避免将合法旧数据当作错误密文反复告警。 */
    private static final java.util.regex.Pattern LEGACY_PHONE =
            java.util.regex.Pattern.compile("1\\d{10}");

    /** 派生后的 32 字节密钥（静态，供 TypeHandler 无 Spring 上下文时调用） */
    private static volatile byte[] KEY_BYTES;

    /**
     * 注入配置密钥并派生 32 字节 AES-256 密钥（Spring 启动时调用）。
     *
     * @param configuredKey 配置密钥
     */
    @Value("${aes.key}")
    public void setConfiguredKey(String configuredKey) {
        if (configuredKey == null || configuredKey.trim().isEmpty()) {
            throw new IllegalStateException("缺少 aes.key 配置，禁止使用本地默认密钥");
        }
        try {
            KEY_BYTES = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化 AES 密钥失败", e);
        }
    }

    /**
     * 加密（随机 IV + 密文，Base64 存储）。
     *
     * @param plain 明文
     * @return Base64(iv + ciphertext)，明文为空返回 null
     */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.warn("AES 加密失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解密。
     *
     * @param cipherBase64 Base64(iv + ciphertext)
     * @return 明文，失败返回 null
     */
    public static String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isEmpty()) {
            return null;
        }
        // 历史数据中仍可能存在迁移前的明文手机号；读取时原样返回，写入新数据仍强制走 AES。
        if (LEGACY_PHONE.matcher(cipherBase64).matches()) {
            return cipherBase64;
        }
        try {
            byte[] data = Base64.getDecoder().decode(cipherBase64);
            if (data.length <= 16) {
                return null;
            }
            byte[] iv = new byte[16];
            System.arraycopy(data, 0, iv, 0, 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(data, 16, data.length - 16);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("AES 解密失败: {}", e.getMessage());
            return null;
        }
    }
}
