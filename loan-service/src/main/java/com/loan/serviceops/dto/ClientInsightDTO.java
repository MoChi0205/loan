package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工端客户画像快照响应。
 *
 * <p>只返回裁剪后的维度、风险提示、经营建议与来源链，不含客户物理 ID、内部报告号之外的内部评分对象。
 * 客户端视角只允许读取 {@code customerSummary}，该字段由服务端用固定模板生成，不含承诺性措辞。
 */
@Data
public class ClientInsightDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 画像快照业务编号 */
    private String snapshotNo;
    /** 客户业务编号 */
    private String clientCode;
    /** 关联报告号，可为空 */
    private String reportNo;
    /** 同一客户递增版本 */
    private Integer snapshotVersion;
    /** DRAFT / REVIEWED / CURRENT / ARCHIVED */
    private String status;
    /** 是否为当前生效版本 */
    private Boolean current;
    /** 生成时间 */
    private LocalDateTime generatedAt;
    /** 生成方式：AI / RULE / STAFF */
    private String generatedBy;
    /** 经营规模、现金流、负债、回款、客户集中度、资料完整度等维度 */
    private Object dimension;
    /** 风险提示（不是审批结论） */
    private Object riskFlags;
    /** 经营改善建议（仅员工视角） */
    private Object advice;
    /** 来源材料、授权时间、数据期间、可信度、核验状态 */
    private Object sourceSummary;
    /** 客户端可读的裁剪摘要 */
    private String customerSummary;
    /** 复核人姓名 */
    private String reviewedBy;
    /** 复核时间 */
    private LocalDateTime reviewedAt;
    /** 复核意见 */
    private String reviewRemark;
}
