package com.loan.gateway.filter;

import com.loan.gateway.auth.ApiRuleService;
import com.loan.gateway.auth.GatewayJwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 渠道跨 mini 业务域精确授权边界测试。 */
class ApiAuthGlobalFilterTest {

    private ApiAuthGlobalFilter filter;
    private List<Map<String, String>> channelRules;

    @BeforeEach
    void setUp() {
        filter = new ApiAuthGlobalFilter(mock(GatewayJwtUtil.class), mock(ApiRuleService.class));
        channelRules = Arrays.asList(
                rule("POST", "/api/mini/lead/submit"),
                rule("GET", "/api/mini/lead/my"),
                rule("GET", "/api/mini/product/{code}"));
    }

    @Test
    void permitsChannelLeadCreateAndOwnListWithContextPath() {
        assertTrue(filter.matchesTypeApiRules(channelRules, "POST",
                new String[]{"/loan/api/mini/lead/submit", "/api/mini/lead/submit"}));
        assertTrue(filter.matchesTypeApiRules(channelRules, "GET",
                new String[]{"/loan/api/mini/lead/my", "/api/mini/lead/my"}));
    }

    @Test
    void permitsConfiguredProductDetailButRejectsWrongMethod() {
        assertTrue(filter.matchesTypeApiRules(channelRules, "GET",
                new String[]{"/api/mini/product/product-001"}));
        assertFalse(filter.matchesTypeApiRules(channelRules, "DELETE",
                new String[]{"/api/mini/product/product-001"}));
    }

    @Test
    void rejectsChannelMatchReportAndOtherMalformedRules() {
        assertFalse(filter.matchesTypeApiRules(channelRules, "POST",
                new String[]{"/api/mini/match/run"}));
        assertFalse(filter.matchesTypeApiRules(channelRules, "GET",
                new String[]{"/api/mini/report/list"}));
        assertFalse(filter.matchesTypeApiRules(Collections.singletonList(Collections.singletonMap("method", "GET")),
                "GET", new String[]{"/api/mini/lead/my"}));
        assertFalse(filter.matchesTypeApiRules(null, "GET", new String[]{"/api/mini/lead/my"}));
    }

    private Map<String, String> rule(String method, String pathPattern) {
        Map<String, String> rule = new LinkedHashMap<>();
        rule.put("method", method);
        rule.put("pathPattern", pathPattern);
        return rule;
    }
}
