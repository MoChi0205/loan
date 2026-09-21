package com.loan.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.loan.common.cache.UnifiedCacheService;
import com.loan.context.LoanUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportAnalyticsCacheServiceTest {

    private UnifiedCacheService cacheService;
    private ReportAnalyticsCacheService service;

    @BeforeEach
    void setUp() {
        ReportService reportService = mock(ReportService.class);
        cacheService = mock(UnifiedCacheService.class);
        when(cacheService.getOrLoad(any(), any(TypeReference.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        service = new ReportAnalyticsCacheService(reportService, cacheService);
    }

    @Test
    @DisplayName("顾问经营概览缓存键包含本人工号")
    void adviser_cache_is_isolated_by_staff_code() {
        service.overview(staff("ADVISER", "S-100", "D-1"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cacheService).getOrLoad(key.capture(), any(TypeReference.class), any(Supplier.class));
        assertTrue(key.getValue().contains("MY:S-100"));
    }

    @Test
    @DisplayName("部门经营分析缓存键包含部门编码")
    void team_cache_is_isolated_by_department() {
        service.operations("TEAM", 30, staff("DEPT_MANAGER", "M-1", "D-200"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cacheService).getOrLoad(key.capture(), any(TypeReference.class), any(Supplier.class));
        assertTrue(key.getValue().contains("TEAM:D-200"));
    }

    private LoanUser staff(String role, String userNo, String deptCode) {
        LoanUser user = new LoanUser();
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(role);
        user.setUserNo(userNo);
        user.setDeptCode(deptCode);
        return user;
    }
}
