package com.loan.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行产品全量库实体（t_bank_product）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_bank_product")
public class BankProduct {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品编码（内部代号化） */
    private String productCode;

    /** 所属银行渠道编码（业务编码） */
    private String bankChannelCode;

    /** 产品名称（仅管理端可见，客户端屏蔽） */
    private String productName;

    /** 客群（ENTERPRISE/PERSONAL） */
    private String customerGroup;

    /** 来源（CHANNEL_SELF/OURS） */
    private String source;

    /** 额度下限（元） */
    private BigDecimal amountMin;

    /** 额度上限（元） */
    private BigDecimal amountMax;

    /** 利率下限 */
    private BigDecimal rateMin;

    /** 利率上限 */
    private BigDecimal rateMax;

    /** 期限下限（月） */
    private Integer termMin;

    /** 期限上限（月） */
    private Integer termMax;

    /** 状态（DRAFT/PENDING/APPROVED/REJECTED/OFFLINE） */
    private String status;

    /** 创建人姓名 */
    private String createdBy;

    /** 更新人姓名 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
