package com.loan.allocation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.allocation.entity.ClaimQuotaConfig;
import com.loan.allocation.mapper.ClaimQuotaConfigMapper;
import com.loan.allocation.service.ClaimQuotaService;
import com.loan.client.pool.ResourcePool;
import com.loan.client.pool.ResourcePoolMapper;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.log.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认领设置（Web 管理端）：配置各资源池的每日认领上限与持有上限。
 *
 * <p>参考 tse「分配设置」页（{@code /api/enterprise/allocation-config}）：上限值集中在配置表，
 * 后台可改、改完即时生效，不写死在代码里。本项目为单租户，按 {@code scope}
 * （{@code LEAD} 线索 / {@code CLIENT} 客户）分行配置。</p>
 *
 * <p>接口 key 由启动期 {@code ApiPermissionSyncService} 自动登记为
 * {@code allocation-quota:list} / {@code allocation-quota:save}，
 * 全量角色（老板 / 运营 / 超管）默认放行，顾问与部门经理不授权。</p>
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/allocation-quota")
@RequiredArgsConstructor
public class AllocationQuotaController {

    /** scope 前缀：资源池维度（POOL:<poolCode>），与公海类型（LEAD/CLIENT）并存。 */
    private static final String POOL_SCOPE_PREFIX = "POOL:";

    /** 范围中文名（公海类型固定映射；资源池维度动态取资源池名）。 */
    private static final Map<String, String> SCOPE_NAMES = new LinkedHashMap<>();

    static {
        SCOPE_NAMES.put(ClaimQuotaService.SCOPE_LEAD, "线索公海");
        SCOPE_NAMES.put(ClaimQuotaService.SCOPE_CLIENT, "客户公海");
    }

    private final ClaimQuotaConfigMapper configMapper;
    private final ClaimQuotaService claimQuotaService;
    private final ResourcePoolMapper resourcePoolMapper;

    /**
     * 查询认领设置（全部已配置行；未配置公海类型时补 LEAD/CLIENT 默认两行）。
     *
     * @return 配置列表（scope/scopeName/dailyClaimLimit/maxHolding/protectDays/dailyCooldownLimit/returnTarget/remark/updatedBy/updatedAt）
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Map<String, ClaimQuotaConfig> existing = new LinkedHashMap<>();
        for (ClaimQuotaConfig row : configMapper.selectList(null)) {
            existing.put(row.getScope(), row);
        }
        // 公海类型两行常驻（即使尚未配置也显示默认行，便于直接编辑）
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(toView(ClaimQuotaService.SCOPE_LEAD, existing.get(ClaimQuotaService.SCOPE_LEAD)));
        rows.add(toView(ClaimQuotaService.SCOPE_CLIENT, existing.get(ClaimQuotaService.SCOPE_CLIENT)));
        // 追加资源池维度专属配置行
        for (Map.Entry<String, ClaimQuotaConfig> entry : existing.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(POOL_SCOPE_PREFIX)) {
                rows.add(toView(entry.getKey(), entry.getValue()));
            }
        }
        return Result.ok(rows);
    }

    /**
     * 保存认领设置（按 scope 幂等 upsert；支持新增资源池维度行与删除行）。
     *
     * <p>{@code dailyClaimLimit} / {@code maxHolding} 传 0 表示不限。保存后清本地缓存，
     * 配额规则<b>即时生效</b>，无需重启或发版。</p>
     *
     * @param body { items: [{ scope, dailyClaimLimit, maxHolding, protectDays, dailyCooldownLimit, returnTarget, remark }],
     *               deleteScopes: [scope...] 要删除的行（LEAD/CLIENT 不可删除，仅可删资源池维度行） }
     * @param user 当前用户（记录更新人）
     * @return ok
     */
    @PutMapping("/save")
    @OpLog(bizType = "认领设置", action = "UPDATE")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> save(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        Object itemsRaw = body == null ? null : body.get("items");
        if (!(itemsRaw instanceof List)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请提交 items 列表");
        }
        String operatorName = user == null || !StringUtils.hasText(user.getName())
                ? "system" : user.getName();
        for (Object item : (List<?>) itemsRaw) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) item;
            String scope = normalizeScope(row.get("scope"));
            validateScope(scope);
            int dailyClaimLimit = readNonNegative(row.get("dailyClaimLimit"), "每日认领上限");
            int maxHolding = readNonNegative(row.get("maxHolding"), "持有上限");
            int protectDays = readNonNegative(row.get("protectDays"), "数据保护期天数");
            int dailyCooldownLimit = readNonNegative(row.get("dailyCooldownLimit"), "每日认领冷却条数");
            String returnTarget = row.get("returnTarget") == null || "".equals(row.get("returnTarget"))
                    ? "PUBLIC" : String.valueOf(row.get("returnTarget")).trim().toUpperCase();
            if (!"PUBLIC".equals(returnTarget) && !"ORIGINAL".equals(returnTarget)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "回纳目标仅支持 PUBLIC / ORIGINAL");
            }
            upsert(scope, dailyClaimLimit, maxHolding, protectDays, dailyCooldownLimit, returnTarget,
                    (String) row.get("remark"), operatorName);
        }
        // 删除行（仅资源池维度，LEAD/CLIENT 常驻不可删）
        Object deleteRaw = body == null ? null : body.get("deleteScopes");
        if (deleteRaw instanceof List) {
            for (Object del : (List<?>) deleteRaw) {
                String scope = normalizeScope(del);
                if (scope == null || !scope.startsWith(POOL_SCOPE_PREFIX)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "仅资源池维度配置可删除");
                }
                configMapper.delete(new LambdaQueryWrapper<ClaimQuotaConfig>()
                        .eq(ClaimQuotaConfig::getScope, scope));
            }
        }
        claimQuotaService.evictCache();
        return Result.ok("ok");
    }

    /** 归一化 scope：去除首尾空白并大写；保留 POOL: 前缀。 */
    private String normalizeScope(Object raw) {
        if (raw == null || "".equals(String.valueOf(raw).trim())) {
            return null;
        }
        return String.valueOf(raw).trim().toUpperCase();
    }

    /** 校验 scope 合法：LEAD / CLIENT / POOL:<存在的资源池编码>。 */
    private void validateScope(String scope) {
        if (scope == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "适用范围 scope 不能为空");
        }
        if (ClaimQuotaService.SCOPE_LEAD.equals(scope) || ClaimQuotaService.SCOPE_CLIENT.equals(scope)) {
            return;
        }
        if (scope.startsWith(POOL_SCOPE_PREFIX)) {
            String poolCode = scope.substring(POOL_SCOPE_PREFIX.length());
            if (!StringUtils.hasText(poolCode)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "资源池编码不能为空");
            }
            ResourcePool pool = resourcePoolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                    .eq(ResourcePool::getPoolCode, poolCode).last("LIMIT 1"));
            if (pool == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "资源池不存在：" + poolCode);
            }
            return;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的适用范围：" + scope);
    }

    /** 解析非负整数（null / 空串按 0 处理；非数字或负数直接拒绝）。 */
    private int readNonNegative(Object raw, String label) {
        if (raw == null || "".equals(raw)) {
            return 0;
        }
        int value;
        try {
            value = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "必须是整数");
        }
        if (value < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "不能为负数（0 表示不限）");
        }
        return value;
    }

    /** 按 scope upsert 配置行。 */
    private void upsert(String scope, int dailyClaimLimit, int maxHolding, int protectDays,
                        int dailyCooldownLimit, String returnTarget, String remark, String operator) {
        ClaimQuotaConfig existing = configMapper.selectOne(new LambdaQueryWrapper<ClaimQuotaConfig>()
                .eq(ClaimQuotaConfig::getScope, scope)
                .last("LIMIT 1"));
        if (existing == null) {
            ClaimQuotaConfig row = new ClaimQuotaConfig();
            row.setScope(scope);
            row.setDailyClaimLimit(dailyClaimLimit);
            row.setMaxHolding(maxHolding);
            row.setProtectDays(protectDays);
            row.setDailyCooldownLimit(dailyCooldownLimit);
            row.setReturnTarget(returnTarget);
            row.setRemark(remark);
            row.setUpdatedBy(operator);
            configMapper.insert(row);
            return;
        }
        existing.setDailyClaimLimit(dailyClaimLimit);
        existing.setMaxHolding(maxHolding);
        existing.setProtectDays(protectDays);
        existing.setDailyCooldownLimit(dailyCooldownLimit);
        existing.setReturnTarget(returnTarget);
        if (remark != null) {
            existing.setRemark(remark);
        }
        existing.setUpdatedBy(operator);
        configMapper.updateById(existing);
    }

    /** 组装出参（配置行缺失时回落到当前生效的兜底值，便于前端直接展示）。 */
    private Map<String, Object> toView(String scope, ClaimQuotaConfig row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("scope", scope);
        view.put("scopeName", scopeNameOf(scope));
        view.put("dailyClaimLimit", row != null && row.getDailyClaimLimit() != null
                ? row.getDailyClaimLimit() : claimQuotaService.limitOf(scope));
        view.put("maxHolding", row != null && row.getMaxHolding() != null
                ? row.getMaxHolding() : claimQuotaService.holdingLimitOf(scope));
        view.put("protectDays", row != null && row.getProtectDays() != null
                ? row.getProtectDays() : claimQuotaService.protectDaysOf(scope));
        view.put("dailyCooldownLimit", row != null && row.getDailyCooldownLimit() != null
                ? row.getDailyCooldownLimit() : claimQuotaService.cooldownLimitOf(scope));
        view.put("returnTarget", row != null && StringUtils.hasText(row.getReturnTarget())
                ? row.getReturnTarget() : claimQuotaService.returnTargetOf(scope));
        view.put("remark", row == null ? null : row.getRemark());
        view.put("updatedBy", row == null ? null : row.getUpdatedBy());
        view.put("updatedAt", row == null || row.getUpdatedAt() == null
                ? null : row.getUpdatedAt().toString().replace('T', ' '));
        return view;
    }

    /** scope → 展示名：公海类型固定映射；资源池维度动态取资源池名。 */
    private String scopeNameOf(String scope) {
        if (SCOPE_NAMES.containsKey(scope)) {
            return SCOPE_NAMES.get(scope);
        }
        if (scope != null && scope.startsWith(POOL_SCOPE_PREFIX)) {
            String poolCode = scope.substring(POOL_SCOPE_PREFIX.length());
            ResourcePool pool = resourcePoolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                    .eq(ResourcePool::getPoolCode, poolCode).last("LIMIT 1"));
            return pool != null && StringUtils.hasText(pool.getPoolName())
                    ? pool.getPoolName() : "资源池(" + poolCode + ")";
        }
        return scope;
    }
}
