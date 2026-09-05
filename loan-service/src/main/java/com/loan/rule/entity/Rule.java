package com.loan.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则目录实体（t_rule）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_rule")
public class Rule {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 运算符（==/!=/>/</>=/<=/in/not_in/contains/not_contains/between/is_null/not_null） */
    private String operator;

    /** 值类型（STRING/NUMBER/DATE/LIST） */
    private String valueType;

    /** 规则值 */
    private String valueText;

    /** 客群（PERSONAL/ENTERPRISE/COMMON） */
    private String customerGroup;

    /** 规则说明 */
    private String description;

    /** 状态（DRAFT/ONLINE/DISABLED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
