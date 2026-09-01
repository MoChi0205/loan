package com.loan.lead.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.loan.infrastructure.security.AesTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 企业线索扩展实体（t_lead_ent_ext，1:1 关联 t_lead）。
 *
 * <p>敏感字段 {@code creditCode}（统一社会信用代码）落库 AES 加密 + {@code creditCodeHash}
 * SHA-256 哈希（查重/等值比对，不以明文）；读取解密需 Service 手动做
 * （@TableField typeHandler 仅写入生效，红线 #4）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_lead_ent_ext")
public class LeadEntExt implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联线索 ID（t_lead.id，1:1） */
    private Long leadId;

    /** 企业名称 */
    private String companyName;

    /** 统一社会信用代码（AES 加密） */
    @TableField(typeHandler = AesTypeHandler.class)
    private String creditCode;

    /** 信用代码 SHA-256 哈希（查重） */
    private String creditCodeHash;

    /** 行业 */
    private String industry;

    /** 成立年限（年） */
    private Integer foundYears;

    /** 纳税等级（A/B/C/D/M） */
    private String taxLevel;

    /** 年纳税额（元） */
    private BigDecimal annualTaxAmount;

    /** 年开票额（元） */
    private BigDecimal annualInvoiceAmount;

    /** 扩展预留 JSON */
    private String extJson;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
