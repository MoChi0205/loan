package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户报告详情响应。
 *
 * <p>该 DTO 仅承载经营分析字段，禁止增加银行、产品、准入规则、匹配数量、
 * 内部评级或审批预测字段。
 */
@Data
public class CustomerReportDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reportNo;
    private String status;
    private LocalDateTime createdAt;
    private String analysisStatus;
    private String analysisLabel;
    private String riskSummary;
    private List<String> riskFactors;
    private String analysisNotice;
    private String dataSourceNotice;
}
