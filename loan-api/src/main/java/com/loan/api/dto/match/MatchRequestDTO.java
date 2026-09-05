package com.loan.api.dto.match;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 匹配请求（Dubbo 跨系统契约）。
 *
 * @author loan-platform
 */
@Data
public class MatchRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客群（ENTERPRISE/PERSONAL） */
    private String customerGroup;

    /** 客户经营事实：field_code → value（数值为字符串） */
    private Map<String, Object> facts;
}
