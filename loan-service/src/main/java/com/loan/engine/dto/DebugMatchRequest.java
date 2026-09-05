package com.loan.engine.dto;

import lombok.Data;

import java.util.Map;

/**
 * 调试中心执行请求（调试复用生产引擎，可选写审计）。
 *
 * @author loan-platform
 */
@Data
public class DebugMatchRequest {

    /** 渠道编码（合作渠道，按渠道加载策略/计划） */
    private String channelCode;

    /** 客群（ENTERPRISE/PERSONAL） */
    private String customerGroup;

    /** 申请城市（市一级，非空时按产品服务城市精确筛选） */
    private String applyCity;

    /** 模拟客户经营事实：field_code → value */
    private Map<String, Object> facts;
}
