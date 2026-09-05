package com.loan.channel.dto;

import lombok.Data;

import java.util.List;

/**
 * 渠道只读资源批量查询请求。
 *
 * <p>客户档案传客户业务编码，分析报告传报告业务编号；服务端统一去空、去重、保序，
 * 单次最多接收 100 条。</p>
 */
@Data
public class ChannelBatchQueryRequest {

    /** 客户业务编码或报告业务编号集合。 */
    private List<String> codes;
}
