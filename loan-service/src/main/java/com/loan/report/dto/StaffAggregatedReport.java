package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 按报告编号聚合的员工内部经营咨询报告。 */
@Data
public class StaffAggregatedReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reportNo;
    private StaffReportDetail reportSummary;
    private StaffClientProfile clientProfile;
    private StaffMaterialSummary materialStatus;
    private Map<String, Object> businessAnalysis;
    private List<StaffMatchedProduct> matchedProducts;
    private String dataSourceNotice;
    private LocalDateTime aggregatedAt;
}
