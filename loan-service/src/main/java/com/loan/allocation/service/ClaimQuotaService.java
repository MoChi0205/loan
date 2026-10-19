package com.loan.allocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.allocation.entity.ClaimQuotaConfig;
import com.loan.allocation.mapper.ClaimQuotaConfigMapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公海认领配额（每日上限）——线索与客户共用。
 *
 * <p><b>口径来源</b>：参考 tse 的 {@code daily_assign_limit_per_user}
 * （企业级可配 · 每坐席每日公海认领上限 · 上限值不写死在代码里）。本项目为单租户，
 * 上限值读 {@code t_allocation_quota_config} 的 {@code LEAD} / {@code CLIENT} 两行。</p>
 *
 * <p><b>计数方式</b>：Redis 当日原子计数（key 含日期与工号，TTL 2 天）。
 * 沿用原客户侧 {@code loan:client:claim:daily:*} 键格式以保持行为一致；
 * 线索侧新增 {@code loan:lead:claim:daily:*}。</p>
 *
 * <p><b>降级策略</b>：配置表未执行 migration 或读取异常时，回退到
 * {@code loan.claim.daily-limit.*} 配置项（默认 30），<b>不阻断认领</b>——
 * 配额是防刷手段，不应因配置缺失导致业务不可用。</p>
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimQuotaService {

    /** 线索公海认领。 */
    public static final String SCOPE_LEAD = "LEAD";
    /** 客户公海认领。 */
    public static final String SCOPE_CLIENT = "CLIENT";

    /** 上限配置本地缓存时长（毫秒）：避免每次认领都查库，同时保证改配置后 1 分钟内生效。 */
    private static final long CACHE_TTL_MS = 60_000L;
    /** 计数键保留时长：跨天后自然失效，留 1 天余量便于排查。 */
    private static final Duration COUNTER_TTL = Duration.ofDays(2);

    private final ClaimQuotaConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;

    /** 配置表不可用时的兜底上限（线索）。 */
    @Value("${loan.claim.daily-limit.lead:30}")
    private int leadFallbackLimit;
    /** 配置表不可用时的兜底上限（客户）。 */
    @Value("${loan.claim.daily-limit.client:30}")
    private int clientFallbackLimit;
    /** 配置表不可用时的兜底持有上限（客户侧原 loan.client.claim.max-holding）。 */
    @Value("${loan.client.claim.max-holding:100}")
    private int clientHoldingFallback;

    /** scope → 配额配置缓存。 */
    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 读取某公海类型的每日认领上限（按人计数，口径对齐 tse daily_assign_limit_per_user）。
     *
     * @param scope {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @return 上限；0 或负数表示不限
     */
    public int limitOf(String scope) {
        return configOf(scope).dailyClaimLimit;
    }

    /**
     * 读取某公海类型的持有上限（按人名下归属对象计数）。
     *
     * @param scope {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @return 上限；0 或负数表示不限
     */
    public int holdingLimitOf(String scope) {
        return configOf(scope).maxHolding;
    }

    /** 数据保护期天数：进入公海后 N 天内所有人不可认领（0 = 不启用）。 */
    public int protectDaysOf(String scope) {
        return configOf(scope).protectDays;
    }

    /** 每日认领冷却条数：与每日认领上限取较小值生效（0 = 不启用）。 */
    public int cooldownLimitOf(String scope) {
        return configOf(scope).cooldownLimit;
    }

    /** 退回/回收回纳目标：PUBLIC（默认，公司公海）/ ORIGINAL（归属人原部门团队池）。 */
    public String returnTargetOf(String scope) {
        return configOf(scope).returnTarget;
    }

    // —— 资源池维度重载（2026-10-20 scope 扩展：资源池专属配置优先，无则回落公海类型配置） ——

    /** 读取并缓存某 scope key 的专属配置行；无行返回 null（不回落默认，供资源池优先逻辑用）。 */
    private CachedConfig configRowOf(String scopeKey) {
        long now = System.currentTimeMillis();
        CachedConfig cached = configCache.get(scopeKey);
        if (cached != null && cached.expiresAt > now) {
            return cached;
        }
        CachedConfig value = null;
        try {
            ClaimQuotaConfig row = configMapper.selectOne(new LambdaQueryWrapper<ClaimQuotaConfig>()
                    .eq(ClaimQuotaConfig::getScope, scopeKey).last("LIMIT 1"));
            if (row != null) {
                value = new CachedConfig(
                        row.getDailyClaimLimit() == null ? 0 : row.getDailyClaimLimit(),
                        row.getMaxHolding() == null ? 0 : row.getMaxHolding(),
                        row.getProtectDays() == null ? 0 : Math.max(0, row.getProtectDays()),
                        row.getDailyCooldownLimit() == null ? 0 : Math.max(0, row.getDailyCooldownLimit()),
                        StringUtils.hasText(row.getReturnTarget()) ? row.getReturnTarget().trim().toUpperCase() : "PUBLIC",
                        now + CACHE_TTL_MS);
                configCache.put(scopeKey, value);
            }
        } catch (Exception e) {
            log.warn("认领配额配置读取失败 scopeKey={}", scopeKey, e);
        }
        return value;
    }

    /** 解析生效配置：资源池专属配置（POOL:<poolCode>）优先，无则回落公海类型配置。 */
    private CachedConfig resolveConfig(String scope, String poolCode) {
        if (StringUtils.hasText(poolCode)) {
            CachedConfig poolCfg = configRowOf("POOL:" + poolCode);
            if (poolCfg != null) {
                return poolCfg;
            }
        }
        return configOf(scope);
    }

    /** 资源池维度每日认领上限（资源池专属优先）。 */
    public int limitOf(String scope, String poolCode) {
        return resolveConfig(scope, poolCode).dailyClaimLimit;
    }

    /** 资源池维度持有上限（资源池专属优先）。 */
    public int holdingLimitOf(String scope, String poolCode) {
        return resolveConfig(scope, poolCode).maxHolding;
    }

    /** 资源池维度数据保护期天数（资源池专属优先）。 */
    public int protectDaysOf(String scope, String poolCode) {
        return resolveConfig(scope, poolCode).protectDays;
    }

    /** 资源池维度每日认领冷却条数（资源池专属优先）。 */
    public int cooldownLimitOf(String scope, String poolCode) {
        return resolveConfig(scope, poolCode).cooldownLimit;
    }

    /** 资源池维度退回/回收回纳目标（资源池专属优先）。 */
    public String returnTargetOf(String scope, String poolCode) {
        return resolveConfig(scope, poolCode).returnTarget;
    }

    /** 读取并缓存某公海类型的配额配置；配置表缺失/读写异常时回退兜底值，不阻断认领。 */
    private CachedConfig configOf(String scope) {
        long now = System.currentTimeMillis();
        CachedConfig cached = configCache.get(scope);
        if (cached != null && cached.expiresAt > now) {
            return cached;
        }
        int dailyClaimLimit = fallbackDailyLimitOf(scope);
        int maxHolding = fallbackHoldingOf(scope);
        int protectDays = 0;
        int cooldownLimit = 0;
        String returnTarget = "PUBLIC";
        try {
            ClaimQuotaConfig row = configMapper.selectOne(new LambdaQueryWrapper<ClaimQuotaConfig>()
                    .eq(ClaimQuotaConfig::getScope, scope)
                    .last("LIMIT 1"));
            if (row != null) {
                if (row.getDailyClaimLimit() != null) {
                    dailyClaimLimit = row.getDailyClaimLimit();
                }
                if (row.getMaxHolding() != null) {
                    maxHolding = row.getMaxHolding();
                }
                if (row.getProtectDays() != null) {
                    protectDays = Math.max(0, row.getProtectDays());
                }
                if (row.getDailyCooldownLimit() != null) {
                    cooldownLimit = Math.max(0, row.getDailyCooldownLimit());
                }
                if (StringUtils.hasText(row.getReturnTarget())) {
                    returnTarget = row.getReturnTarget().trim().toUpperCase();
                }
            }
        } catch (Exception e) {
            log.warn("认领配额配置读取失败，降级为兜底值 scope={} daily={} holding={}",
                    scope, dailyClaimLimit, maxHolding, e);
        }
        CachedConfig value = new CachedConfig(dailyClaimLimit, maxHolding, protectDays, cooldownLimit, returnTarget, now + CACHE_TTL_MS);
        configCache.put(scope, value);
        return value;
    }

    /**
     * 预占当日认领额度；超限抛业务异常（调用方需在失败时 {@link #refund} 回滚）。
     *
     * @param scope     {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @param staffCode 认领人工号
     * @param count     本次预占条数（批量认领传批量数）
     */
    public void consume(String scope, String staffCode, int count) {
        consume(scope, null, staffCode, count);
    }

    /**
     * 预占当日认领额度（资源池维度）：poolCode 非空时按资源池专属上限，无则回落公海类型上限。
     * 计数 key 仍按员工 × 公海类型维度（不按资源池隔离），超限抛业务异常。
     *
     * @param scope     {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @param poolCode  目标资源池编码（可为 null，表示公海类型维度）
     * @param staffCode 认领人工号
     * @param count     本次预占条数（批量认领传批量数）
     */
    public void consume(String scope, String poolCode, String staffCode, int count) {
        if (count <= 0 || !StringUtils.hasText(staffCode)) {
            return;
        }
        int limit = limitOf(scope, poolCode);
        // 每日认领冷却条数：与每日认领上限取较小值生效（0 = 不启用，2026-10-19）
        int cooldownLimit = cooldownLimitOf(scope, poolCode);
        if (cooldownLimit > 0) {
            limit = limit <= 0 ? cooldownLimit : Math.min(limit, cooldownLimit);
        }
        if (limit <= 0) {
            return;
        }
        String key = counterKey(scope, staffCode);
        Long used = redisTemplate.opsForValue().increment(key, count);
        if (used != null && used == (long) count) {
            redisTemplate.expire(key, COUNTER_TTL);
        }
        if (used != null && used > limit) {
            redisTemplate.opsForValue().decrement(key, count);
            throw new BusinessException(ResultCode.PARAM_ERROR, exceededMessage(scope, limit));
        }
    }

    /**
     * 回滚预占额度（认领失败/事务回滚时调用），计数不低于 0。
     *
     * @param scope     {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @param staffCode 认领人工号
     * @param count     需回滚条数
     */
    public void refund(String scope, String staffCode, int count) {
        if (count <= 0 || !StringUtils.hasText(staffCode)) {
            return;
        }
        String key = counterKey(scope, staffCode);
        Long left = redisTemplate.opsForValue().decrement(key, count);
        if (left != null && left < 0) {
            redisTemplate.opsForValue().set(key, "0");
            redisTemplate.expire(key, COUNTER_TTL);
        }
    }

    /** 当日已用额度（用于排查与前端展示）。 */
    public long usedToday(String scope, String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return 0L;
        }
        String value = redisTemplate.opsForValue().get(counterKey(scope, staffCode));
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 清理本地缓存（配置被后台修改后可即时生效）。 */
    public void evictCache() {
        configCache.clear();
    }

    private String counterKey(String scope, String staffCode) {
        // 客户侧沿用历史键格式，避免升级当天计数清零
        String prefix = SCOPE_LEAD.equals(scope) ? "loan:lead:claim:daily:" : "loan:client:claim:daily:";
        return prefix + staffCode + ":" + LocalDate.now();
    }

    private int fallbackDailyLimitOf(String scope) {
        return SCOPE_LEAD.equals(scope) ? leadFallbackLimit : clientFallbackLimit;
    }

    /** 线索侧无持有上限概念，默认不限。 */
    private int fallbackHoldingOf(String scope) {
        return SCOPE_LEAD.equals(scope) ? 0 : clientHoldingFallback;
    }

    private String exceededMessage(String scope, int limit) {
        return SCOPE_LEAD.equals(scope)
                ? "已达到今日线索认领上限（" + limit + " 条），请明日再试"
                : "已达到今日客户认领上限（" + limit + " 条），请明日再试";
    }

    /** 配额配置缓存项（每日认领上限 + 持有上限 + 保护期/冷却/回纳目标）。 */
    private static final class CachedConfig {
        private final int dailyClaimLimit;
        private final int maxHolding;
        private final int protectDays;
        private final int cooldownLimit;
        private final String returnTarget;
        private final long expiresAt;

        private CachedConfig(int dailyClaimLimit, int maxHolding, int protectDays,
                             int cooldownLimit, String returnTarget, long expiresAt) {
            this.dailyClaimLimit = dailyClaimLimit;
            this.maxHolding = maxHolding;
            this.protectDays = protectDays;
            this.cooldownLimit = cooldownLimit;
            this.returnTarget = returnTarget;
            this.expiresAt = expiresAt;
        }
    }
}
