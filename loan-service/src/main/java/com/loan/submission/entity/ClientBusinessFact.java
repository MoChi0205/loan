package com.loan.submission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户经营事实实体（t_client_business_fact，提取层；匹配引擎数据源）。
 *
 * <p>field_code 与 {@code t_rule.field_code} / 匹配入参 facts.key 天然对齐；
 * FK 引用存业务编码 client_profile_code / submission_no。
 *
 * @author loan-platform
 */
@Data
@TableName("t_client_business_fact")
public class ClientBusinessFact implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（内部物理主键，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（业务ID） */
    private String clientProfileCode;

    /** 提交单号（业务ID） */
    private String submissionNo;

    /** 字段编码（= facts key = t_rule.field_code） */
    private String fieldCode;

    /** 字段值 */
    private String fieldValue;

    /** 字段类型（STRING/NUMBER/DATE/BOOL） */
    private String fieldType;

    /** 提取时间 */
    private LocalDateTime extractTime;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
