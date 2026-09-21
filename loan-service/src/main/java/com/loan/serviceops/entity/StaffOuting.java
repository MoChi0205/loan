package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 员工上门拜访外出与双打卡记录。定位字段只保存 AES 密文。 */
@Data
@TableName("t_staff_outing")
public class StaffOuting implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outingNo;
    private String staffCode;
    private String clientCode;
    private String appointmentNo;
    private String orderNo;
    private String outingType;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private LocalDateTime actualDepartedAt;
    private String departedLocationCiphertext;
    private LocalDateTime actualReturnedAt;
    private String returnedLocationCiphertext;
    private String destination;
    private String purpose;
    private String status;
    private String internalNote;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
