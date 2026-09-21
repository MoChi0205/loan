package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;

/** 员工内部报告使用的规则执行摘要。 */
@Data
public class ReportRuleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleCode;
    private String expression;
    private String result;
}
