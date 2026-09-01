package com.loan.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品审核工单实体（t_product_approval，新建与变更都走审核）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_product_approval")
public class ProductApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审核工单号（业务唯一ID：prdapr + 32 位随机） */
    private String approvalNo;

    /** 银行产品编码（业务编码） */
    private String bankProductCode;

    /** 提交渠道账号 ID（内部引用，渠道端功能预留） */
    private Long channelUserId;

    /** 申请类型（CREATE 新建 / UPDATE 变更） */
    private String applyType;

    /** 变更前快照 */
    private String beforeSnapshotJson;

    /** 变更后快照 */
    private String afterSnapshotJson;

    /** 重复产品标记 */
    private Integer duplicateFlag;

    /** 审核状态（PENDING/APPROVED/REJECTED） */
    private String approveStatus;

    /** 审核人工号（业务编码） */
    private String approverStaffCode;

    /** 审核意见（驳回必填） */
    private String approveOpinion;

    /** 审核时效 */
    private LocalDateTime timeoutAt;

    /** 审核完成时间 */
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
