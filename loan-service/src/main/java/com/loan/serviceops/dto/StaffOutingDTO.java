package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Web 员工外出视角。
 *
 * <p>不向客户或渠道返回；定位解密结果按操作人最小授权。
 * 照片只回 fileKey（前端凭 fileKey 走受控预览地址），不回存储路径。
 */
@Data
public class StaffOutingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String outingNo;
    private String staffCode;
    private String staffName;
    private String deptCode;
    private String clientCode;
    private String customerName;
    private String appointmentNo;
    private String orderNo;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    /** 提交审核时间。 */
    private LocalDateTime submittedAt;
    private LocalDateTime actualDepartedAt;
    private LocalDateTime actualReturnedAt;
    private String destination;
    private String purpose;
    private String status;
    private Boolean departureCheckInCompleted;
    private Boolean returnCheckInCompleted;
    /** 审核人工号（未审核为空）。 */
    private String reviewerStaffCode;
    /** 审核人姓名（未审核为空）。 */
    private String reviewerName;
    private LocalDateTime reviewedAt;
    /** 审核意见 / 驳回原因。 */
    private String reviewRemark;
    /** 出发打卡照片 fileKey。 */
    private String departedPhotoKey;
    /** 返回打卡照片 fileKey。 */
    private String returnedPhotoKey;
}
