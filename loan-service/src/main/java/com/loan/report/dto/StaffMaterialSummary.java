package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 员工报告中的本次提交与材料复核状态。 */
@Data
public class StaffMaterialSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private String submissionNo;
    private String submissionStatus;
    private LocalDateTime submittedAt;
    private String materialVersion;
    private Integer reviewTotal;
    private Integer pendingReviewCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private LocalDateTime latestReviewAt;
}
