package com.loan.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规则分类实体（t_rule_category）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_rule_category")
public class RuleCategory {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类编码 */
    private String categoryCode;

    /** 上级分类 ID（四分类树形） */
    private Long parentId;

    /** 分类名称 */
    private String categoryName;

    /** 客群（PERSONAL/ENTERPRISE/COMMON） */
    private String customerGroup;

    /** 状态（ACTIVE/DISABLED） */
    private String status;
}
