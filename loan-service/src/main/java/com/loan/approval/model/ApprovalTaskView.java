package com.loan.approval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一审批任务读模型（DTO）。
 *
 * <p>聚合各业务线（额度分配 / 产品 / 下载等）的审批任务，对外提供一致的结构，
 * 便于审批中心统一渲染与序列化（Jackson 需要 getter，由 Lombok {@code @Data} 生成）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTaskView {

    /** 审批单号（业务唯一 ID，前缀 + 32 位随机）。 */
    private String approvalNo;

    /** 审批类型，例如 ALLOCATION / PRODUCT / DOWNLOAD。 */
    private String type;

    /** 审批标题。 */
    private String title;

    /** 申请人标识。 */
    private String applicant;

    /** 审批状态。 */
    private String status;

    /** 创建时间（ISO 字符串）。 */
    private String createdAt;

    /** 业务主键，用于回查原业务数据。 */
    private String businessKey;

    /** 原始业务 JSON，供前端按需解析。 */
    private Object rawJson;
}
