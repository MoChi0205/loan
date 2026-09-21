package com.loan.serviceops.dto;

import lombok.Data;

@Data
public class OutingCreateRequest {
    private String appointmentNo;
    private String destination;
    private String purpose;
    private String internalNote;
}
