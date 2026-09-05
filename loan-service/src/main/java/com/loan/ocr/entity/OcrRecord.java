package com.loan.ocr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图片识别记录实体（t_ocr_record，AI 提取轨迹可追溯）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_ocr_record")
public class OcrRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景（CLIENT_AUTH/CLIENT_SUBMIT/CHANNEL_PRODUCT） */
    private String bizScene;

    /** 关联业务对象 ID */
    private Long bizId;

    /** 关联业务编码（业务ID：reportNo/clientCode/productCode；替代 biz_id 物理主键查询，红线#3） */
    private String bizCode;

    /** 原始文件 key */
    private String fileKey;

    /** 识别类型（provider 名：mock 默认未激活 / vlm 大模型视觉抽取） */
    private String ocrType;

    /** 腾讯云 OCR 接口名 */
    private String tencentAction;

    /** 提取结果 JSON */
    private String extractJson;

    /** 平均置信度 */
    private java.math.BigDecimal confidenceAvg;

    /** 人工修正 JSON */
    private String manualFixJson;

    /** 操作端（CUSTOMER/CHANNEL/STAFF） */
    private String operatorType;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String createdBy;

    /** 识别时间 */
    private LocalDateTime createdAt;

    /** 更新时间（识别结果二次回填 / 人工修正） */
    private LocalDateTime updatedAt;

    /** 复核状态（PENDING_REVIEW / APPROVED / REJECTED；材料复核审批门控用，缺省 PENDING_REVIEW 由 ingest 写入） */
    private String reviewStatus;

    /** 客户可见标志（0 不可见 / 1 可见；审批通过后置 1，审批前识别结果不入客可见数据） */
    private Integer visibleFlag;
}
