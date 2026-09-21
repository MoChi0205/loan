package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;

/** 客户画像快照复核请求。 */
@Data
public class InsightReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 复核结论：APPROVE 通过并设为当前版本 / REJECT 归档 */
    private String decision;

    /** 复核意见；驳回必填 */
    private String remark;
}
