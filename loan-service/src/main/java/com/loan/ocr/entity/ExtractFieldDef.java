package com.loan.ocr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提取字段定义实体（t_extract_field_def）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_extract_field_def")
public class ExtractFieldDef implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字段编码 */
    private String fieldCode;

    /** 字段名称 */
    private String fieldName;

    /** 字段类型（STRING/NUMBER/DATE/BOOL） */
    private String fieldType;

    /** 客群 */
    private String customerGroup;

    /** 提取规则 JSON */
    private String extractRuleJson;

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
