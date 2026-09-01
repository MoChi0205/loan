package com.loan.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则模板版本实体（t_rule_template_version）。
 *
 * <p>发布规则模板时生成版本快照：version_no 递增，snapshot_json 保存字段定义完整快照。
 *
 * @author loan-platform
 */
@Data
@TableName("t_rule_template_version")
public class RuleTemplateVersion {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则模板 ID */
    private Long templateId;

    /** 版本号 */
    private Integer versionNo;

    /** 完整快照（字段定义 JSON） */
    private String snapshotJson;

    /** 状态 */
    private String status;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 发布人 */
    private String publishedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
