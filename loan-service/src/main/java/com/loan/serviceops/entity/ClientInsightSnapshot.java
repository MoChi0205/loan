package com.loan.serviceops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户画像版本快照：按版本链保存，不覆盖旧值。
 *
 * <p>同一客户最多一个 {@code status=CURRENT}，由数据库生成列 {@code current_flag} + 唯一键
 * {@code uk_insight_client_current} 兜底保证；该生成列不映射到实体，避免写入冲突。
 *
 * <p>客户端只读 {@code customer_summary} 这一层裁剪结果，完整维度与来源链仅员工端可见。
 */
@Data
@TableName("t_client_insight_snapshot")
public class ClientInsightSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotNo;
    private String clientCode;
    private String reportNo;
    private Integer snapshotVersion;
    private String dimensionJson;
    private String riskFlagsJson;
    private String adviceJson;
    private String sourceSummaryJson;
    private String customerSummary;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private String status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewRemark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
