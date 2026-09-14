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
     * 读取某资源池的每日认领上限。
     *
     * @param scope {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @return 上限；0 或负数表示不限
     */
    public int limitOf(String scope) {
        return configOf(scope).dailyClaimLimit;
    }

    /**
     * 读取某资源池的持有上限。
     *
     * @param scope {@link #SCOPE_LEAD} 或 {@link #SCOPE_CLIENT}
     * @return 上限；0 或负数表示不限
     */
    public int holdingLimitOf(String scope) {
        return configOf(scope).maxHolding;
    }

    /** 读取并缓存某资源池的配额配置；配置表缺失/读写异常时回退兜底值，不阻断认领。 */
    private CachedConfig configOf(String scope) {
        long now = System.currentTimeMillis();
        CachedConfig cached = configCache.get(scope);
        if (cached != null && cached.expiresAt > now) {
            return cached;
        }
        int dailyClaimLimit = fallbackDailyLimitOf(scope);
        int maxHolding = fallbackHoldingOf(scope);
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
            }
        } catch (Exception e) {
            log.warn("认领配额配置读取失败，降级为兜底值 scope={} daily={} holding={}",
                    scope, dailyClaimLimit, maxHolding, e);
        }
        CachedConfig value = new CachedConfig(dailyClaimLimit, maxHolding, now + CACHE_TTL_MS);
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
        if (count <= 0 || !StringUtils.hasText(staffCode)) {
            return;
        }
        int limit = limitOf(scope);
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

    /** 配额配置缓存项（每日认领上限 + 持有上限）。 */
    private static final class CachedConfig {
        private final int dailyClaimLimit;
        private final int maxHolding;
        private final long expiresAt;

        private CachedConfig(int dailyClaimLimit, int maxHolding, long expiresAt) {
            this.dailyClaimLimit = dailyClaimLimit;
            this.maxHolding = maxHolding;
            this.expiresAt = expiresAt;
        }
    }
}
