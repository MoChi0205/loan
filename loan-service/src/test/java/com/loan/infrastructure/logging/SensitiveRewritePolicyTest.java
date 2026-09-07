package com.loan.infrastructure.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感信息脱敏单测（纯 JUnit，不启动 Spring 上下文）。
 *
 * <p>覆盖《前后端小程序代码与交互优化计划》阶段2 P1 要求的：
 * token / 密码 / 手机号 / 证件号 / OCR 原文中的证件类文本。
 */
public class SensitiveRewritePolicyTest {

    @Test
    public void mobile_shouldKeepFirst3AndLast4() {
        assertEquals("手机号：138****8000", SensitiveRewritePolicy.mask("手机号：13800138000"));
    }

    @Test
    public void idCard_shouldKeepFirst6AndLast4() {
        assertEquals("证件：440302********1234", SensitiveRewritePolicy.mask("证件：440302199001011234"));
    }

    @Test
    public void passwordKeyValue_shouldMaskValue() {
        String masked = SensitiveRewritePolicy.mask("login req {\"password\":\"P@ssw0rd123\"}");
        // 注意：JUnit 5 的 message 参数在最后（JUnit 4 在最前）
        assertFalse(masked.contains("P@ssw0rd123"), "密码明文不应残留");
        assertTrue(masked.contains("password"), "应保留字段名便于定位");
    }

    /**
     * 回归用例：字符集方案下，长度不足会让整条规则不匹配，短密码完全不脱敏。
     * 改为长度界定后，任意非空长度的密码都应被掩码。
     */
    @Test
    public void shortPassword_shouldStillBeMasked() {
        assertFalse(SensitiveRewritePolicy.mask("pwd=a").contains("=a"), "单字符密码也应脱敏");
        assertFalse(SensitiveRewritePolicy.mask("pwd=ab").contains("=ab"), "双字符密码也应脱敏");
        assertFalse(SensitiveRewritePolicy.mask("pwd=abc").contains("=abc"), "三字符密码也应脱敏");
    }

    /**
     * 密码常含特殊字符。值改用长度界定（而非字符集）后应整段掩码，不留残余明文。
     */
    @Test
    public void passwordWithSpecialChars_shouldBeFullyMasked() {
        String[] pwds = {"P@ssw0rd123", "abc!@#$%^&*()", "P@w0rd", "a b c", "中文密码123", "x-y_z.w~q/p+v=d"};
        for (String p : pwds) {
            String masked = SensitiveRewritePolicy.mask("password=" + p);
            assertFalse(masked.contains(p), "密码应完全脱敏（不留明文）: " + p);
            assertTrue(masked.contains("password"), "应保留字段名便于定位: " + p);
        }
    }

    @Test
    public void jwt_shouldMaskSignaturePart() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjd9.SIGNATURE_PART_abc123";

        // 裸 JWT（无 token= 等键名前缀）：走 JWT 规则，保留 eyJ 头便于人工识别是哪类 token
        String bare = SensitiveRewritePolicy.mask("payload " + jwt);
        assertFalse(bare.contains("SIGNATURE_PART_abc123"), "JWT 签名段不应明文");
        assertTrue(bare.contains("eyJhbGciOiJIUzI1NiJ9"), "裸 JWT 应保留 eyJ 头便于识别");

        // 带 token= 键名：优先走 KV_SECRET，值整体掩码（比保留前缀更安全）
        String kv = SensitiveRewritePolicy.mask("token=" + jwt);
        assertFalse(kv.contains("SIGNATURE_PART_abc123"), "token 值不应明文");
        assertFalse(kv.contains("eyJ1c2VySWQiOjd9"), "token 值不应残留 payload");
    }

    @Test
    public void bankCard_shouldKeepLast4() {
        String masked = SensitiveRewritePolicy.mask("卡号 6222021234567890123");
        assertFalse(masked.contains("2021234567890"), "卡号中间段不应明文");
        assertTrue(masked.endsWith("0123"), "应保留后 4 位");
    }

    @Test
    public void creditCode_shouldKeepFirst4AndLast4() {
        String masked = SensitiveRewritePolicy.mask("信用代码 91440300MA5EPXXX8K");
        assertFalse(masked.contains("300MA5EPXX"), "信用代码中间段不应明文");
    }

    @Test
    public void plainText_shouldNotBeChanged() {
        String plain = "[邀请绑定] 绑定成功 clientCode=clientc142b4ad, referrerName=张三";
        // JUnit 5：message 参数在最后
        assertEquals(plain, SensitiveRewritePolicy.mask(plain), "无敏感信息时不应改动原文");
    }

    @Test
    public void isSensitive_shouldDetectOnlySensitiveText() {
        assertTrue(SensitiveRewritePolicy.isSensitive("手机号 13800138000"));
        assertFalse(SensitiveRewritePolicy.isSensitive("普通业务日志，无敏感信息"));
    }

    @Test
    public void mask_shouldHandleNullAndEmpty() {
        assertEquals("", SensitiveRewritePolicy.mask(""));
        assertFalse(SensitiveRewritePolicy.isSensitive(null));
    }

    @Test
    public void multipleSensitive_shouldAllBeMasked() {
        String src = "用户 13800138000 用身份证 440302199001011234 改密码 {\"password\":\"abc123456\"}";
        String masked = SensitiveRewritePolicy.mask(src);
        assertFalse(masked.contains("13800138000"));
        assertFalse(masked.contains("440302199001011234"));
        assertFalse(masked.contains("abc123456"));
    }
}
