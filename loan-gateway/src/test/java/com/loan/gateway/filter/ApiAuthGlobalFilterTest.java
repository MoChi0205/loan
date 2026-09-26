package com.loan.gateway.filter;

import com.loan.gateway.auth.ApiRuleService;
import com.loan.gateway.auth.GatewayJwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void authenticatedLogoutIsCommonForEveryRoleAndUserType() {
        assertTrue(filter.isAuthenticatedCommonApi("/loan/api/auth/logout", "POST"));
        assertTrue(filter.isAuthenticatedCommonApi("/api/auth/logout", "POST"));
        assertFalse(filter.isAuthenticatedCommonApi("/loan/api/auth/logout", "GET"));
    }

    @Test
    void bossSystemConfigDenyRuleOverridesBusinessSuperRole() {
        Map<String, Object> rules = new LinkedHashMap<>();
        Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("BOSS", Arrays.asList("org:roleList", "org:saveRolePermission", "api-perm:", "debug:"));
        rules.put("roleDenyApiRules", deny);

        assertTrue(filter.isRoleExplicitlyDenied(rules, "BOSS", "org:saveRolePermission"));
        assertFalse(filter.isRoleExplicitlyDenied(rules, "BOSS", "org:staffPage"));
        assertFalse(filter.isRoleExplicitlyDenied(rules, "BOSS", "config:status"));
        assertFalse(filter.isRoleExplicitlyDenied(rules, "BOSS", "client:pageLite"));
        assertFalse(filter.isRoleExplicitlyDenied(rules, "SUPER", "org:saveRolePermission"));
    }

    @Test
    void successfulForwardMustNotFallThroughToSecondForbiddenResponse() {
        GatewayJwtUtil jwt = mock(GatewayJwtUtil.class);
        ApiRuleService ruleService = mock(ApiRuleService.class);
        filter = new ApiAuthGlobalFilter(jwt, ruleService);
        Claims claims = Jwts.claims();
        claims.put("userId", 1L);
        claims.put("userNo", "BOSS001");
        claims.put("userType", "STAFF");
        claims.put("roleCode", "BOSS");
        when(jwt.parse("valid-token")).thenReturn(claims);

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("apiKey", "report:overview");
        api.put("method", "GET");
        api.put("pathPattern", "/api/admin/report/overview");
        api.put("status", "ACTIVE");
        api.put("clientTypes", Arrays.asList("WEB", "MINI_APP"));
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("apis", Collections.singletonList(api));
        rules.put("superRoles", Collections.singletonList("BOSS"));
        when(ruleService.loadRules()).thenReturn(Mono.just(rules));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/loan/api/admin/report/overview")
                        .header("Authorization", "Bearer valid-token").build());
        AtomicInteger forwarded = new AtomicInteger();
        GatewayFilterChain chain = ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(forwarded.get() == 1);
        assertFalse(exchange.getResponse().getStatusCode() != null
                && exchange.getResponse().getStatusCode().is4xxClientError());
    }

    private Map<String, String> rule(String method, String pathPattern) {
        Map<String, String> rule = new LinkedHashMap<>();
        rule.put("method", method);
        rule.put("pathPattern", pathPattern);
        return rule;
    }
}
