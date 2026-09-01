package com.loan.engine.enums;

import lombok.Getter;

/**
 * 单步规则执行结果（五态，第 15 章定稿）。
 *
 * <p>与 mds 三态（PASS/REJECT/SKIP）的差异：新增 {@link #SKIP_SEGMENT_MISMATCH}（客群串访跳过）
 * 与 {@link #ERROR}（规则执行异常），用于更精确的审计追踪。
 *
 * @author loan-platform
 */
@Getter
public enum StepResult {

    /** 通过 */
    PASS("PASS", "通过"),

    /** 不通过 */
    FAIL("FAIL", "不通过"),

    /** 跳过（条件不适用 / 熔断放行等） */
    SKIP("SKIP", "跳过"),

    /** 短路跳过（上游链断裂/OR 组已满足，后续步骤不再执行） */
    SKIP_SHORT_CIRCUIT("SKIP_SHORT_CIRCUIT", "短路跳过"),

    /** 客群串访跳过（身份选择锁定后串客群硬分流） */
    SKIP_SEGMENT_MISMATCH("SKIP_SEGMENT_MISMATCH", "客群不匹配跳过"),

    /** 执行异常 */
    ERROR("ERROR", "执行异常");

    /** 结果编码 */
    private final String code;

    /** 结果名称 */
    private final String name;

    StepResult(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按编码解析（未命中返回 null）。
     *
     * @param code 结果编码
     * @return 结果枚举，未命中为 null
     */
    public static StepResult fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (StepResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        return null;
    }
}
