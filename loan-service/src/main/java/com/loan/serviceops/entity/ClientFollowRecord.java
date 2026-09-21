package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 不依赖工单存在的客户级跟进记录。 */
@Data
@TableName("t_client_follow_record")
public class ClientFollowRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String followNo;
    private String clientCode;
    private String orderNo;
    private String appointmentNo;
    private String staffCode;
    private String channelType;
    private String resultCode;
    private String content;
    private String customerVisibleSummary;
    private String nextAction;
    private LocalDateTime nextFollowAt;
    private String visibility;
    private String createdBy;
    private LocalDateTime createdAt;
}
