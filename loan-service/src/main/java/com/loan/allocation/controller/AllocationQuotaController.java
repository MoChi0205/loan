package com.loan.allocation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.allocation.entity.ClaimQuotaConfig;
import com.loan.allocation.mapper.ClaimQuotaConfigMapper;
import com.loan.allocation.service.ClaimQuotaService;
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
import java.util.Arrays;
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

    /** 支持的资源池范围。 */
    private static final List<String> SCOPES = Arrays.asList(
            ClaimQuotaService.SCOPE_LEAD, ClaimQuotaService.SCOPE_CLIENT);

    /** 范围中文名，用于前端展示。 */
    private static final Map<String, String> SCOPE_NAMES = new LinkedHashMap<>();

    static {
        SCOPE_NAMES.put(ClaimQuotaService.SCOPE_LEAD, "线索公海");
        SCOPE_NAMES.put(ClaimQuotaService.SCOPE_CLIENT, "客户公海");
    }

    private final ClaimQuotaConfigMapper configMapper;
    private final ClaimQuotaService claimQuotaService;

    /**
     * 查询认领设置（两个资源池各一行）。
     *
     * @return 配置列表（scope/scopeName/dailyClaimLimit/maxHolding/remark/updatedBy/updatedAt）
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Map<String, ClaimQuotaConfig> existing = new LinkedHashMap<>();
        for (ClaimQuotaConfig row : configMapper.selectList(null)) {
            existing.put(row.getScope(), row);
        }
        List<Map<String, Object>> rows = new ArrayList<>(SCOPES.size());
        for (String scope : SCOPES) {
            rows.add(toView(scope, existing.get(scope)));
        }
        return Result.ok(rows);
    }

    /**
     * 保存认领设置（按 scope 幂等 upsert）。
     *
     * <p>{@code dailyClaimLimit} / {@code maxHolding} 传 0 表示不限。保存后清本地缓存，
     * 配额规则<b>即时生效</b>，无需重启或发版。</p>
     *
     * @param body 配置项列表，形如 { items: [{ scope, dailyClaimLimit, maxHolding, remark }] }
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
            String scope = StringUtils.hasText((String) row.get("scope"))
                    ? ((String) row.get("scope")).trim().toUpperCase() : null;
            if (scope == null || !SCOPES.contains(scope)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的适用范围：" + row.get("scope"));
            }
            int dailyClaimLimit = readNonNegative(row.get("dailyClaimLimit"), "每日认领上限");
            int maxHolding = readNonNegative(row.get("maxHolding"), "持有上限");
            upsert(scope, dailyClaimLimit, maxHolding, (String) row.get("remark"), operatorName);
        }
        claimQuotaService.evictCache();
        return Result.ok("ok");
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
    private void upsert(String scope, int dailyClaimLimit, int maxHolding, String remark, String operator) {
        ClaimQuotaConfig existing = configMapper.selectOne(new LambdaQueryWrapper<ClaimQuotaConfig>()
                .eq(ClaimQuotaConfig::getScope, scope)
                .last("LIMIT 1"));
        if (existing == null) {
            ClaimQuotaConfig row = new ClaimQuotaConfig();
            row.setScope(scope);
            row.setDailyClaimLimit(dailyClaimLimit);
            row.setMaxHolding(maxHolding);
            row.setRemark(remark);
            row.setUpdatedBy(operator);
            configMapper.insert(row);
            return;
        }
        existing.setDailyClaimLimit(dailyClaimLimit);
        existing.setMaxHolding(maxHolding);
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
        view.put("scopeName", SCOPE_NAMES.get(scope));
        view.put("dailyClaimLimit", row != null && row.getDailyClaimLimit() != null
                ? row.getDailyClaimLimit() : claimQuotaService.limitOf(scope));
        view.put("maxHolding", row != null && row.getMaxHolding() != null
                ? row.getMaxHolding() : claimQuotaService.holdingLimitOf(scope));
        view.put("remark", row == null ? null : row.getRemark());
        view.put("updatedBy", row == null ? null : row.getUpdatedBy());
        view.put("updatedAt", row == null || row.getUpdatedAt() == null
                ? null : row.getUpdatedAt().toString().replace('T', ' '));
        return view;
    }
}
