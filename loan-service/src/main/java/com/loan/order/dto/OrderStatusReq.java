package com.loan.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务工单状态流转请求。
 *
 * @author loan-platform
 */
@Data
public class OrderStatusReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态（IN_SERVICE / DEAL / CANCEL / REFUND） */
    private String status;

    /** 成交金额（流转到 DEAL 必填） */
    private BigDecimal dealAmount;

    /** 成交时间（流转到 DEAL 必填，缺省当前时间） */
    private LocalDateTime dealTime;

    /** 成交时手动指定奖励金额（可选；填写则跳过比例计算，标记人工调整） */
    private BigDecimal rewardAmount;
}
