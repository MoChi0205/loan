package com.loan.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 材料复核审批单实体（t_material_review）。
 *
 * <p>上传材料经大模型（VLM）识别后得到经营事实，先进入本表 PENDING_REVIEW 状态，
 * 由我司内部审批；审批通过后才回灌到客户提交单（{@code t_client_submission.data_json}）并置客可见，
 * 审批驳回则识别结果作废、绝不进入客户数据。</p>
 *
 * @author loan-platform
 */
@Data
@TableName("t_material_review")
public class MaterialReview implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 复核单号（业务唯一ID：matrev + 32 位随机） */
    private String reviewNo;

    /** 关联 OCR 记录主键（t_ocr_record.id） */
    private Long ocrRecordId;

    /** 资料类型（ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER） */
    private String bizType;

    /** 客户编码（业务ID，用于回灌提交单与客可见判定） */
    private String clientProfileCode;

    /** 关联报告编号（诊断材料回灌用，可空） */
    private String reportNo;

    /** 待复核事实 JSON（VLM 抽取后经 t_extract_field_def 映射的规范 facts） */
    private String pendingFactsJson;

    /** 复核状态（PENDING_REVIEW / APPROVED / REJECTED） */
    private String reviewStatus;

    /** 审批人工号（业务编码） */
    private String reviewerStaffCode;

    /** 审批意见（驳回必填） */
    private String reviewOpinion;

    /** 复核完成时间 */
    private LocalDateTime reviewTime;

    /** 回灌后的提交单号（审批通过并回灌后写入） */
    private String submissionNo;

    /** 创建人（上传操作人） */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
