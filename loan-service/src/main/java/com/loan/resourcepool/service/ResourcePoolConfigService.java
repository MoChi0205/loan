package com.loan.resourcepool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loan.client.entity.ClientRecycleConfig;
import com.loan.client.mapper.ClientRecycleConfigMapper;
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
 * 兼容遗留 {@code t_client_recycle_config} 全局配置作为兜底，避免迁移未执行时行为回退。</p>
 */
@Service
@RequiredArgsConstructor
public class ResourcePoolConfigService {
    private final ResourcePoolMapper poolMapper;
    private final ClientRecycleConfigMapper legacyMapper;

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
        if (pool != null) return fromPool(pool);
        // 兼容已存在的全局配置，避免迁移未执行时改变行为。
        ClientRecycleConfig legacy = legacyMapper.selectOne(new LambdaQueryWrapper<ClientRecycleConfig>()
                .eq(ClientRecycleConfig::getConfigKey, "GLOBAL").last("LIMIT 1"));
        if (legacy == null) return null;
        ResourcePoolConfig fallback = new ResourcePoolConfig();
        fallback.setPoolCode("COMPANY"); fallback.setPoolName("公司公共资源池"); fallback.setScopeType("COMPANY");
        fallback.setEnabled(1); fallback.setRecycleEnabled(legacy.getRecycleEnabled()); fallback.setRecycleDays(legacy.getRecycleDays());
        fallback.setWarnDays(legacy.getWarnDays()); fallback.setCooldownDays(legacy.getCooldownDays());
        return fallback;
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

    public Map<String, Integer> parseStatusDays(ResourcePoolConfig config) {
        if (config == null || !StringUtils.hasText(config.getStatusDaysJson())) return Collections.emptyMap();
        try {
            Map<?, ?> raw = new com.fasterxml.jackson.databind.ObjectMapper().readValue(config.getStatusDaysJson(), Map.class);
            Map<String, Integer> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> { try { int days = Integer.parseInt(String.valueOf(v)); if (days > 0) out.put(String.valueOf(k).toUpperCase(), days); } catch (Exception ignored) {} });
            return out;
        } catch (Exception e) { return Collections.emptyMap(); }
    }
}
