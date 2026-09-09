package com.loan.apiperm.service;

import com.loan.apiperm.mapper.ApiPermissionMapper;
import com.loan.apiperm.mapper.RoleApiMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 接口权限同步默认矩阵测试。
 */
class ApiPermissionSyncServiceTest {

    /** 顾问必须使用精确最小授权，不得继承线索指派、客户分配等管理动作。 */
    @Test
    void adviserUsesExactLeastPrivilegeApis() {
        Map<String, List<String>> defaults = service().defaultRoleApis(keys(
                "lead:page", "lead:claim", "lead:assign", "lead:delete",
                "client:pageLite", "client:assign", "approval:productPage"));

        List<String> adviser = defaults.get("ADVISER");
        assertTrue(adviser.contains("lead:page"));
        assertTrue(adviser.contains("lead:claim"));
        assertTrue(adviser.contains("client:pageLite"));
        assertFalse(adviser.contains("lead:assign"));
        assertFalse(adviser.contains("lead:delete"));
        assertFalse(adviser.contains("client:assign"));
        assertFalse(adviser.contains("approval:productPage"));
    }

    /** 部门经理可处理团队分配和下载审批，但不得获得渠道产品终审。 */
    @Test
    void managerExcludesProductApprovalAndKeepsRequiredApprovalApis() {
        Map<String, List<String>> defaults = service().defaultRoleApis(keys(
                "approval:productPage", "approval:productDetail", "approval:productAudit",
                "approval:downloadPage", "approval:downloadAudit",
                "approval:allocationPending", "approval:allocationApprove",
                "approval:unifiedPending", "approval:unifiedCounts", "approval:unifiedAudit"));

        List<String> manager = defaults.get("DEPT_MANAGER");
        assertFalse(manager.contains("approval:productPage"));
        assertFalse(manager.contains("approval:productDetail"));
        assertFalse(manager.contains("approval:productAudit"));
        assertTrue(manager.contains("approval:downloadPage"));
        assertTrue(manager.contains("approval:downloadAudit"));
        assertTrue(manager.contains("approval:allocationPending"));
        assertTrue(manager.contains("approval:allocationApprove"));
        assertTrue(manager.contains("approval:unifiedPending"));
        assertTrue(manager.contains("approval:unifiedCounts"));
        assertTrue(manager.contains("approval:unifiedAudit"));
    }

    private ApiPermissionSyncService service() {
        return new ApiPermissionSyncService(
                mock(RequestMappingHandlerMapping.class),
                mock(ApiPermissionMapper.class),
                mock(RoleApiMapper.class),
                mock(ApiPermissionService.class));
    }

    private Set<String> keys(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
