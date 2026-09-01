package com.loan.api.dto.match;

import lombok.Data;

import java.io.Serializable;

/**
 * 步骤匹配结果（结果树叶子节点，Dubbo 跨系统契约）。
 *
 * @author loan-platform
 */
@Data
public class StepMatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 条件字段编码 */
    private String fieldCode;

    /** 执行表达式（field operator value） */
    private String expression;

    /** 步骤结果（PASS/FAIL/SKIP/SKIP_SEGMENT_MISMATCH/ERROR） */
    private String stepResult;

    /** 命中/拒绝/取值快照说明 */
    private String detail;
}
