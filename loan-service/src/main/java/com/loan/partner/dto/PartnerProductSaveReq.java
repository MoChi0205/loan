package com.loan.partner.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合作库上架请求（评审决策：bankProductCode 业务编码关联，替代 Long bankProductId）。
 *
 * @author loan-platform
 */
@Data
public class PartnerProductSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 银行产品业务编码（必填，对应 t_bank_product.product_code） */
    private String bankProductCode;

    /** 合作库有效期（必填，到期自动下架） */
    private LocalDateTime cooperateUntil;

    /** 状态（可选，默认 ACTIVE；ACTIVE/EXPIRING/EXPIRED/OFFLINE） */
    private String status;
}
