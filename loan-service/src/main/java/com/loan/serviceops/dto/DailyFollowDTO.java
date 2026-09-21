package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 今日待回访条目。 */
@Data
public class DailyFollowDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String followNo;
    private String clientCode;
    private String customerName;
    private String staffCode;
    private String nextAction;
    private LocalDateTime nextFollowAt;
}
