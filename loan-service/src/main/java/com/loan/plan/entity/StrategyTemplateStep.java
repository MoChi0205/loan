package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略模版步骤实体（t_strategy_template_step）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_strategy_template_step")
public class StrategyTemplateStep implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模版模块 ID */
    private Long templateModuleId;

    /** 规则模版 ID（可选，引用规则模版） */
    private Long ruleTemplateId;

    /** 直接引用规则 ID */
    private Long ruleId;

    /** 步骤顺序 */
    private Integer stepSort;

    /** 与下一步骤连接（AND/OR，支持 OR 组短路） */
    private String joinWithNext;

    /** 步骤级空跑（1=Handler REJECT 时链上 stepResult 改写 PASS，保留 handlerStepResult） */
    private Integer isDryRun;

    /** 步骤参数配置（schema 驱动动态表单，对齐 mds） */
    private String stepConfigJson;

    /** 步骤前置条件-字段（fact 字段码；与 conditionOperator 同时非空时生效） */
    private String conditionField;

    /** 步骤前置条件-运算符（EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK） */
    private String conditionOperator;

    /** 步骤前置条件-值（阈值或匹配列表） */
    private String conditionValue;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
