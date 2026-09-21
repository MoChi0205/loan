package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 按角色裁剪后的客户活动时间线条目。 */
@Data
public class ActivityTimelineDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventNo;
    private String eventType;
    private String summary;
    private String visibility;
    private LocalDateTime happenedAt;
    private String staffCode;
    private String actorType;
}
