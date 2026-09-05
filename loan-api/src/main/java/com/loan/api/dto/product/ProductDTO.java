package com.loan.api.dto.product;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 银行产品 DTO（Dubbo 跨系统契约，产品库双层查询出参）。
 *
 * @author loan-platform
 */
@Data
public class ProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品编码（业务唯一ID，产品身份标识） */
    private String productCode;

    /** 所属银行名称 */
    private String bankName;

    /** 产品名称（仅管理端/内部系统可见，客户端屏蔽） */
    private String productName;

    /** 客群（ENTERPRISE/PERSONAL） */
    private String customerGroup;

    /** 额度区间描述（如 50万-300万） */
    private String amountRange;

    /** 利率区间描述 */
    private String rateRange;

    /** 期限区间描述 */
    private String termRange;

    /** 来源（CHANNEL_SELF 渠道自建 / OURS 我司录入） */
    private String source;

    /** 状态（DRAFT/PENDING/APPROVED/REJECTED/OFFLINE） */
    private String status;

    /** 所属银行渠道编码（业务编码） */
    private String bankChannelCode;

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

    /** 服务城市（逗号分隔，如 "湖北省武汉市, 湖北省宜昌市"） */
    private String serviceCities;

    /** 录入人 */
    private String createdBy;

    /** 录入时间 */
    private java.time.LocalDateTime createdAt;
}
