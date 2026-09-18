package com.loan.mini.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 客户侧资质与经营风险分析结果。
 *
 * <p>该对象不得承载银行、产品、准入规则、匹配数量、通过概率或审批预测。
 */
@Data
public class CustomerRiskAnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reportNo;
    private String analysisStatus;
    private String analysisLabel;
    private String riskSummary;
    private List<String> riskFactors;
    private String analysisNotice;
}
