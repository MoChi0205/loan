package com.loan.apiperm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.apiperm.mapper.ApiPermissionMapper;
import com.loan.apiperm.mapper.RoleApiMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 无角色用户类型规则发布测试。 */
class ApiPermissionServiceTypeRulesTest {

    private ApiPermissionService service;

    @BeforeEach
    void setUp() {
        ApiPermissionMapper apiMapper = mock(ApiPermissionMapper.class);
        RoleApiMapper roleMapper = mock(RoleApiMapper.class);
        when(apiMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(roleMapper.selectList(any())).thenReturn(Collections.emptyList());
        service = new ApiPermissionService(apiMapper, roleMapper,
                mock(StringRedisTemplate.class), new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void channelKeepsChannelPrefixAndPublishesOnlyExactMiniAllowList() {
        Map<String, Object> rules = service.buildRules();
        Map<String, List<String>> typeRules = (Map<String, List<String>>) rules.get("typeRules");
        assertEquals(Collections.singletonList("channel:"), typeRules.get("CHANNEL"));
        assertFalse(typeRules.get("CHANNEL").contains("mini:"));

        Map<String, List<Map<String, String>>> exactRules =
                (Map<String, List<Map<String, String>>>) rules.get("typeApiRules");
        List<Map<String, String>> channel = exactRules.get("CHANNEL");
        assertTrue(channel.stream().anyMatch(item -> "POST".equals(item.get("method"))
                && "/api/mini/lead/submit".equals(item.get("pathPattern"))));
        assertTrue(channel.stream().anyMatch(item -> "GET".equals(item.get("method"))
                && "/api/mini/lead/my".equals(item.get("pathPattern"))));
        assertFalse(channel.stream().anyMatch(item -> item.get("pathPattern").startsWith("/api/mini/match")));
        assertFalse(channel.stream().anyMatch(item -> item.get("pathPattern").startsWith("/api/mini/report")));
        assertFalse(channel.stream().anyMatch(item -> item.get("pathPattern").startsWith("/api/mini/order")));
    }
}
