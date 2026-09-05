package com.loan.engine.enums;

import lombok.Getter;

/**
 * 单个产品的匹配总结果（t_match_trace.total_result，第 10 章定稿三态卡片）。
 *
 * <p>客户端展示：PASS 可进件（绿）/ CONDITION 需补料（橙，列缺哪些维度）/ REJECT 暂不匹配（灰，附原因摘要）。
 * 全局前置风控命中直接 REJECT；客群串访记 SKIP_SEGMENT_MISMATCH；执行异常记 ERROR。
 *
 * @author loan-platform
 */
@Getter
public enum TotalResult {

    /** 可进件（命中全部核心维度） */
    PASS("PASS", "可进件"),

    /** 需补料（命中部分维度，列缺哪些维度） */
    CONDITION("CONDITION", "需补料"),

    /** 暂不匹配（不通过，附原因摘要） */
    REJECT("REJECT", "暂不匹配"),

    /** 客群串访（身份选择锁定后串客群） */
    SKIP_SEGMENT_MISMATCH("SKIP_SEGMENT_MISMATCH", "客群不匹配"),

    /** 执行异常 */
    ERROR("ERROR", "执行异常");

    /** 结果编码 */
    private final String code;

    /** 结果名称 */
    private final String name;

    TotalResult(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按编码解析（未命中返回 null）。
     *
     * @param code 结果编码
     * @return 结果枚举，未命中为 null
     */
    public static TotalResult fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TotalResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        return null;
    }
}
