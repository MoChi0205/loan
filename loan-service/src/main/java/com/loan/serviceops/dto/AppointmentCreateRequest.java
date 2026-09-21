package com.loan.serviceops.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentCreateRequest {
    private String clientCode;
    private String hostStaffCode;
    private String orderNo;
    private String serviceMethod;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String locationName;
    private String locationDetail;
    private String customerVisibleNote;
    private String internalNote;
}
