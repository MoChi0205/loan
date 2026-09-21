package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;

/** 已落库的匹配结果快照；聚合报告不会重新执行匹配。 */
@Data
public class StaffMatchedProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productCode;
    private String productName;
    private String bankName;
    private String hitResult;
    private Integer matchScore;
    private String amountRange;
    private String rate;
    private String term;
}
