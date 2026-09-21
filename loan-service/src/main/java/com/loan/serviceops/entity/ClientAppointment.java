package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客户预约与四类服务履约记录。 */
@Data
@TableName("t_client_appointment")
public class ClientAppointment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appointmentNo;
    private String clientCode;
    private String orderNo;
    private String hostStaffCode;
    private String appointmentType;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String locationName;
    private String locationDetail;
    private String status;
    private String customerConfirmStatus;
    private LocalDateTime actualArrivedAt;
    private LocalDateTime actualLeftAt;
    private String cancelReason;
    private String customerVisibleNote;
    private String internalNote;
    private String createdByType;
    private String createdByCode;
    private String sourceTerminal;
    private String rescheduledFromNo;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
