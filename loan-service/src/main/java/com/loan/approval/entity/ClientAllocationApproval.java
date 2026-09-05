package com.loan.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户归属分配审批单（t_client_allocation_approval）。
 *
 * <p>C2 归属流转：无归宿（公海 / 无主）客户申请分配，需上级或运营审批，
 * 审批通过后归属流转给申请人。业务 ID：alloc + 32 位随机。
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_allocation_approval")
public class ClientAllocationApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审批单号（业务唯一ID：alloc + 32 位随机） */
    private String approvalNo;

    /** 客户编码（业务唯一ID） */
    private String clientCode;

    /** 申请人员工工号 */
    private String applicantStaffCode;

    /** 申请时客户原归属顾问工号；转移审批时用于乐观并发校验。 */
    private String fromOwnerStaffCode;

    /** 待审唯一键（PENDING 时=clientCode，完成后置空，防并发重复申请） */
    private String pendingKey;

    /** 申请来源（ADVISER_CLAIM / MANAGER_ASSIGN） */
    private String applySource;

    /** 发起操作人员工工号（管理者指定时与目标顾问不同） */
    private String applyOperatorCode;

    /** 状态（PENDING/APPROVED/REJECTED） */
    private String approveStatus;

    /** 审批人员工工号（运营/超管） */
    private String approverStaffCode;

    /** 审批意见（驳回必填） */
    private String approveOpinion;

    /** 审批完成时间 */
    private LocalDateTime approvedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
