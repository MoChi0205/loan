package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客户服务统一活动回放读模型；只允许追加写。 */
@Data
@TableName("t_client_activity_event")
public class ClientActivityEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private String clientCode;
    private String staffCode;
    private String eventType;
    private String sourceType;
    private String sourceNo;
    private LocalDateTime happenedAt;
    private String summary;
    private String visibility;
    private String actorType;
    private String actorCode;
    private String metadataJson;
    private LocalDateTime createdAt;
}
