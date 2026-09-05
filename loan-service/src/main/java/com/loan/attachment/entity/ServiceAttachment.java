package com.loan.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务资料附件实体（t_service_attachment，原文件永不出库，查看/下载统一动态水印）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_service_attachment")
public class ServiceAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号（业务唯一ID） */
    private String orderNo;

    /** 客户编码（业务唯一ID，同客户跨工单一键引用复用） */
    private String clientProfileCode;

    /** 关联初筛报告编号（诊断补充材料场景，业务ID：report+32位随机） */
    private String reportNo;

    /** 资料类型（ID_CARD/BUSINESS_LICENSE/FINANCIAL_STATEMENT/CONTRACT/DUE_DILIGENCE/OTHER） */
    private String attachmentType;

    /** 文件 key */
    private String fileKey;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 敏感标记 */
    private Integer sensitiveFlag;

    /** 已加密标记 */
    private Integer encryptedFlag;

    /** 跨单引用来源附件 ID */
    private Long referencedFromId;

    /** 上传人 */
    private Long uploadStaffId;

    /** 上传时间 */
    private LocalDateTime uploadTime;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
