package com.loan.mini.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 逐条规则命中说明（小程序端脱敏展示，不含敏感字段值）。
 *
 * @author loan-platform
 */
@Data
public class RuleHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码（= t_rule.field_code / facts key） */
    private String ruleCode;

    /** 执行表达式（field operator value，值为客户自填经营事实，非身份敏感信息） */
    private String expression;

    /** 步骤结果（PASS/FAIL/SKIP/SKIP_SEGMENT_MISMATCH/ERROR） */
    private String result;
}
