package com.loan.api.dto.rule;

import lombok.Data;

import java.io.Serializable;

/**
 * 准入规则 DTO（Dubbo 跨系统契约，规则目录查询出参）。
 *
 * @author loan-platform
 */
@Data
public class RuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（步骤表 ruleId 外键引用） */
    private Long id;

    /** 规则编码（业务唯一ID，规则身份标识） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 运算符（==/!=/>/</>=/<=/in/not_in/contains/not_contains/between/is_null/not_null） */
    private String operator;

    /** 规则值 */
    private String valueText;

    /** 表达式（field operator value） */
    private String expression;

    /** 分类编码（RISK/OPERATION/QUALIFICATION/PERSONAL） */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 客群（PERSONAL/ENTERPRISE/COMMON） */
    private String customerGroup;

    /** 说明 */
    private String description;

    /** 状态（DRAFT/ONLINE/DISABLED） */
    private String status;

    /** 值类型（STRING/NUMBER/DATE/LIST） */
    private String valueType;
}
