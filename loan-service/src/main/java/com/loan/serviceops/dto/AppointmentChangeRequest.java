package com.loan.serviceops.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentChangeRequest {
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String locationName;
    private String locationDetail;
    private String reason;
}
