package com.loan.infrastructure.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志敏感信息脱敏：在日志事件落盘前重写消息，避免密钥/个人信息以明文写入日志文件。
 *
 * <p>覆盖范围（对齐《前后端小程序代码与交互优化计划》阶段2 P1）：
 * token / 密码 / 手机号 / 证件号 / OCR 原文中的证件类文本。
 *
 * <p>Log4j2 通过 RewriteAppender 包装目标 appender 生效：
 * <pre>{@code
 * <Rewrite name="RewriteBiz">
 *   <SensitiveRewritePolicy/>
 *   <AppenderRef ref="BizFile"/>
 * </Rewrite>
 * }</pre>
 *
 * <p>注意：只处理格式化后的消息文本，不改变 logger 名、异常栈以外的结构；
 * 未命中任何规则时直接返回原事件，避免额外的对象分配。
 */
@Plugin(name = "SensitiveRewritePolicy", category = "Core", elementType = "rewritePolicy", printObject = true)
public class SensitiveRewritePolicy implements RewritePolicy {

    /** JWT：三段式，形如 eyJhbGci....eyJ1c2Vy....signature */
    private static final Pattern JWT = Pattern.compile("(eyJ[A-Za-z0-9_-]{4,})\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    /**
     * 键值对形态的敏感字段：password / token / secret / authorization 等，值整体掩码。
     *
     * <p><b>值用长度界定（{@code .{1,256}}），不使用字符集。</b>
     *
     * <p>为什么不能用字符集：密码常含 {@code @ # ! $ % ^ & *} 等特殊字符。
     * 早先用白名单 {@code [A-Za-z0-9._~+/=-]{6,}} 匹配 {@code P@ssw0rd123} 时，
     * 只能吃到首字母 {@code P}（{@code @} 不在字符集内），长度不足下限导致
     * <b>整条规则不匹配</b>，密码完全没脱敏。
     * 字符集方案无论白名单还是排除式，本质都是"遇到不在集合内的字符就截断"，
     * 都会漏掩码；因此改为纯长度控制——任意字符都吃，只靠长度收边界。
     *
     * <p>上界 256：足以覆盖超长 JWT（通常 100~300 字符）。
     * 下界 1：避免空值或短密码因长度不足而漏匹配。
     */
    private static final Pattern KV_SECRET = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|authorization)"
                    + "\\s*[\"']?\\s*[:=]\\s*[\"']?)(.{1,256})");

    /** 中国大陆手机号：保留前 3 后 4 */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    /** 身份证号（18 位，末位可为 X）：保留前 6 后 4 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[\\dXx])(?!\\d)");

    /** 统一社会信用代码（18 位）：保留前 4 后 4 */
    private static final Pattern CREDIT_CODE = Pattern.compile(
            "(?<![A-Za-z0-9])([0-9A-HJ-NPQRTUWXY]{4})[0-9A-HJ-NPQRTUWXY]{10}([0-9A-HJ-NPQRTUWXY]{4})(?![A-Za-z0-9])");

    /** 银行卡号（16~19 位）：保留后 4 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{12,15}(\\d{4})(?!\\d)");

    private static final String MASK = "****";

    @PluginFactory
    public static SensitiveRewritePolicy createPolicy() {
        return new SensitiveRewritePolicy();
    }

    @Override
    public LogEvent rewrite(LogEvent source) {
        if (source == null || source.getMessage() == null) {
            return source;
        }
        String origin = source.getMessage().getFormattedMessage();
        if (origin == null || origin.isEmpty()) {
            return source;
        }
        String masked = mask(origin);
        if (masked.equals(origin)) {
            return source;
        }
        // 重建事件：仅替换消息体，保留时间戳/级别/MDC(traceId)/线程等上下文
        return new Log4jLogEvent.Builder(source).setMessage(new SimpleMessage(masked)).build();
    }

    /**
     * 按固定顺序执行脱敏规则。顺序有意：先处理结构化字段（JWT/键值对），
     * 再处理裸号码，避免号码被前面的规则部分改写后影响后续匹配。
     */
    static String mask(String text) {
        String out = text;
        out = KV_SECRET.matcher(out).replaceAll("$1" + MASK);
        out = JWT.matcher(out).replaceAll("$1." + MASK + "." + MASK);
        out = MOBILE.matcher(out).replaceAll("$1" + MASK + "$2");
        out = ID_CARD.matcher(out).replaceAll("$1" + MASK + MASK + "$2");
        out = CREDIT_CODE.matcher(out).replaceAll("$1" + MASK + MASK + "$2");
        out = BANK_CARD.matcher(out).replaceAll(MASK + MASK + MASK + "$1");
        return out;
    }

    /** 供单元测试与本地验证使用 */
    static String describeRules() {
        return "JWT, KV_SECRET, MOBILE, ID_CARD, CREDIT_CODE, BANK_CARD";
    }

    /** 便于排查：暴露当前是否命中（不写入日志，仅调试用） */
    static boolean isSensitive(String text) {
        return text != null && !text.equals(mask(text));
    }

    /** 避免外部误用 Matcher 造成状态泄漏的示例实现（保留以说明规则顺序） */
    static String maskFirstMatchOnly(String text) {
        Matcher m = MOBILE.matcher(text);
        if (m.find()) {
            return m.replaceAll("$1" + MASK + "$2");
        }
        return text;
    }
}
