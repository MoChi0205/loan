package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.util.PageOrder;
import com.loan.common.util.PageParams;
import com.loan.client.entity.ClientProfile;
import com.loan.client.entity.ClientRecycleConfig;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.mapper.ClientRecycleConfigMapper;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.common.ResultCode;
import com.loan.infrastructure.security.HashUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.mapper.RewardRecordMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表中心服务：经营总览 / 按月趋势（成交、奖励）/ 初筛报告查询。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<ClientScreening, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", ClientScreening::getCreatedAt);
    }

    private final ServiceOrderMapper orderMapper;
    private final RewardRecordMapper rewardRecordMapper;
    private final LeadMapper leadMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final ClientRecycleConfigMapper recycleConfigMapper;
    private final ClientLifecycleEventMapper lifecycleEventMapper;
    private final ClientScreeningMapper screeningMapper;
    private final BankProductMapper bankProductMapper;
    private final StaffMapper staffMapper;

    /** 报表全量可见角色（运营/超管/老板/超级管理员）：跨全部数据范围。 */
    private static final Set<String> REPORT_FULL_ROLES =
            new HashSet<>(java.util.Arrays.asList("BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER"));

    private static final Set<String> CLIENT_ASSIGN_ACTIONS =
            new HashSet<>(java.util.Arrays.asList("CLAIM_APPROVED", "MANAGER_ASSIGN", "MANUAL", "AUTO"));

    /**
     * 计算当前用户报表数据可见范围（按角色）：
     * - 全量角色（老板/运营/超管/超级管理员）→ null（不限制）
     * - 顾问 ADVISER → 仅本人归属（ownerStaffCode = 本人工号）
     * - 部门主管 DEPT_MANAGER → 本部门（本部门所有员工工号集合）
     * - 其余（未配置部门/未知角色）→ 空集（deny，看不到任何数据）
     *
     * @param user 当前登录用户
     * @return 受限的 ownerStaffCode 集合；null 表示全量；空集表示无权
     */
    private Set<String> buildOwnerScope(LoanUser user) {
        if (user == null) {
            return Collections.emptySet();
        }
        String role = user.getRoleCode();
        if (!StringUtils.hasText(role)) {
            return Collections.emptySet();
        }
        role = role.toUpperCase();
        if (REPORT_FULL_ROLES.contains(role)) {
            return null;
        }
        if ("ADVISER".equals(role)) {
            String no = user.getUserNo();
            return StringUtils.hasText(no) ? Collections.singleton(no) : Collections.emptySet();
        }
        if ("DEPT_MANAGER".equals(role)) {
            String dept = user.getDeptCode();
            if (!StringUtils.hasText(dept)) {
                return Collections.emptySet();
            }
            List<String> codes = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                            .eq(Staff::getDeptCode, dept)).stream()
                    .map(Staff::getStaffCode).collect(Collectors.toList());
            return new HashSet<>(codes);
        }
        return Collections.emptySet();
    }

    /**
     * 经营总览（全部走 COUNT/SUM 聚合 SQL，不拉全表）。
     * 按角色数据可见范围过滤（老板全量 / 部门主管本部门 / 顾问本人）。
     *
     * <p>返回：核心指标 + 环比（本月 vs 上月）+ 转化漏斗 + 分布维度（客群 / 产品 / 工单状态）。
     */
    public Map<String, Object> overview(LoanUser user) {
        Map<String, Object> m = new LinkedHashMap<>();

        // 角色数据可见范围：null=全量；空集=无权；非空=受限 ownerStaffCode 集合
        Set<String> scope = buildOwnerScope(user);
        // 由 scope 派生：归属 scope 的工单号 / 客户号集合（用于奖励、初筛报告二次过滤）
        // null = 全量（老板/运营/超管）；非空集合 = 受限；空集 = 无权（deny）
        Set<String> orderNos = null;
        Set<String> clientCodes = null;
        if (scope != null && !scope.isEmpty()) {
            orderNos = orderMapper.selectList(new LambdaQueryWrapper<ServiceOrder>()
                            .in(ServiceOrder::getOwnerStaffCode, scope)).stream()
                    .map(ServiceOrder::getOrderNo).collect(Collectors.toSet());
            clientCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                            .in(ClientProfile::getOwnerStaffCode, scope)).stream()
                    .map(ClientProfile::getClientCode).collect(Collectors.toSet());
        }

        long clientCount = scopedClientCount(scope);
        long leadCount = scopedLeadCount(scope);
        long orderCount = scopedOrderCount(scope, false);
        long dealOrderCount = scopedOrderCount(scope, true);
        BigDecimal dealAmountSum = scopedDealAmountSum(scope);
        long rewardCount = scopedRewardCount(scope, orderNos);
        BigDecimal rewardAmountSum = scopedRewardAmountSum(scope, orderNos);
        long screeningCount = scopedScreeningCount(scope, clientCodes);

        m.put("clientCount", clientCount);
        m.put("leadCount", leadCount);
        m.put("orderCount", orderCount);
        m.put("dealOrderCount", dealOrderCount);
        m.put("dealAmountSum", dealAmountSum);
        m.put("rewardCount", rewardCount);
        m.put("rewardAmountSum", rewardAmountSum);
        m.put("screeningCount", screeningCount);

        // 环比（本月新增 vs 上月新增，按同一角色范围过滤）
        LocalDateTime[] cur = monthBounds(0);
        LocalDateTime[] prev = monthBounds(1);
        m.put("clientCountDelta", pctDelta(
                scopedCountCreated(clientProfileMapper, ClientProfile::getCreatedAt, cur, scope, ClientProfile::getOwnerStaffCode),
                scopedCountCreated(clientProfileMapper, ClientProfile::getCreatedAt, prev, scope, ClientProfile::getOwnerStaffCode)));
        m.put("leadCountDelta", pctDelta(
                scopedCountCreated(leadMapper, Lead::getCreatedAt, cur, scope, Lead::getOwnerStaffCode),
                scopedCountCreated(leadMapper, Lead::getCreatedAt, prev, scope, Lead::getOwnerStaffCode)));
        m.put("orderCountDelta", pctDelta(
                scopedCountCreated(orderMapper, ServiceOrder::getCreatedAt, cur, scope, ServiceOrder::getOwnerStaffCode),
                scopedCountCreated(orderMapper, ServiceOrder::getCreatedAt, prev, scope, ServiceOrder::getOwnerStaffCode)));
        m.put("dealOrderCountDelta", pctDelta(scopedCountDealOrder(cur, scope), scopedCountDealOrder(prev, scope)));
        m.put("dealAmountSumDelta", pctDelta(scopedSumDealAmount(cur, scope), scopedSumDealAmount(prev, scope)));
        m.put("rewardCountDelta", pctDelta(
                scopedCountCreated(rewardRecordMapper, RewardRecord::getCreatedAt, cur, orderNos, RewardRecord::getServiceOrderNo),
                scopedCountCreated(rewardRecordMapper, RewardRecord::getCreatedAt, prev, orderNos, RewardRecord::getServiceOrderNo)));
        m.put("rewardAmountSumDelta", pctDelta(scopedSumRewardAmount(cur, orderNos), scopedSumRewardAmount(prev, orderNos)));
        m.put("screeningCountDelta", pctDelta(
                scopedScreeningCountCreated(scope, cur[0], cur[1]),
                scopedScreeningCountCreated(scope, prev[0], prev[1])));

        // 转化漏斗 + 分布维度严格复用同一归属范围，避免经理/顾问侧泄露全司数据。
        m.put("funnel", funnel(scope, orderNos));
        m.put("customerGroupDist", distribution("customerGroup", scope));
        m.put("productDist", distribution("product", scope));
        m.put("orderStatusDist", distribution("orderStatus", scope));
        return m;
    }

    /**
     * 客户运营分析（参考 TSE 的本人/团队/全司数据范围与公海流转口径）。
     *
     * <p>资产与 SLA 为查询时点快照；公海、分配与转化为近 days 天经营流量。
     * scope 由后端按角色强制校验，不能依赖前端隐藏实现权限隔离。</p>
     */
    public Map<String, Object> operations(String requestedScope, int requestedDays, LoanUser user) {
        int days = Math.max(7, Math.min(requestedDays, 365));
        String scopeName = resolveOperationsScope(requestedScope, user);
        Set<String> ownerScope = ownerScopeFor(scopeName, user);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scopeName);
        result.put("scopeLabel", scopeLabel(scopeName));
        result.put("periodDays", days);
        result.put("periodStart", from);
        result.put("periodEnd", to);
        result.put("availableScopes", availableOperationScopes(user));

        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("assigned", scopedClientCount(ownerScope));
        assets.put("companySea", countSea("ENTERPRISE", null));
        boolean teamSeaVisible = user != null && StringUtils.hasText(user.getDeptCode())
                && ("DEPT_MANAGER".equalsIgnoreCase(user.getRoleCode())
                || REPORT_FULL_ROLES.contains(normalizeRole(user)));
        assets.put("teamSeaVisible", teamSeaVisible);
        assets.put("teamSea", teamSeaVisible ? countSea("TEAM", user.getDeptCode()) : 0L);
        assets.put("cooldown", countVisibleCooldown(user, teamSeaVisible));
        result.put("clientAssets", assets);

        long entered = countAllocationActions(Collections.singleton("CLIENT_RECYCLE"), from, to,
                ownerScope, false, false, scopeName, user);
        long claimed = countPoolAssignments(from, to, ownerScope);
        long assigned = countAllocationActions(CLIENT_ASSIGN_ACTIONS, from, to,
                ownerScope, false, true, scopeName, user);
        long released = countSelfReleased(from, to, ownerScope);
        Map<String, Object> sea = new LinkedHashMap<>();
        sea.put("entered", entered);
        sea.put("claimed", claimed);
        sea.put("recycled", entered);
        sea.put("released", released);
        List<Map<String, Object>> seaDurations = lifecycleEventMapper.seaStayDurations(from, to, ownerScope);
        double avgStay = averageHours(seaDurations);
        sea.put("claimRate", rate(claimed, entered));
        sea.put("avgStayHours", seaDurations.isEmpty() ? null : round1(avgStay));
        sea.put("completedEpisodes", seaDurations.size());
        sea.put("avgStayMetricAvailable", !seaDurations.isEmpty());
        sea.put("currentPoolAgeHours", currentPoolAgeHours(ownerScope, scopeName, user));
        result.put("seaEfficiency", sea);

        Map<String, Object> allocation = new LinkedHashMap<>();
        allocation.put("assigned", assigned);
        allocation.put("claimed", claimed);
        allocation.put("recycled", entered);
        allocation.put("selfReleased", released);
        result.put("allocationFlow", allocation);

        ClientRecycleConfig cfg = recycleConfigMapper.selectOne(new LambdaQueryWrapper<ClientRecycleConfig>()
                .eq(ClientRecycleConfig::getConfigKey, "GLOBAL").last("LIMIT 1"));
        int recycleDays = cfg != null && cfg.getRecycleDays() != null && cfg.getRecycleDays() > 0
                ? cfg.getRecycleDays() : 30;
        int warnDays = cfg != null && cfg.getWarnDays() != null && cfg.getWarnDays() > 0
                ? cfg.getWarnDays() : 3;
        LocalDateTime overdueAt = to.minusDays(recycleDays);
        LocalDateTime dueSoonAt = to.minusDays(Math.max(0, recycleDays - warnDays));
        Map<String, Object> sla = new LinkedHashMap<>();
        sla.put("neverFollowed", countNeverFollowed(ownerScope));
        sla.put("overdue", countFollowClock(ownerScope, null, overdueAt, true));
        sla.put("dueSoon", countFollowClock(ownerScope, overdueAt, dueSoonAt, false));
        sla.put("recycleDays", recycleDays);
        sla.put("warnDays", warnDays);
        List<Map<String, Object>> firstDurations = lifecycleEventMapper.firstFollowDurations(from, to, ownerScope);
        sla.put("avgFirstFollowHours", firstDurations.isEmpty() ? null : round1(averageHours(firstDurations)));
        sla.put("firstFollowMetricAvailable", !firstDurations.isEmpty());
        sla.put("firstFollowCompletedEpisodes", firstDurations.size());
        result.put("followSla", sla);

        Map<String, Object> conversion = new LinkedHashMap<>();
        conversion.put("leads", scopedCountCreated(leadMapper, Lead::getCreatedAt,
                new LocalDateTime[]{from, to}, ownerScope, Lead::getOwnerStaffCode));
        conversion.put("clients", scopedCountCreated(clientProfileMapper, ClientProfile::getCreatedAt,
                new LocalDateTime[]{from, to}, ownerScope, ClientProfile::getOwnerStaffCode));
        conversion.put("screenings", scopedScreeningCountCreated(ownerScope, from, to));
        conversion.put("orders", scopedCountCreated(orderMapper, ServiceOrder::getCreatedAt,
                new LocalDateTime[]{from, to}, ownerScope, ServiceOrder::getOwnerStaffCode));
        conversion.put("deals", scopedCountDealOrder(new LocalDateTime[]{from, to}, ownerScope));
        conversion.put("dealAmount", scopedSumDealAmount(new LocalDateTime[]{from, to}, ownerScope));
        conversion.put("timeBasis", "线索/客户/初筛/工单按创建时间，成交按成交时间");
        result.put("conversion", conversion);
        return result;
    }

    private String resolveOperationsScope(String requested, LoanUser user) {
        String role = normalizeRole(user);
        String defaultScope = REPORT_FULL_ROLES.contains(role) ? "ALL"
                : "DEPT_MANAGER".equals(role) ? "TEAM" : "MY";
        String scope = StringUtils.hasText(requested) ? requested.trim().toUpperCase() : defaultScope;
        if (!availableOperationScopes(user).contains(scope)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色无权查看该经营统计范围");
        }
        return scope;
    }

    private List<String> availableOperationScopes(LoanUser user) {
        String role = normalizeRole(user);
        List<String> scopes = new ArrayList<>();
        if (user != null && StringUtils.hasText(user.getUserNo())) scopes.add("MY");
        if ("DEPT_MANAGER".equals(role) && StringUtils.hasText(user.getDeptCode())) scopes.add("TEAM");
        if (REPORT_FULL_ROLES.contains(role)) scopes.add("ALL");
        return scopes;
    }

    private Set<String> ownerScopeFor(String scope, LoanUser user) {
        if ("ALL".equals(scope)) return null;
        if ("MY".equals(scope)) return Collections.singleton(user.getUserNo());
        if ("TEAM".equals(scope)) {
            return staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                            .eq(Staff::getDeptCode, user.getDeptCode()))
                    .stream().map(Staff::getStaffCode).filter(StringUtils::hasText).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private String normalizeRole(LoanUser user) {
        return user == null || user.getRoleCode() == null ? "" : user.getRoleCode().trim().toUpperCase();
    }

    private String scopeLabel(String scope) {
        if ("ALL".equals(scope)) return "全司";
        if ("TEAM".equals(scope)) return "本团队";
        return "我的";
    }

    private long countSea(String seaLevel, String deptCode) {
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<ClientProfile>()
                .isNull(ClientProfile::getOwnerStaffCode).eq(ClientProfile::getSeaLevel, seaLevel);
        if (StringUtils.hasText(deptCode)) w.eq(ClientProfile::getSeaDeptCode, deptCode);
        return clientProfileMapper.selectCount(w);
    }

    private long countVisibleCooldown(LoanUser user, boolean teamSeaVisible) {
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<ClientProfile>()
                .isNull(ClientProfile::getOwnerStaffCode)
                .gt(ClientProfile::getAssignBlockedUntil, LocalDateTime.now());
        if (teamSeaVisible) {
            w.and(q -> q.eq(ClientProfile::getSeaLevel, "ENTERPRISE")
                    .or(x -> x.eq(ClientProfile::getSeaLevel, "TEAM")
                            .eq(ClientProfile::getSeaDeptCode, user.getDeptCode())));
        } else {
            w.eq(ClientProfile::getSeaLevel, "ENTERPRISE");
        }
        return clientProfileMapper.selectCount(w);
    }

    private long countAllocationActions(Set<String> actions, LocalDateTime from, LocalDateTime to,
                                        Set<String> scope, boolean fromNull, boolean toRequired,
                                        String scopeName, LoanUser user) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<LeadAllocationRecord> w = new LambdaQueryWrapper<LeadAllocationRecord>()
                .likeRight(LeadAllocationRecord::getLeadNo, "client")
                .in(LeadAllocationRecord::getActionType, actions)
                .ge(LeadAllocationRecord::getCreatedAt, from).lt(LeadAllocationRecord::getCreatedAt, to);
        if (fromNull) w.isNull(LeadAllocationRecord::getFromStaffCode);
        if (toRequired) w.isNotNull(LeadAllocationRecord::getToStaffCode);
        applyAllocationScope(w, scope);
        applyFullRolePersonalScope(w, scopeName, user);
        return allocationRecordMapper.selectCount(w);
    }

    private long countPoolAssignments(LocalDateTime from, LocalDateTime to, Set<String> scope) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<LeadAllocationRecord> w = new LambdaQueryWrapper<LeadAllocationRecord>()
                .likeRight(LeadAllocationRecord::getLeadNo, "client")
                .in(LeadAllocationRecord::getActionType, java.util.Arrays.asList("CLAIM_APPROVED", "MANAGER_ASSIGN"))
                .isNull(LeadAllocationRecord::getFromStaffCode)
                .isNotNull(LeadAllocationRecord::getToStaffCode)
                .ge(LeadAllocationRecord::getCreatedAt, from).lt(LeadAllocationRecord::getCreatedAt, to);
        if (scope != null) w.in(LeadAllocationRecord::getToStaffCode, scope);
        return allocationRecordMapper.selectCount(w);
    }

    private long countSelfReleased(LocalDateTime from, LocalDateTime to, Set<String> scope) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<LeadAllocationRecord> w = new LambdaQueryWrapper<LeadAllocationRecord>()
                .likeRight(LeadAllocationRecord::getLeadNo, "client")
                .eq(LeadAllocationRecord::getActionType, "CLIENT_RECYCLE")
                .like(LeadAllocationRecord::getRemark, "主动释放")
                .ge(LeadAllocationRecord::getCreatedAt, from).lt(LeadAllocationRecord::getCreatedAt, to);
        if (scope != null) w.in(LeadAllocationRecord::getFromStaffCode, scope);
        return allocationRecordMapper.selectCount(w);
    }

    private void applyAllocationScope(LambdaQueryWrapper<LeadAllocationRecord> w, Set<String> scope) {
        if (scope != null) {
            w.and(q -> q.in(LeadAllocationRecord::getFromStaffCode, scope)
                    .or().in(LeadAllocationRecord::getToStaffCode, scope));
        }
    }

    /** 全量角色选择“我的”时，流转记录仍必须限制到本人。 */
    private void applyFullRolePersonalScope(LambdaQueryWrapper<LeadAllocationRecord> w,
                                            String scopeName, LoanUser user) {
        if ("MY".equals(scopeName) && user != null && StringUtils.hasText(user.getUserNo())) {
            w.and(q -> q.eq(LeadAllocationRecord::getFromStaffCode, user.getUserNo())
                    .or().eq(LeadAllocationRecord::getToStaffCode, user.getUserNo()));
        }
    }

    private long countNeverFollowed(Set<String> scope) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<ClientProfile>()
                .isNotNull(ClientProfile::getOwnerStaffCode)
                .notExists("SELECT 1 FROM t_lead_allocation_record ar WHERE ar.lead_no = t_client_profile.client_code "
                        + "AND ar.action_type = 'FOLLOW_UP'");
        if (scope != null) w.in(ClientProfile::getOwnerStaffCode, scope);
        return clientProfileMapper.selectCount(w);
    }

    private long countFollowClock(Set<String> scope, LocalDateTime lowerExclusive,
                                  LocalDateTime upperInclusive, boolean includeNever) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<ClientProfile>()
                .isNotNull(ClientProfile::getOwnerStaffCode);
        if (includeNever) {
            w.and(q -> q.isNull(ClientProfile::getLastFollowedAt)
                    .or().le(ClientProfile::getLastFollowedAt, upperInclusive));
        } else {
            w.gt(ClientProfile::getLastFollowedAt, lowerExclusive)
                    .le(ClientProfile::getLastFollowedAt, upperInclusive);
        }
        if (scope != null) w.in(ClientProfile::getOwnerStaffCode, scope);
        return clientProfileMapper.selectCount(w);
    }

    private long scopedScreeningCountCreated(Set<String> scope, LocalDateTime from, LocalDateTime to) {
        if (scope != null && scope.isEmpty()) return 0;
        LambdaQueryWrapper<ClientScreening> w = new LambdaQueryWrapper<ClientScreening>()
                .ge(ClientScreening::getCreatedAt, from).lt(ClientScreening::getCreatedAt, to);
        if (scope != null) {
            w.exists("SELECT 1 FROM t_client_profile cp WHERE cp.client_code = t_client_screening.client_profile_code "
                    + "AND cp.owner_staff_code IN (" + sqlQuoted(scope) + ")");
        } else {
            w.exists("SELECT 1 FROM t_client_profile cp WHERE cp.client_code = t_client_screening.client_profile_code "
                    + "AND cp.owner_staff_code IS NOT NULL");
        }
        return screeningMapper.selectCount(w);
    }

    private String sqlQuoted(Set<String> values) {
        return values.stream().map(x -> "'" + x.replace("'", "''") + "'").collect(Collectors.joining(","));
    }

    private Double rate(long numerator, long denominator) {
        if (denominator == 0) return null;
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private double averageHours(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return 0d;
        double total = 0d;
        int count = 0;
        for (Map<String, Object> row : rows) {
            Object value = row.get("hours");
            if (value == null) continue;
            try { total += Double.parseDouble(value.toString()); count++; } catch (NumberFormatException ignored) { }
        }
        return count == 0 ? 0d : total / count;
    }

    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private Double currentPoolAgeHours(Set<String> scope, String scopeName, LoanUser user) {
        if (scope != null && scope.isEmpty()) return null;
        String seaLevel = "TEAM".equals(scopeName) ? "TEAM" : "ENTERPRISE";
        String seaDeptCode = "TEAM".equals(scopeName) ? user.getDeptCode() : null;
        Map<String, Object> row = lifecycleEventMapper.averageCurrentPoolAgeHours(seaLevel, seaDeptCode);
        Object value = row == null ? null : row.get("hours");
        if (value == null) return null;
        try { return round1(Double.parseDouble(value.toString())); }
        catch (NumberFormatException e) { return null; }
    }

    /** 客户数：按归属范围。 */
    private long scopedClientCount(Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<>();
        if (scope != null) {
            w.in(ClientProfile::getOwnerStaffCode, scope);
        } else {
            w.isNotNull(ClientProfile::getOwnerStaffCode);
        }
        return clientProfileMapper.selectCount(w);
    }

    /** 线索数：按归属范围（公海未归属线索不计入个人范围）。 */
    private long scopedLeadCount(Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<Lead> w = new LambdaQueryWrapper<>();
        if (scope != null) {
            w.in(Lead::getOwnerStaffCode, scope);
        } else {
            w.isNotNull(Lead::getOwnerStaffCode);
        }
        return leadMapper.selectCount(w);
    }

    /** 工单数：dealOnly=true 仅成交。 */
    private long scopedOrderCount(Set<String> scope, boolean dealOnly) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<ServiceOrder> w = new LambdaQueryWrapper<>();
        if (dealOnly) {
            w.eq(ServiceOrder::getStatus, ServiceOrder.STATUS_DEAL);
        }
        if (scope != null) {
            w.in(ServiceOrder::getOwnerStaffCode, scope);
        } else {
            w.isNotNull(ServiceOrder::getOwnerStaffCode);
        }
        return orderMapper.selectCount(w);
    }

    /** 成交金额（status=DEAL，按归属范围）。 */
    private BigDecimal scopedDealAmountSum(Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<ServiceOrder> w = new QueryWrapper<ServiceOrder>()
                .select("IFNULL(SUM(deal_amount),0) AS amt").eq("status", ServiceOrder.STATUS_DEAL);
        if (scope != null) {
            w.in("owner_staff_code", scope);
        } else {
            w.isNotNull("owner_staff_code");
        }
        return firstSum(w, orderMapper);
    }

    /** 奖励数：按归属工单范围。 */
    private long scopedRewardCount(Set<String> scope, Set<String> orderNos) {
        if (scope == null) {
            return rewardRecordMapper.selectCount(new LambdaQueryWrapper<RewardRecord>()
                    .exists("SELECT 1 FROM t_service_order o WHERE o.order_no = t_reward_record.service_order_no "
                            + "AND o.owner_staff_code IS NOT NULL"));
        }
        if (scope.isEmpty() || orderNos.isEmpty()) {
            return 0;
        }
        return rewardRecordMapper.selectCount(new LambdaQueryWrapper<RewardRecord>()
                .in(RewardRecord::getServiceOrderNo, orderNos));
    }

    /** 奖励金额：按归属工单范围。 */
    private BigDecimal scopedRewardAmountSum(Set<String> scope, Set<String> orderNos) {
        if (scope == null) {
            QueryWrapper<RewardRecord> w = new QueryWrapper<RewardRecord>()
                    .select("IFNULL(SUM(reward_amount),0) AS amt")
                    .exists("SELECT 1 FROM t_service_order o WHERE o.order_no = t_reward_record.service_order_no "
                            + "AND o.owner_staff_code IS NOT NULL");
            return firstSum(w, rewardRecordMapper);
        }
        if (scope.isEmpty() || orderNos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<RewardRecord> w = new QueryWrapper<RewardRecord>()
                .select("IFNULL(SUM(reward_amount),0) AS amt").in("service_order_no", orderNos);
        return firstSum(w, rewardRecordMapper);
    }

    /** 初筛报告数：按归属客户范围。 */
    private long scopedScreeningCount(Set<String> scope, Set<String> clientCodes) {
        if (scope == null) {
            return screeningMapper.selectCount(new LambdaQueryWrapper<ClientScreening>()
                    .exists("SELECT 1 FROM t_client_profile cp WHERE cp.client_code = t_client_screening.client_profile_code "
                            + "AND cp.owner_staff_code IS NOT NULL"));
        }
        if (scope.isEmpty() || clientCodes.isEmpty()) {
            return 0;
        }
        return screeningMapper.selectCount(new LambdaQueryWrapper<ClientScreening>()
                .in(ClientScreening::getClientProfileCode, clientCodes));
    }

    /** 通用：按角色范围统计某表某时间列落在 [range) 内的记录数。 */
    private <T> long scopedCountCreated(BaseMapper<T> mapper, SFunction<T, LocalDateTime> col,
                                       LocalDateTime[] range, Set<String> scope, SFunction<T, String> ownerCol) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<T> w = new LambdaQueryWrapper<>();
        w.ge(col, range[0]).lt(col, range[1]);
        if (scope != null) {
            w.in(ownerCol, scope);
        } else {
            w.isNotNull(ownerCol);
        }
        return mapper.selectCount(w);
    }

    /** 成交工单数（status=DEAL 且 deal_time 落在 [range)，按归属范围）。 */
    private long scopedCountDealOrder(LocalDateTime[] range, Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<ServiceOrder> w = new LambdaQueryWrapper<>();
        w.eq(ServiceOrder::getStatus, ServiceOrder.STATUS_DEAL)
                .ge(ServiceOrder::getDealTime, range[0]).lt(ServiceOrder::getDealTime, range[1]);
        if (scope != null) {
            w.in(ServiceOrder::getOwnerStaffCode, scope);
        } else {
            w.isNotNull(ServiceOrder::getOwnerStaffCode);
        }
        return orderMapper.selectCount(w);
    }

    /** 成交金额（status=DEAL 且 deal_time 落在 [range)，按归属范围）。 */
    private BigDecimal scopedSumDealAmount(LocalDateTime[] range, Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<ServiceOrder> w = new QueryWrapper<ServiceOrder>()
                .select("IFNULL(SUM(deal_amount),0) AS amt").eq("status", ServiceOrder.STATUS_DEAL)
                .ge("deal_time", range[0]).lt("deal_time", range[1]);
        if (scope != null) {
            w.in("owner_staff_code", scope);
        } else {
            w.isNotNull("owner_staff_code");
        }
        return firstSum(w, orderMapper);
    }

    /** 奖励金额（created_at 落在 [range)，按归属工单范围）。orderNos=null 表示全量。 */
    private BigDecimal scopedSumRewardAmount(LocalDateTime[] range, Set<String> orderNos) {
        if (orderNos == null) {
            QueryWrapper<RewardRecord> w = new QueryWrapper<RewardRecord>()
                    .select("IFNULL(SUM(reward_amount),0) AS amt")
                    .ge("created_at", range[0]).lt("created_at", range[1])
                    .exists("SELECT 1 FROM t_service_order o WHERE o.order_no = t_reward_record.service_order_no "
                            + "AND o.owner_staff_code IS NOT NULL");
            return firstSum(w, rewardRecordMapper);
        }
        if (orderNos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<RewardRecord> w = new QueryWrapper<RewardRecord>()
                .select("IFNULL(SUM(reward_amount),0) AS amt")
                .ge("created_at", range[0]).lt("created_at", range[1])
                .in("service_order_no", orderNos);
        return firstSum(w, rewardRecordMapper);
    }

    /**
     * 转化漏斗：线索池 → 客户(转正) → 服务工单 → 成交 → 发放奖励（累计口径）。
     */
    public List<Map<String, Object>> funnel() {
        return funnel(null, null);
    }

    private List<Map<String, Object>> funnel(Set<String> scope, Set<String> orderNos) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stage("线索池", scopedLeadCount(scope)));
        list.add(stage("客户(转正)", scopedClientCount(scope)));
        list.add(stage("服务工单", scopedOrderCount(scope, false)));
        list.add(stage("成交", scopedOrderCount(scope, true)));
        list.add(stage("发放奖励", scopedRewardCount(scope, orderNos)));
        return list;
    }

    /**
     * 分布维度：客群 / 产品 / 工单状态。
     *
     * @param dim customerGroup | product | orderStatus
     */
    public List<Map<String, Object>> distribution(String dim) {
        return distribution(dim, null);
    }

    private List<Map<String, Object>> distribution(String dim, Set<String> scope) {
        if (scope != null && scope.isEmpty()) return new ArrayList<>();
        switch (dim) {
            case "customerGroup": {
                QueryWrapper<ClientProfile> wrapper = new QueryWrapper<ClientProfile>()
                        .select("COALESCE(customer_group, 'UNKNOWN') AS name", "COUNT(*) AS value");
                if (scope != null) wrapper.in("owner_staff_code", scope);
                else wrapper.isNotNull("owner_staff_code");
                wrapper.groupBy("customer_group");
                List<Map<String, Object>> rows = clientProfileMapper.selectMaps(wrapper);
                return rows.stream().map(r -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("name", r.get("name"));
                    x.put("value", r.get("value"));
                    return x;
                }).collect(Collectors.toList());
            }
            case "product": {
                QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<ServiceOrder>()
                        .select("bank_product_code AS code", "COUNT(*) AS orderCount",
                                "SUM(CASE WHEN status = 'DEAL' THEN 1 ELSE 0 END) AS dealCount",
                                "IFNULL(SUM(CASE WHEN status = 'DEAL' THEN deal_amount ELSE 0 END), 0) AS dealAmount")
                        .isNotNull("bank_product_code").ne("bank_product_code", "");
                if (scope != null) wrapper.in("owner_staff_code", scope);
                else wrapper.isNotNull("owner_staff_code");
                wrapper.groupBy("bank_product_code").orderByDesc("dealAmount").last("LIMIT 8");
                List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
                java.util.Set<String> codes = new java.util.HashSet<>();
                for (Map<String, Object> r : rows) {
                    Object c = r.get("code");
                    if (c != null) {
                        codes.add(c.toString());
                    }
                }
                Map<String, String> nameMap = codes.isEmpty() ? java.util.Collections.emptyMap()
                        : bankProductMapper.selectList(new LambdaQueryWrapper<BankProduct>()
                                .in(BankProduct::getProductCode, codes)).stream()
                                .collect(Collectors.toMap(BankProduct::getProductCode, BankProduct::getProductName));
                for (Map<String, Object> r : rows) {
                    Object c = r.get("code");
                    r.put("name", c == null ? "未关联产品" : nameMap.getOrDefault(c.toString(), c.toString()));
                }
                return rows;
            }
            case "orderStatus": {
                QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<ServiceOrder>()
                        .select("status AS name", "COUNT(*) AS value");
                if (scope != null) wrapper.in("owner_staff_code", scope);
                else wrapper.isNotNull("owner_staff_code");
                wrapper.groupBy("status");
                List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
                return rows.stream().map(r -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("name", r.get("name"));
                    x.put("value", r.get("value"));
                    return x;
                }).collect(Collectors.toList());
            }
            default:
                return new ArrayList<>();
        }
    }

    /** 漏斗单阶段。 */
    private Map<String, Object> stage(String name, long value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    /** 当月（back=0）或往回第 N 月（back=1 上月）的 [起, 止) 时间边界。 */
    private LocalDateTime[] monthBounds(int back) {
        LocalDate start = LocalDate.now().minusMonths(back).withDayOfMonth(1);
        return new LocalDateTime[]{ start.atStartOfDay(), start.plusMonths(1).atStartOfDay() };
    }

    /** 通用：统计某表某时间列落在 [range) 内的记录数。 */
    private <T> long countCreated(BaseMapper<T> mapper, SFunction<T, LocalDateTime> col, LocalDateTime[] range) {
        LambdaQueryWrapper<T> w = new LambdaQueryWrapper<>();
        w.ge(col, range[0]).lt(col, range[1]);
        return mapper.selectCount(w);
    }

    /** 成交工单（status=DEAL 且 deal_time 落在 [range)）。 */
    private long countDealOrder(LocalDateTime[] range) {
        return orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getStatus, ServiceOrder.STATUS_DEAL)
                .ge(ServiceOrder::getDealTime, range[0]).lt(ServiceOrder::getDealTime, range[1]));
    }

    /** 成交金额（status=DEAL 且 deal_time 落在 [range)）。 */
    private BigDecimal sumDealAmount(LocalDateTime[] range) {
        QueryWrapper<ServiceOrder> w = new QueryWrapper<ServiceOrder>()
                .select("IFNULL(SUM(deal_amount), 0) AS amt").eq("status", ServiceOrder.STATUS_DEAL)
                .ge("deal_time", range[0]).lt("deal_time", range[1]);
        return firstSum(w, orderMapper);
    }

    /** 奖励金额（created_at 落在 [range)）。 */
    private BigDecimal sumRewardAmount(LocalDateTime[] range) {
        QueryWrapper<RewardRecord> w = new QueryWrapper<RewardRecord>()
                .select("IFNULL(SUM(reward_amount), 0) AS amt")
                .ge("created_at", range[0]).lt("created_at", range[1]);
        return firstSum(w, rewardRecordMapper);
    }

    /** 通用：取聚合 SUM 的首行值。 */
    private <T> BigDecimal firstSum(QueryWrapper<T> w, BaseMapper<T> mapper) {
        List<Map<String, Object>> rows = mapper.selectMaps(w);
        if (rows.isEmpty() || rows.get(0).get("amt") == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rows.get(0).get("amt").toString());
    }

    /** 环比百分比（长整型口径，1 位小数；无基数且无增长返回 null）。 */
    private Double pctDelta(long cur, long prev) {
        if (prev == 0 && cur == 0) {
            return null;
        }
        if (prev == 0) {
            return 100.0;
        }
        return Math.round((cur - prev) * 1000.0 / prev) / 10.0;
    }

    /** 环比百分比（金额口径，1 位小数）。 */
    private Double pctDelta(BigDecimal cur, BigDecimal prev) {
        if (prev == null) {
            prev = BigDecimal.ZERO;
        }
        if (cur == null) {
            cur = BigDecimal.ZERO;
        }
        if (prev.compareTo(BigDecimal.ZERO) == 0 && cur.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (prev.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        BigDecimal ratio = cur.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(1000));
        return Math.round(ratio.doubleValue()) / 10.0;
    }


    /** 角色无权时返回全零趋势占位（保持前端结构稳定）。 */
    private List<Map<String, Object>> emptyTrend(int months) {
        int n = Math.max(6, Math.min(months, 24));
        LocalDate start = LocalDate.now().minusMonths(n - 1L).withDayOfMonth(1);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", start.plusMonths(i).toString().substring(0, 7));
            row.put("count", 0L);
            row.put("amount", BigDecimal.ZERO);
            trend.add(row);
        }
        return trend;
    }

    /**
     * 近 N 个月成交趋势（按月 COUNT/SUM 聚合，走 idx_status_dealtime / idx_created_at，避免全表扫描）。
     */
    public List<Map<String, Object>> orderTrend(int months, LoanUser user) {
        Set<String> scope = buildOwnerScope(user);
        if (scope != null && scope.isEmpty()) {
            return emptyTrend(months);
        }
        int n = Math.max(6, Math.min(months, 24));
        LocalDate start = LocalDate.now().minusMonths(n - 1L).withDayOfMonth(1);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate monthStart = start.plusMonths(i);
            LocalDateTime from = monthStart.atStartOfDay();
            LocalDateTime to = monthStart.plusMonths(1).atStartOfDay();
            QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<ServiceOrder>()
                    .select("COUNT(*) AS cnt", "IFNULL(SUM(deal_amount),0) AS amt")
                    .eq("status", ServiceOrder.STATUS_DEAL)
                    .ge("deal_time", from)
                    .lt("deal_time", to);
            if (scope != null) {
                wrapper.in("owner_staff_code", scope);
            } else {
                wrapper.isNotNull("owner_staff_code");
            }
            List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
            Object cnt = rows.isEmpty() ? 0 : rows.get(0).get("cnt");
            Object amt = rows.isEmpty() ? 0 : rows.get(0).get("amt");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", monthStart.toString().substring(0, 7));
            row.put("count", cnt == null ? 0 : Long.parseLong(cnt.toString()));
            row.put("amount", amt == null ? BigDecimal.ZERO : new BigDecimal(amt.toString()));
            trend.add(row);
        }
        return trend;
    }

    /**
     * 近 N 个月奖励趋势（按月 COUNT/SUM 聚合，走 idx_created_at）。
     */
    public List<Map<String, Object>> rewardTrend(int months, LoanUser user) {
        Set<String> scope = buildOwnerScope(user);
        if (scope != null && scope.isEmpty()) {
            return emptyTrend(months);
        }
        // 奖励按归属工单范围过滤：先取归属 scope 的工单号集合
        Set<String> orderNos = null;
        if (scope != null) {
            orderNos = orderMapper.selectList(new LambdaQueryWrapper<ServiceOrder>()
                            .in(ServiceOrder::getOwnerStaffCode, scope)).stream()
                    .map(ServiceOrder::getOrderNo).collect(Collectors.toSet());
        }
        int n = Math.max(6, Math.min(months, 24));
        LocalDate start = LocalDate.now().minusMonths(n - 1L).withDayOfMonth(1);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate monthStart = start.plusMonths(i);
            LocalDateTime from = monthStart.atStartOfDay();
            LocalDateTime to = monthStart.plusMonths(1).atStartOfDay();
            QueryWrapper<RewardRecord> wrapper = new QueryWrapper<RewardRecord>()
                    .select("COUNT(*) AS cnt", "IFNULL(SUM(reward_amount),0) AS amt")
                    .ge("created_at", from)
                    .lt("created_at", to);
            if (orderNos != null) {
                if (orderNos.isEmpty()) {
                    wrapper.in("service_order_no", Collections.singletonList("__NONE__"));
                } else {
                    wrapper.in("service_order_no", orderNos);
                }
            } else {
                wrapper.exists("SELECT 1 FROM t_service_order o WHERE o.order_no = t_reward_record.service_order_no "
                        + "AND o.owner_staff_code IS NOT NULL");
            }
            List<Map<String, Object>> rows = rewardRecordMapper.selectMaps(wrapper);
            Object cnt = rows.isEmpty() ? 0 : rows.get(0).get("cnt");
            Object amt = rows.isEmpty() ? 0 : rows.get(0).get("amt");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", monthStart.toString().substring(0, 7));
            row.put("count", cnt == null ? 0 : Long.parseLong(cnt.toString()));
            row.put("amount", amt == null ? BigDecimal.ZERO : new BigDecimal(amt.toString()));
            trend.add(row);
        }
        return trend;
    }

    /** 成交金额聚合（避免全表扫描）。 */
    private BigDecimal orderSum(String col, String val, String sumCol) {
        QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<ServiceOrder>()
                .select("IFNULL(SUM(" + sumCol + "),0) AS amt").eq(col, val);
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
        if (rows.isEmpty() || rows.get(0).get("amt") == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rows.get(0).get("amt").toString());
    }

    /** 奖励金额聚合（避免全表扫描）。 */
    private BigDecimal rewardSum() {
        QueryWrapper<RewardRecord> wrapper = new QueryWrapper<RewardRecord>()
                .select("IFNULL(SUM(reward_amount),0) AS amt");
        List<Map<String, Object>> rows = rewardRecordMapper.selectMaps(wrapper);
        if (rows.isEmpty() || rows.get(0).get("amt") == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rows.get(0).get("amt").toString());
    }

    /**
     * 初筛报告分页（含客户名）。
     */
    public PageResult<Map<String, Object>> screeningPage(String status, String grade, String keyword,
                                                          int page, int size, String orderBy, String orderDir,
                                                          LoanUser user) {
        LambdaQueryWrapper<ClientScreening> wrapper = new LambdaQueryWrapper<>();
        // 角色数据可见范围：按归属客户过滤（老板/运营/超管全量；顾问本人；部门主管本部门）
        Set<String> scope = buildOwnerScope(user);
        if (scope != null && !scope.isEmpty()) {
            // 将归属过滤下沉为 EXISTS，避免 10 万客户场景先加载全部 clientCode 再拼超大 IN。
            wrapper.exists("SELECT 1 FROM t_client_profile cp WHERE cp.client_code = t_client_screening.client_profile_code "
                    + "AND cp.owner_staff_code IN ("
                    + scope.stream().map(x -> "'" + x.replace("'", "''") + "'").collect(Collectors.joining(",")) + "))");
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ClientScreening::getStatus, status);
        }
        if (StringUtils.hasText(grade)) {
            wrapper.eq(ClientScreening::getGrade, grade);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 报告编号模糊；客户姓名 / 企业名 / 手机号（SHA-256 哈希精确）命中则按客户编码追加匹配。
            // 不再按客户内部编码(客户ID)模糊，统一以「姓名/手机号/企业名」关键字查询。
            List<String> nameCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                            .like(ClientProfile::getContactName, kw)
                            .or().like(ClientProfile::getEnterpriseName, kw))
                    .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            List<String> phoneCodes = new ArrayList<>();
            if (kw.matches("\\d{11}")) {
                String hash = HashUtils.sha256Hex(kw);
                phoneCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                                .eq(ClientProfile::getPhoneHash, hash))
                        .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }
            java.util.Set<String> matchedCodes = new java.util.LinkedHashSet<>();
            matchedCodes.addAll(nameCodes);
            matchedCodes.addAll(phoneCodes);
            wrapper.and(w -> {
                w.like(ClientScreening::getReportNo, kw);
                if (!matchedCodes.isEmpty()) {
                    w.or().in(ClientScreening::getClientProfileCode, matchedCodes);
                }
            });
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, ClientScreening::getCreatedAt);
        Page<ClientScreening> result = screeningMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)), wrapper);

        List<String> clientCodes = result.getRecords().stream().map(ClientScreening::getClientProfileCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> nameMap = clientCodes.isEmpty() ? java.util.Collections.emptyMap()
                : clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                        .in(ClientProfile::getClientCode, clientCodes)).stream()
                        .collect(Collectors.toMap(ClientProfile::getClientCode,
                                c -> StringUtils.hasText(c.getEnterpriseName()) ? c.getEnterpriseName()
                                        : c.getContactName()));

        List<Map<String, Object>> records = result.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportNo", s.getReportNo());
            m.put("clientProfileCode", s.getClientProfileCode());
            m.put("clientName", nameMap.get(s.getClientProfileCode()));
            m.put("grade", s.getGrade());
            m.put("bankCount", s.getBankCount());
            m.put("productCount", s.getProductCount());
            m.put("passCount", s.getPassCount());
            m.put("conditionCount", s.getConditionCount());
            m.put("rejectCount", s.getRejectCount());
            m.put("vipFlag", s.getVipFlag());
            m.put("status", s.getStatus());
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 初筛报告详情。
     */
    public Map<String, Object> screeningDetail(String reportNo) {
        ClientScreening s = screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getReportNo, reportNo));
        if (s == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportNo", s.getReportNo());
        m.put("clientProfileCode", s.getClientProfileCode());
        m.put("matchTraceUuid", s.getMatchTraceUuid());
        m.put("templateCode", s.getTemplateCode());
        m.put("grade", s.getGrade());
        m.put("bankCount", s.getBankCount());
        m.put("productCount", s.getProductCount());
        m.put("passCount", s.getPassCount());
        m.put("conditionCount", s.getConditionCount());
        m.put("rejectCount", s.getRejectCount());
        m.put("adviceJson", s.getAdviceJson());
        m.put("vipFlag", s.getVipFlag());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    /** 汇总金额。 */
    private <T> BigDecimal sum(List<T> list, java.util.function.Function<T, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (T o : list) {
            BigDecimal v = getter.apply(o);
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }
}
