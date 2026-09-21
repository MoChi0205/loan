package com.loan.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.loan.common.cache.UnifiedCacheService;
import com.loan.context.LoanUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 经营看板二级缓存：Caffeine + Redis，按角色数据范围隔离缓存键。 */
@Service
@RequiredArgsConstructor
public class ReportAnalyticsCacheService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() { };
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() { };

    private final ReportService reportService;
    private final UnifiedCacheService cacheService;

    public Map<String, Object> overview(LoanUser user) {
        return cacheService.getOrLoad("report:overview:" + scopeKey(user), MAP_TYPE,
                () -> reportService.overview(user));
    }

    public Map<String, Object> operations(String scope, int days, LoanUser user) {
        String normalizedScope = scope == null || scope.trim().isEmpty()
                ? defaultScope(user) : scope.trim().toUpperCase();
        int normalizedDays = Math.max(7, Math.min(days, 365));
        return cacheService.getOrLoad("report:operations:" + operationScopeKey(normalizedScope, user) + ":"
                        + normalizedScope + ":" + normalizedDays, MAP_TYPE,
                () -> reportService.operations(scope, normalizedDays, user));
    }

    public List<Map<String, Object>> orderTrend(int months, LoanUser user) {
        int normalized = Math.max(6, Math.min(months, 24));
        return cacheService.getOrLoad("report:order-trend:" + scopeKey(user) + ":" + normalized,
                LIST_TYPE, () -> reportService.orderTrend(normalized, user));
    }

    public List<Map<String, Object>> rewardTrend(int months, LoanUser user) {
        int normalized = Math.max(6, Math.min(months, 24));
        return cacheService.getOrLoad("report:reward-trend:" + scopeKey(user) + ":" + normalized,
                LIST_TYPE, () -> reportService.rewardTrend(normalized, user));
    }

    /** XXL-Job 预热全司默认看板，实际老板/运营访问直接命中同一 GLOBAL 键。 */
    public void warmGlobalDashboard() {
        for (String role : new String[]{"BOSS", "OPERATOR", "SUPER", "SUPER_ADMIN"}) {
            LoanUser system = new LoanUser();
            system.setUserType(LoanUser.TYPE_STAFF);
            system.setRoleCode(role);
            system.setUserNo("REPORT_WARMER");
            overview(system);
            operations(null, 30, system);
            orderTrend(12, system);
            rewardTrend(12, system);
        }
    }

    private String scopeKey(LoanUser user) {
        String role = user == null || user.getRoleCode() == null ? "UNKNOWN" : user.getRoleCode().toUpperCase();
        if ("BOSS".equals(role) || "OPERATOR".equals(role) || "SUPER".equals(role) || "SUPER_ADMIN".equals(role)) {
            return "GLOBAL:" + role;
        }
        if ("DEPT_MANAGER".equals(role)) return "TEAM:" + safe(user.getDeptCode());
        return "MY:" + safe(user == null ? null : user.getUserNo());
    }

    private String operationScopeKey(String scope, LoanUser user) {
        if ("MY".equals(scope)) return "MY:" + safe(user == null ? null : user.getUserNo());
        if ("TEAM".equals(scope)) return "TEAM:" + safe(user == null ? null : user.getDeptCode());
        return scopeKey(user);
    }

    private String defaultScope(LoanUser user) {
        String role = user == null || user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if ("ADVISER".equals(role)) return "MY";
        if ("DEPT_MANAGER".equals(role)) return "TEAM";
        return "ALL";
    }

    private String safe(String value) {
        return value == null ? "NONE" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
