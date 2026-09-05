package com.loan.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 计划步骤实体（t_admission_plan_step，步骤 = 单条规则）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_admission_plan_step")
public class AdmissionPlanStep implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 步骤业务编码（对外唯一标识，父级模块范围内唯一） */
    private String stepCode;

    /** 模块 ID */
    private Long moduleId;

    /** 父模块业务编码（接口入参使用，不落库） */
    @TableField(exist = false)
    private String moduleBizCode;

    /** 规则 ID */
    private Long ruleId;

    /** 规则版本 ID */
    private Long ruleVersionId;

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

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
