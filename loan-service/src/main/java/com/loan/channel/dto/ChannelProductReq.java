package com.loan.channel.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 渠道端产品录入请求（银行员工录入银行产品 + 进件要求）。
 *
 * <p>约定：渠道账号只能以自己的所属银行为归属；银行渠道编码由后端按登录账号推导，不接收前端传入。
 *
 * @author loan-platform
 */
@Data
public class ChannelProductReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品编码（编辑时必填；新增时忽略） */
    private String productCode;

    /** 产品名称（仅管理端可见，客户端屏蔽） */
    private String productName;

    /** 客群（ENTERPRISE 企业 / PERSONAL 个人） */
    private String customerGroup;

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

    /** 纳税门槛（元/年） */
    private BigDecimal taxThreshold;

    /** 开票要求 */
    private String invoiceRequire;

    /** 进件要求结构化表单（JSON：准入条件/担保方式/材料清单等） */
    private String bizTermsJson;

    /** 合作有效期截止日期（ISO，上架合作库使用，默认 1 年） */
    private String cooperateUntil;
}
