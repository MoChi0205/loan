package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 员工报告中的客户画像；敏感标识仅返回掩码。 */
@Data
public class StaffClientProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private String clientCode;
    private String customerGroup;
    private String contactName;
    private String contactPhoneMasked;
    private String enterpriseName;
    private String identityType;
    private String identityMasked;
    private String creditCodeMasked;
    private String ownerStaffCode;
    private String source;
    private String status;
    private String vipLevel;
    private LocalDateTime createdAt;
}
