package com.loan.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则模板实体（t_rule_template）。
 *
 * <p>规则模板 = 一条可复用规则的骨架：主表（分类/客群/说明）+ 字段定义（t_rule_template_field）+ 版本快照。
 * 上线后可被「导入为规则」实例化为 t_rule 真实规则，也可被策略模板步骤引用（rule_template_id）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_rule_template")
public class RuleTemplate {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 所属分类 ID（t_rule_category） */
    private Long categoryId;

    /** 客群（PERSONAL/ENTERPRISE/COMMON） */
    private String customerGroup;

    /** 模板说明 */
    private String description;

    /** 状态（ACTIVE/DISABLED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
