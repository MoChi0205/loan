package com.loan.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品新增/编辑请求（管理端）。
 *
 * <p>bankName 为银行名称（文本），后端按名称解析/创建 {@code t_bank_channel} 并回填 bankChannelId。
 *
 * @author loan-platform
 */
@Data
public class ProductSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品编码（业务唯一ID：产品身份标识，编辑/删除均以此为准，新增时唯一） */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 所属银行（名称，用于解析银行渠道） */
    private String bankName;

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
}
