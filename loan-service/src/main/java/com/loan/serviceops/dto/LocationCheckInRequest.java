package com.loan.serviceops.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LocationCheckInRequest {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;
    private String locationText;
    private LocalDateTime collectedAt;
}
