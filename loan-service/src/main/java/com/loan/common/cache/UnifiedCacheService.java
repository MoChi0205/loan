package com.loan.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 轻量二级缓存门面：Caffeine 一级 + Redis 二级。
 *
 * <p>仅用于菜单、角色、部门等低频变更的只读数据。Redis 不可用时自动降级为本地缓存，
 * 不得让缓存故障影响业务查询。调用方必须在写成功后显式 {@link #evict(String)}。
 * 当前不缓存空值，避免强一致资源的新建记录在 TTL 内不可见。</p>
 */
@Service
public class UnifiedCacheService {

    private Cache<String, String> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    @Value("${loan.cache.redis-prefix:loan:cache:v1:}")
    private String redisPrefix;

    @Value("${loan.cache.local-ttl-seconds:30}")
    private long localTtlSeconds;

    @Value("${loan.cache.redis-ttl-seconds:120}")
    private long redisTtlSeconds;

    /**
     * 构造缓存门面。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     */
    public UnifiedCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 根据配置初始化本地缓存容量与 TTL。 */
    @PostConstruct
    public void init() {
        this.localCache = Caffeine.newBuilder().maximumSize(500)
                .expireAfterWrite(Duration.ofSeconds(Math.max(1L, localTtlSeconds))).build();
    }

    /**
     * 读取缓存，未命中时 single-flight 回源并写入两级缓存。
     *
     * @param key    业务缓存键（不含命名空间）
     * @param type   JSON 类型
     * @param loader 回源查询
     * @param <T>    结果类型
     * @return 缓存或回源结果
     */
    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader) {
        String local = localCache.getIfPresent(key);
        T value = read(local, type);
        if (value != null) {
            return value;
        }
        Object lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                return loadUnderLock(key, type, loader);
            } finally {
                loadLocks.remove(key, lock);
            }
        }
    }

    /** 同一 key 内串行回源，不同 key 可并发。 */
    private <T> T loadUnderLock(String key, TypeReference<T> type, Supplier<T> loader) {
        String local = localCache.getIfPresent(key);
        T value = read(local, type);
        if (value != null) {
            return value;
        }
        String redis = null;
        try {
            redis = redisTemplate.opsForValue().get(redisPrefix + key);
        } catch (RuntimeException ignored) {
            // Redis 故障自动降级，不影响只读查询。
        }
        value = read(redis, type);
        if (value != null) {
            localCache.put(key, redis);
            return value;
        }
        value = loader.get();
        if (value == null) {
            return null;
        }
        String json = write(value);
        if (json != null) {
            localCache.put(key, json);
            try {
                redisTemplate.opsForValue().set(redisPrefix + key, json,
                        Duration.ofSeconds(redisTtlWithJitter()));
            } catch (RuntimeException ignored) {
                // Redis 故障自动降级为本地缓存。
            }
        }
        return value;
    }

    /** 写入缓存（用于预热或测试）。 */
    public void put(String key, Object value) {
        String json = write(value);
        if (json == null) {
            return;
        }
        localCache.put(key, json);
        try {
            redisTemplate.opsForValue().set(redisPrefix + key, json,
                    Duration.ofSeconds(redisTtlWithJitter()));
        } catch (RuntimeException ignored) {
            // Redis 故障不影响本地缓存。
        }
    }

    /** 同时失效本地与 Redis 缓存。 */
    public void evict(String key) {
        localCache.invalidate(key);
        try {
            redisTemplate.delete(redisPrefix + key);
        } catch (RuntimeException ignored) {
            // Redis 不可用时本地失效仍然生效。
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    /** Redis TTL 增加 0～15% 随机抖动，降低批量雪崩概率。 */
    private long redisTtlWithJitter() {
        long base = Math.max(1L, redisTtlSeconds);
        long bound = Math.max(1L, base * 15L / 100L);
        return base + ThreadLocalRandom.current().nextLong(bound + 1L);
    }
}
