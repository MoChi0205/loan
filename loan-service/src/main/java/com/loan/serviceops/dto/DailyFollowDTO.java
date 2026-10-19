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
    /** 企业名称（企业客群；个人客群为空）。 */
    private String enterpriseName;
    private String staffCode;
    private String nextAction;
    private LocalDateTime nextFollowAt;
}
