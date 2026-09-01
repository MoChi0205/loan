package com.loan.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 无水印下载审批单实体（t_attachment_download_approval，通过生成 24h 限时链接）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_attachment_download_approval")
public class AttachmentDownloadApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请单号（业务唯一ID：dldapr + 32 位随机） */
    private String approvalNo;

    /** 申请人工号（业务编码，仅内部员工） */
    private String applicantStaffCode;

    /** 资料清单（JSON 数组附件 ID） */
    private String attachmentIds;

    /** 用途说明（必填） */
    private String purpose;

    /** 期望使用期限 */
    private Integer expectDays;

    /** 审批人工号（业务编码） */
    private String approverStaffCode;

    /** 审批状态（PENDING/APPROVED/REJECTED） */
    private String approveStatus;

    /** 审批意见 */
    private String approveOpinion;

    /** 24h 限时下载链接 token（通过后生成） */
    private String linkToken;

    /** 链接过期时间 */
    private LocalDateTime linkExpireAt;

    /** 作废标记 */
    private Integer voidFlag;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
