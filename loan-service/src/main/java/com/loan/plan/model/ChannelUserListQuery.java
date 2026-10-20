package com.loan.plan.model;

import lombok.Data;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 渠道名单组合查询模型。
 *
 * @author loan-platform
 */
@Data
public class ChannelUserListQuery {

    /** 渠道编码 */
    private String channelCode;

    /** 客群 */
    private String customerGroup;

    /** 名单类型 */
    private String listType;

    /** 名单键关键词 */
    private String keyword;

    /** 创建时间范围起（含当日） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdAtStart;

    /** 创建时间范围止（含当日） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdAtEnd;

    /** 页码 */
    private int page = 1;

    /** 每页大小 */
    private int size = 10;
}
