package com.loan.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则模板字段定义实体（t_rule_template_field）。
 *
 * <p>一个规则模板包含 1..N 个字段定义，描述该规则涉及哪些字段、默认运算符与默认值。
 * 导入为规则时，按指定字段定义实例化一条 t_rule。
 *
 * @author loan-platform
 */
@Data
@TableName("t_rule_template_field")
public class RuleTemplateField {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则模板 ID */
    private Long templateId;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 字段类型（STRING/NUMBER/DATE/LIST） */
    private String fieldType;

    /** 默认运算符 */
    private String operator;

    /** 默认值 */
    private String defaultValue;

    /** 是否必填（1/0） */
    private Integer required;

    /** 顺序 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
