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
    private String ownerStaffCode;
    private String status;
    private LocalDateTime updatedAt;
}
