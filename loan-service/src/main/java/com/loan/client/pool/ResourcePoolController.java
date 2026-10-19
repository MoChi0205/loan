package com.loan.client.pool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.CurrentUser;
import com.loan.exception.BusinessException;
import com.loan.common.util.BizIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 资源池管理：创建 / 列表 / 启停 / 按池维护回收规则。
 *
 * <p>对齐 tse 资源池模型：回收规则内嵌在每个资源池上（auto_recycle_enabled / days / status_days_json），
 * 一个池一条规则，不再存在「一条全局配置控制所有池」；创建与规则维护仅
 * 部门经理 / 老板 / 超级管理员可操作（tse 中为 ADMIN / MANAGER）。</p>
 *
 * <p>导入任务通过 poolCode 与资源池绑定。</p>
 */
@RestController
@RequestMapping("/api/admin/resource-pool")
@RequiredArgsConstructor
public class ResourcePoolController {

    /** 可创建/维护资源池的角色：经理、老板、超级管理员（2026-10-19 用户口径）。 */
    private static final Set<String> POOL_MANAGER_ROLES = new HashSet<>(Arrays.asList(
            "DEPT_MANAGER", "BOSS", "SUPER_ADMIN", "SUPER"));

    private final ResourcePoolMapper resourcePoolMapper;

    /** 资源池分页（含停用，便于管理）；下拉选择用 list 全量接口。 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @CurrentUser LoanUser user) {
        requirePoolManager(user);
        Page<ResourcePool> result = resourcePoolMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ResourcePool>().orderByAsc(ResourcePool::getId));
        return Result.ok(PageResult.build(page, size, result.getTotal(), result.getRecords().stream()
                .map(ResourcePoolController::toView).collect(java.util.stream.Collectors.toList())));
    }

    /** 全量启用池（导入目标池 / 线索公海、客户列表筛选下拉）：全体员工可读（AdminAuthInterceptor 已强校验 STAFF+管理角色）。 */
    @GetMapping("/list")
    public Result<java.util.List<Map<String, Object>>> list() {
        return Result.ok(resourcePoolMapper.selectList(new LambdaQueryWrapper<ResourcePool>()
                        .eq(ResourcePool::getStatus, "ACTIVE").orderByAsc(ResourcePool::getId))
                .stream().map(ResourcePoolController::toView).collect(java.util.stream.Collectors.toList()));
    }

    /** 创建资源池（含初始回收规则；仅经理/老板/超级管理员）。 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        requirePoolManager(user);
        String name = body == null ? null : String.valueOf(body.getOrDefault("poolName", "")).trim();
        if (name == null || name.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源池名称必填");
        }
        String type = String.valueOf(body.getOrDefault("poolType", "COMPANY"));
        if (!"COMPANY".equals(type) && !"TEAM".equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资源池类型不合法");
        }
        String deptCode = body.get("deptCode") == null ? null : String.valueOf(body.get("deptCode"));
        if ("TEAM".equals(type) && !StringUtils.hasText(deptCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "团队池必须关联部门");
        }
        ResourcePool pool = new ResourcePool();
        pool.setPoolCode(BizIdGenerator.generate("pool"));
        pool.setPoolName(name);
        pool.setPoolType(type);
        pool.setDeptCode("TEAM".equals(type) ? deptCode : null);
        pool.setDeptName("TEAM".equals(type) ? stringOf(body.get("deptName")) : null);
        pool.setDescription(stringOf(body.get("description")));
        pool.setStatus("ACTIVE");
        applyRecycleFields(pool, body);
        pool.setCreatedBy(user.getUserNo());
        pool.setUpdatedBy(user.getUserNo());
        pool.setCreatedAt(LocalDateTime.now());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.insert(pool);
        return Result.ok(toView(pool));
    }

    /** 启用 / 停用（停用后不再出现在导入目标池下拉，历史绑定不受影响）。 */
    @PostMapping("/{poolCode}/toggle")
    public Result<Void> toggle(@PathVariable String poolCode, @CurrentUser LoanUser user) {
        requirePoolManager(user);
        ResourcePool pool = requirePool(poolCode);
        pool.setStatus("ACTIVE".equals(pool.getStatus()) ? "INACTIVE" : "ACTIVE");
        pool.setUpdatedBy(user.getUserNo());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.updateById(pool);
        return Result.ok();
    }

    /**
     * 维护单个资源池的回收规则（按池一条规则，参考 tse）。
     *
     * <p>body: { recycleEnabled, recycleDays, warnDays, cooldownDays, statusDaysJson }；
     * statusDaysJson 为可选的按状态天数 JSON（键限 NEW/NO_ANSWER/POTENTIAL/INTENTION/VISITED/APPOINTED），
     * 成交 / 已签约 / 无需求不允许配置自动回收。</p>
     */
    @PutMapping("/{poolCode}/recycle")
    public Result<Map<String, Object>> updateRecycle(@PathVariable String poolCode,
                                                     @RequestBody Map<String, Object> body,
                                                     @CurrentUser LoanUser user) {
        requirePoolManager(user);
        ResourcePool pool = requirePool(poolCode);
        applyRecycleFields(pool, body);
        pool.setUpdatedBy(user.getUserNo());
        pool.setUpdatedAt(LocalDateTime.now());
        resourcePoolMapper.updateById(pool);
        return Result.ok(toView(pool));
    }

    /* ==================== 校验与转换 ==================== */

    private ResourcePool requirePool(String poolCode) {
        ResourcePool pool = resourcePoolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                .eq(ResourcePool::getPoolCode, poolCode));
        if (pool == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "资源池不存在");
        }
        return pool;
    }

    /** 仅部门经理 / 老板 / 超级管理员可创建或维护资源池（2026-10-19 口径，对齐 tse ADMIN/MANAGER）。 */
    private void requirePoolManager(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType()) || user.getRoleCode() == null
                || !POOL_MANAGER_ROLES.contains(user.getRoleCode().trim().toUpperCase())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅部门经理、老板或超级管理员可维护资源池");
        }
    }

    /** 读取并校验回收规则字段（规则内嵌在池上；缺省值对齐 tse：开、30 天、预警 15、冷却 7）。 */
    private void applyRecycleFields(ResourcePool pool, Map<String, Object> body) {
        int recycleEnabled = intOf(body.get("recycleEnabled"), 1);
        int recycleDays = intOf(body.get("recycleDays"), 30);
        int warnDays = intOf(body.get("warnDays"), 15);
        int cooldownDays = intOf(body.get("cooldownDays"), 7);
        if (recycleEnabled != 0 && recycleEnabled != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "自动回收开关不合法");
        }
        if (recycleDays <= 0 || warnDays < 0 || cooldownDays < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收天数必须大于 0，预警与冷却天数不能为负数");
        }
        pool.setRecycleEnabled(recycleEnabled);
        pool.setRecycleDays(recycleDays);
        pool.setWarnDays(warnDays);
        pool.setCooldownDays(cooldownDays);
        String statusDaysJson = stringOf(body.get("statusDaysJson"));
        if (StringUtils.hasText(statusDaysJson)) {
            validateStatusDays(statusDaysJson);
        }
        pool.setStatusDaysJson(StringUtils.hasText(statusDaysJson) ? statusDaysJson : null);
    }

    /** 按状态天数 JSON 校验：合法 JSON、只允许已知跟进状态、终态（成交/签约/无需求）禁止回收。 */
    private void validateStatusDays(String json) {
        Map<?, ?> raw;
        try {
            raw = new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "按状态天数必须是合法 JSON");
        }
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey()).trim().toUpperCase();
            if ("DEAL".equals(key) || "SIGNED".equals(key) || "NO_NEED".equals(key)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "成交、已签约、无需求客户不可配置自动回收");
            }
            try {
                if (Integer.parseInt(String.valueOf(entry.getValue())) <= 0) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "状态回收天数必须大于 0");
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "状态回收天数必须是整数");
            }
        }
    }

    private static String stringOf(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static int intOf(Object raw, int fallback) {
        if (raw == null || "".equals(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收天数必须是整数");
        }
    }

    private static Map<String, Object> toView(ResourcePool pool) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("poolCode", pool.getPoolCode());
        view.put("poolName", pool.getPoolName());
        view.put("poolType", pool.getPoolType());
        view.put("deptCode", pool.getDeptCode());
        view.put("deptName", pool.getDeptName());
        view.put("description", pool.getDescription());
        view.put("status", pool.getStatus());
        view.put("recycleEnabled", pool.getRecycleEnabled() == null ? 1 : pool.getRecycleEnabled());
        view.put("recycleDays", pool.getRecycleDays() == null ? 30 : pool.getRecycleDays());
        view.put("warnDays", pool.getWarnDays() == null ? 15 : pool.getWarnDays());
        view.put("cooldownDays", pool.getCooldownDays() == null ? 7 : pool.getCooldownDays());
        view.put("statusDaysJson", pool.getStatusDaysJson());
        view.put("createdBy", pool.getCreatedBy());
        view.put("createdAt", pool.getCreatedAt());
        return view;
    }
}
