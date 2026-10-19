package com.loan.resourcepool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loan.client.pool.ResourcePool;
import com.loan.client.pool.ResourcePoolMapper;
import com.loan.resourcepool.entity.ResourcePoolConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 资源池回收规则读取（按池一条规则，参考 tse t_resource_pool 内嵌回收字段）。
 *
 * <p>解析口径：客户/线索归属人的部门若绑定了团队池则用该池规则，否则回落公司公共池规则。
 * 单一配置源：不再回落遗留 {@code t_client_recycle_config} 全局配置（2026-10-19 移除）。</p>
 */
@Service
@RequiredArgsConstructor
public class ResourcePoolConfigService {
    private final ResourcePoolMapper poolMapper;

    /**
     * resolve 结果本地缓存（不经常变更的配置数据，短 TTL 防穿透）。
     * value 用 Optional 包裹以兼容「查不到返回 null」。
     */
    private final Cache<String, Optional<ResourcePoolConfig>> resolveCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /** 解析某员工/部门生效的资源池回收规则：部门绑定团队池优先，否则公司公共池。 */
    public ResourcePoolConfig resolve(String staffCode, String deptCode) {
        String key = (staffCode == null ? "" : staffCode) + "|" + (deptCode == null ? "" : deptCode);
        return resolveCache.get(key, k -> Optional.ofNullable(doResolve(staffCode, deptCode))).orElse(null);
    }

    private ResourcePoolConfig doResolve(String staffCode, String deptCode) {
        ResourcePool teamPool = null;
        if (StringUtils.hasText(deptCode)) {
            teamPool = poolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                    .eq(ResourcePool::getPoolType, "TEAM")
                    .eq(ResourcePool::getDeptCode, deptCode)
                    .eq(ResourcePool::getStatus, "ACTIVE").last("LIMIT 1"));
        }
        ResourcePool pool = teamPool;
        if (pool == null) {
            pool = poolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                    .eq(ResourcePool::getPoolCode, "POOL_COMPANY")
                    .eq(ResourcePool::getStatus, "ACTIVE").last("LIMIT 1"));
        }
        if (pool == null) {
            pool = poolMapper.selectOne(new LambdaQueryWrapper<ResourcePool>()
                    .eq(ResourcePool::getPoolType, "COMPANY")
                    .eq(ResourcePool::getStatus, "ACTIVE").last("LIMIT 1"));
        }
        // 单一配置源（2026-10-19）：遗留 t_client_recycle_config(GLOBAL) 兜底已移除，
        // 查不到任何池 = 无回收规则（不回收），避免双重配置源。
        return pool == null ? null : fromPool(pool);
    }

    /**
     * 每个资源池一行规则（poolCode 唯一；scopeType 由池类型映射：TEAM→TEAM / COMPANY→COMPANY）。
     * 供客户列表等只需要「池名/规则概览」的场景一次性批量读取。
     */
    public List<ResourcePoolConfig> list() {
        List<ResourcePool> pools = poolMapper.selectList(new LambdaQueryWrapper<ResourcePool>()
                .orderByAsc(ResourcePool::getId));
        List<ResourcePoolConfig> rows = new ArrayList<>(pools.size());
        for (ResourcePool pool : pools) {
            rows.add(fromPool(pool));
        }
        return rows;
    }

    /** 池实体 → 规则视图（兼容旧 ResourcePoolConfig 形状，scope 语义映射自池类型）。 */
    private ResourcePoolConfig fromPool(ResourcePool pool) {
        ResourcePoolConfig row = new ResourcePoolConfig();
        row.setPoolCode(pool.getPoolCode());
        row.setPoolName(pool.getPoolName());
        row.setScopeType("TEAM".equals(pool.getPoolType()) ? "TEAM" : "COMPANY");
        row.setScopeKey(pool.getDeptCode());
        row.setEnabled("ACTIVE".equals(pool.getStatus()) ? 1 : 0);
        row.setRecycleEnabled(pool.getRecycleEnabled() == null ? 1 : pool.getRecycleEnabled());
        row.setRecycleDays(pool.getRecycleDays() == null ? 30 : pool.getRecycleDays());
        row.setWarnDays(pool.getWarnDays() == null ? 15 : pool.getWarnDays());
        row.setCooldownDays(pool.getCooldownDays() == null ? 7 : pool.getCooldownDays());
        row.setStatusDaysJson(pool.getStatusDaysJson());
        return row;
    }

    /**
     * 解析按跟进状态配置的回收天数。
     *
     * <p>语义（2026-10-19 用户口径）：JSON 中显式配置的值（含 0）均生效——0 = 当天回收；
     * 未出现在 JSON 中 = 不限制（该状态不参与超期回收）。此前 ≤0 被丢弃导致 0 与未配置同义。</p>
     */
    public Map<String, Integer> parseStatusDays(ResourcePoolConfig config) {
        if (config == null || !StringUtils.hasText(config.getStatusDaysJson())) return Collections.emptyMap();
        try {
            Map<?, ?> raw = new com.fasterxml.jackson.databind.ObjectMapper().readValue(config.getStatusDaysJson(), Map.class);
            Map<String, Integer> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> { try { int days = Integer.parseInt(String.valueOf(v)); if (days >= 0) out.put(String.valueOf(k).toUpperCase(), days); } catch (Exception ignored) {} });
            return out;
        } catch (Exception e) { return Collections.emptyMap(); }
    }
}
