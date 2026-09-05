package com.loan.rule.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 规则新增/编辑请求（管理端）。
 *
 * @author loan-platform
 */
@Data
public class RuleSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码（业务唯一ID：规则身份标识，编辑/删除均以此为准，新增时唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 运算符 */
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
}
