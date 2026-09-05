package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.util.PageOrder;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.infrastructure.security.HashUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.context.LoanUser;
import com.loan.lead.entity.Lead;
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
    private final ClientProfileMapper clientProfileMapper;
    private final ClientScreeningMapper screeningMapper;
    private final BankProductMapper bankProductMapper;
    private final StaffMapper staffMapper;

    /** 报表全量可见角色（运营/超管/老板/超级管理员）：跨全部数据范围。 */
    private static final Set<String> REPORT_FULL_ROLES =
            new HashSet<>(java.util.Arrays.asList("BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER"));

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
            return null;
        }
        String role = user.getRoleCode();
        if (!StringUtils.hasText(role)) {
            return null;
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
                scopedCountCreated(screeningMapper, ClientScreening::getCreatedAt, cur, clientCodes, ClientScreening::getClientProfileCode),
                scopedCountCreated(screeningMapper, ClientScreening::getCreatedAt, prev, clientCodes, ClientScreening::getClientProfileCode)));

        // 转化漏斗 + 分布维度（全局汇总，不按个人范围切割）
        m.put("funnel", funnel());
        m.put("customerGroupDist", distribution("customerGroup"));
        m.put("productDist", distribution("product"));
        m.put("orderStatusDist", distribution("orderStatus"));
        return m;
    }

    /** 客户数：按归属范围。 */
    private long scopedClientCount(Set<String> scope) {
        if (scope != null && scope.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<ClientProfile> w = new LambdaQueryWrapper<>();
        if (scope != null) {
            w.in(ClientProfile::getOwnerStaffCode, scope);
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
        }
        return firstSum(w, orderMapper);
    }

    /** 奖励数：按归属工单范围。 */
    private long scopedRewardCount(Set<String> scope, Set<String> orderNos) {
        if (scope == null) {
            return rewardRecordMapper.selectCount(null);
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
            return rewardSum();
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
            return screeningMapper.selectCount(null);
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
        }
        return firstSum(w, orderMapper);
    }

    /** 奖励金额（created_at 落在 [range)，按归属工单范围）。orderNos=null 表示全量。 */
    private BigDecimal scopedSumRewardAmount(LocalDateTime[] range, Set<String> orderNos) {
        if (orderNos == null) {
            return sumRewardAmount(range);
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
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stage("线索池", leadMapper.selectCount(null)));
        list.add(stage("客户(转正)", clientProfileMapper.selectCount(null)));
        list.add(stage("服务工单", orderMapper.selectCount(null)));
        list.add(stage("成交", orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getStatus, ServiceOrder.STATUS_DEAL))));
        list.add(stage("发放奖励", rewardRecordMapper.selectCount(null)));
        return list;
    }

    /**
     * 分布维度：客群 / 产品 / 工单状态。
     *
     * @param dim customerGroup | product | orderStatus
     */
    public List<Map<String, Object>> distribution(String dim) {
        switch (dim) {
            case "customerGroup": {
                List<Map<String, Object>> rows = clientProfileMapper.selectMaps(new QueryWrapper<ClientProfile>()
                        .select("COALESCE(customer_group, 'UNKNOWN') AS name", "COUNT(*) AS value")
                        .groupBy("customer_group"));
                return rows.stream().map(r -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("name", r.get("name"));
                    x.put("value", r.get("value"));
                    return x;
                }).collect(Collectors.toList());
            }
            case "product": {
                List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<ServiceOrder>()
                        .select("bank_product_code AS code", "COUNT(*) AS orderCount",
                                "SUM(CASE WHEN status = 'DEAL' THEN 1 ELSE 0 END) AS dealCount",
                                "IFNULL(SUM(CASE WHEN status = 'DEAL' THEN deal_amount ELSE 0 END), 0) AS dealAmount")
                        .isNotNull("bank_product_code").ne("bank_product_code", "")
                        .groupBy("bank_product_code").orderByDesc("dealAmount").last("LIMIT 8"));
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
                List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<ServiceOrder>()
                        .select("status AS name", "COUNT(*) AS value").groupBy("status"));
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
            Set<String> scopedClientCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                            .in(ClientProfile::getOwnerStaffCode, scope)).stream()
                    .map(ClientProfile::getClientCode).collect(Collectors.toSet());
            if (scopedClientCodes.isEmpty()) {
                wrapper.in(ClientScreening::getClientProfileCode, Collections.singletonList("__NONE__"));
            } else {
                wrapper.in(ClientScreening::getClientProfileCode, scopedClientCodes);
            }
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
        Page<ClientScreening> result = screeningMapper.selectPage(new Page<>(page, size), wrapper);

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
