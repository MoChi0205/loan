package com.loan.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匹配规则日志实体（t_match_rule_log，双结果审计）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_match_rule_log")
public class MatchRuleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 匹配审计 ID */
    private Long traceId;

    /** 执行计划 ID */
    private Long planId;

    /** 模块 ID */
    private Long moduleId;

    /** 步骤 ID */
    private Long stepId;

    /** 规则编码 */
    private String ruleCode;

    /** 执行表达式 */
    private String expression;

    /** 步骤结果 */
    private String stepResult;

    /** Handler 结果（双结果审计） */
    private String handlerStepResult;

    /** 不一致标记 */
    private Integer mismatchFlag;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
