package com.loan.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 服务工单新增请求（管理端手工建单 / 线下补录）。
 *
 * @author loan-platform
 */
@Data
public class OrderCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户编码（业务唯一ID，必填） */
    private String clientCode;

    /** 客群（ENTERPRISE / PERSONAL，必填） */
    private String customerGroup;

    /** 关联银行产品编码（业务编码，可选） */
    private String bankProductCode;

    /** 客户可见备注 */
    private String customerRemark;

    /** 内部备注（仅管理端可见） */
    private String internalRemark;

    /** 支付方式 */
    private String payType;

    /** 来源（MANUAL 手工建单 / OFFLINE_SUPPLEMENT 线下补录，缺省 MANUAL） */
    private String source;

    /** 来源单号（线下成交单号等） */
    private String sourceOrderNo;
}
