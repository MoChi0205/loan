package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 服务台活跃工单条目；不返回银行产品或匹配结果。 */
@Data
public class DailyOrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String clientCode;
    private String customerName;
    /** 企业名称（企业客群；个人客群为空）。 */
    private String enterpriseName;
    private String ownerStaffCode;
    private String status;
    private LocalDateTime updatedAt;
}
