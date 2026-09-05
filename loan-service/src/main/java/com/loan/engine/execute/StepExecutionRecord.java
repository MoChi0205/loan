package com.loan.engine.execute;

import com.loan.engine.enums.StepResult;
import lombok.Builder;
import lombok.Getter;

/**
 * 单步执行记录（审计明细，落 t_match_rule_log）。
 *
 * <p>含双结果：{@code stepResult}（求值器/Handler 最终结果）与 {@code handlerStepResult}（Handler 原始结果），
 * 二者不一致时标 {@code mismatchFlag}（第 15 章双结果审计）。
 *
 * @author loan-platform
 */
@Getter
@Builder
public class StepExecutionRecord {

    /** 计划 ID */
    private final Long planId;

    /** 模块 ID */
    private final Long moduleId;

    /** 模块编码 */
    private final String moduleCode;

    /** 模块名称 */
    private final String moduleName;

    /** 步骤 ID */
    private final Long stepId;

    /** 规则编码 */
    private final String ruleCode;

    /** 条件字段编码 */
    private final String fieldCode;

    /** 执行表达式（field operator value） */
    private final String expression;

    /** 步骤最终结果（五态） */
    private final StepResult stepResult;

    /** Handler 原始结果（双结果审计） */
    private final StepResult handlerStepResult;

    /** 双结果不一致标记 */
    private final boolean mismatchFlag;

    /** 取值快照 / 拒绝原因 */
    private final String detail;
}
