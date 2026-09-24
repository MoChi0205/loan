package com.loan.serviceops.dto;

import lombok.Data;

@Data
public class OutingCreateRequest {
    private String appointmentNo;
    /** 无客户外出时使用；关联预约时由预约时间覆盖。 */
    private java.time.LocalDateTime plannedStart;
    private java.time.LocalDateTime plannedEnd;
    /** HOME_VISIT / GENERAL；未关联预约时必须为 GENERAL。 */
    private String outingType;
    private String destination;
    private String purpose;
    private String internalNote;
}
