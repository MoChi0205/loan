package com.loan.serviceops.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowRecordCreateRequest {
    private String orderNo;
    private String appointmentNo;
    private String channelType;
    private String resultCode;
    private String content;
    private String customerVisibleSummary;
    private String nextAction;
    private LocalDateTime nextFollowAt;
    private String visibility;
}
