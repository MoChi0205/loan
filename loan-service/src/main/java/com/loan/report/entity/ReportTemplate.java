package com.loan.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告模板版本实体（t_report_template，档位映射/免责声明/建议文案随版本锁定）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_report_template")
public class ReportTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 版本号 */
    private Integer versionNo;

    /** 模板名称 */
    private String templateName;

    /** 档位映射规则 JSON */
    private String gradeRuleJson;

    /** 免责声明文案 */
    private String disclaimerText;

    /** 多维建议文案库 JSON */
    private String adviceRulesJson;

    /** 企微咨询引导尾页配置 JSON */
    private String wecomGuideConfig;

    /** 报告溯源水印配置 JSON */
    private String watermarkConfig;

    /** 状态（ACTIVE/DISABLED） */
    private String status;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 发布人 */
    private String publishedBy;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
