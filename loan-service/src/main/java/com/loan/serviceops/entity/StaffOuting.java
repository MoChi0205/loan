package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 员工上门拜访外出：本人提交申请 → 主管审核 → 出发/返回双打卡（图片 + 定位）。定位只存 AES 密文。 */
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
    /** 提交审核时间（本人提交申请，不接受他人代录）。 */
    private LocalDateTime submittedAt;
    private LocalDateTime actualDepartedAt;
    private String departedLocationCiphertext;
    /** 出发打卡照片 fileKey（必填，现场凭证）。 */
    private String departedPhotoKey;
    private LocalDateTime actualReturnedAt;
    private String returnedLocationCiphertext;
    /** 返回打卡照片 fileKey（必填，现场凭证）。 */
    private String returnedPhotoKey;
    private String destination;
    private String purpose;
    private String status;
    /** 审核人工号（禁止自审）。 */
    private String reviewerStaffCode;
    /** 审核人姓名（到人留痕）。 */
    private String reviewerName;
    private LocalDateTime reviewedAt;
    /** 审核意见（驳回必填原因）。 */
    private String reviewRemark;
    private String internalNote;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
