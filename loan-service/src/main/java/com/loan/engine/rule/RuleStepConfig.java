package com.loan.engine.rule;

import lombok.Builder;
import lombok.Getter;

/**
 * 单步规则执行配置（参考 mds RuleStepConfig）。
 *
 * <p>由 DB 的准入执行计划（t_admission_execution_plan → module → step → rule）组装而来，
 * 承载本步执行所需的全部元信息：所属模块、规则编码、条件表达式字段、是否全局前置风控。
 *
 * @author loan-platform
 */
@Getter
@Builder
public class RuleStepConfig {

    /** 执行计划 ID */
    private final Long planId;

    /** 计划模块 ID */
    private final Long moduleId;

    /** 计划步骤 ID */
    private final Long stepId;

    /** 模块编码 */
    private final String moduleCode;

    /** 模块名称 */
    private final String moduleName;

    /** 模块逻辑（AND：遇 FAIL 短路 / OR：遇 PASS 短路） */
    private final String logicType;

    /** 是否全局前置风控模块（命中直接 REJECT） */
    private final boolean globalPre;

    /** 规则编码（稳定目录键） */
    private final String ruleCode;

    /** 条件字段编码（从客户事实取数） */
    private final String fieldCode;

    /** 条件字段名称 */
    private final String fieldName;

    /** 运算符（==/!=/>/</>=/<=/in/not_in/contains/not_contains/between/is_null/not_null） */
    private final String operator;

    /** 值类型（STRING/NUMBER/DATE/LIST） */
    private final String valueType;

    /** 规则值（原始文本，求值器解析） */
    private final String valueText;

    /** 步骤顺序 */
    private final Integer stepSort;

    /** 与下一步骤连接（AND/OR，支持 OR 组短路） */
    private final String joinWithNext;

    /** 步骤级空跑（1=命中 REJECT 改写 stepResult 为 PASS，保留 handlerStepResult） */
    private final Integer isDryRun;

    /** 步骤参数配置（schema 驱动动态表单，对齐 mds） */
    private final String stepConfigJson;

    /** 步骤前置条件-字段（fact 字段码；与 conditionOperator 同时非空时生效） */
    private final String conditionField;

    /** 步骤前置条件-运算符（EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK，非空即启用条件判定） */
    private final String conditionOperator;

    /** 步骤前置条件-值（阈值或匹配列表） */
    private final String conditionValue;

    /**
     * 是否全局前置风控模块（语义化访问）。
     *
     * @return true 全局前置风控
     */
    public boolean isGlobalPre() {
        return globalPre;
    }

    /**
     * 是否 OR 模块（遇 PASS 短路）。
     *
     * @return true OR 逻辑
     */
    public boolean isOrLogic() {
        return "OR".equalsIgnoreCase(logicType);
    }

    /**
     * 是否空跑步骤（命中 REJECT 时链上 stepResult 改写 PASS）。
     *
     * @return true 空跑
     */
    public boolean isDryRun() {
        return isDryRun != null && isDryRun == 1;
    }

    /**
     * 与下一步骤是否 OR 连接。
     *
     * @return true OR
     */
    public boolean isOrJoinNext() {
        return "OR".equalsIgnoreCase(joinWithNext);
    }
}
