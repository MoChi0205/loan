package com.loan.common.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 业务 ID 生成器（架构准则：业务主键不以自增 id 为查询条件）。
 *
 * <p>业务 ID 规则：<b>业务前缀编码 + 32 位随机字符串</b>，全表唯一。
 * 例如 {@code CLIENT3f2a8b9c1d4e5f6a7b8c9d0e1f2a3b4c}（前缀 CLIENT + 32 位十六进制随机）。
 *
 * <p>约定：
 * <ul>
 *   <li>业务查询 / 对外暴露 / 跨系统关联一律使用业务 ID，<b>禁止使用自增主键 id</b>；</li>
 *   <li>每张业务表都必须有业务 ID 字段 + 唯一索引（uk_xxx）；</li>
 *   <li>业务 ID 为无业务含义的随机串，仅用于唯一标识与关联，不做排序/分页依据。</li>
 * </ul>
 *
 * @author loan-platform
 */
public final class BizIdGenerator {

    /** 随机源（线程安全，优于 Random） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 十六进制字符表 */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** 固定短编码随机字符表（小写字母 + 数字，兼容不区分大小写排序规则） */
    private static final char[] LOWER_ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private BizIdGenerator() {
    }

    /**
     * 生成业务 ID：前缀 + 32 位十六进制随机字符串（全小写）。
     *
     * @param prefix 业务前缀编码（如 client / lead / order / submit）
     * @return 业务 ID（小写前缀 + 32 位随机，如 client3f2a8b9c...）
     */
    public static String generate(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("业务前缀编码不能为空");
        }
        return prefix.toLowerCase(Locale.ROOT) + randomHex(32);
    }

    /**
     * 生成指定总长度的业务 ID。
     *
     * <p>仅用于经业务契约明确约定长度的记录；默认业务记录仍使用 {@link #generate(String)}。
     * 随机部分使用小写字母与数字，在短长度下比十六进制提供更大的随机空间；总长度必须大于前缀长度。
     *
     * @param prefix      业务前缀编码
     * @param totalLength 业务 ID 总长度
     * @return 固定总长度业务 ID
     */
    public static String generate(String prefix, int totalLength) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("业务前缀编码不能为空");
        }
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        int randomLength = totalLength - normalizedPrefix.length();
        if (randomLength <= 0) {
            throw new IllegalArgumentException("业务 ID 总长度必须大于前缀长度");
        }
        return normalizedPrefix + randomLowerAlphanumeric(randomLength);
    }

    /**
     * 生成纯 32 位随机字符串（无前缀，供已有前缀拼接场景）。
     *
     * @return 32 位十六进制随机字符串
     */
    public static String random32() {
        return randomHex(32);
    }

    /**
     * 生成指定长度的十六进制随机字符串。
     *
     * @param length 长度
     * @return 十六进制随机字符串
     */
    private static String randomHex(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("随机字符串长度必须大于 0");
        }
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        char[] out = new char[length];
        for (int i = 0; i < bytes.length && i * 2 < length; i++) {
            int b = bytes[i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            if (i * 2 + 1 < length) {
                out[i * 2 + 1] = HEX[b & 0x0f];
            }
        }
        return new String(out);
    }

    /** 生成指定长度的小写字母数字随机串。 */
    private static String randomLowerAlphanumeric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("随机字符串长度必须大于 0");
        }
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = LOWER_ALPHANUMERIC[RANDOM.nextInt(LOWER_ALPHANUMERIC.length)];
        }
        return new String(out);
    }
}
