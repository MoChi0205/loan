package com.loan.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 统一二级缓存命中、失效和 Redis 降级测试。 */
class UnifiedCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UnifiedCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new UnifiedCacheService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(cacheService, "redisPrefix", "test:");
        ReflectionTestUtils.setField(cacheService, "localTtlSeconds", 30L);
        ReflectionTestUtils.setField(cacheService, "redisTtlSeconds", 120L);
        cacheService.init();
    }

    @Test
    void shouldLoadOnceAndHitLocalCache() {
        AtomicInteger loads = new AtomicInteger();
        TypeReference<List<String>> type = new TypeReference<List<String>>() { };

        List<String> first = cacheService.getOrLoad("roles", type, () -> {
            loads.incrementAndGet();
            return Arrays.asList("BOSS", "ADVISER");
        });
        List<String> second = cacheService.getOrLoad("roles", type, () -> {
            loads.incrementAndGet();
            return Arrays.asList("OTHER");
        });

        assertEquals(Arrays.asList("BOSS", "ADVISER"), first);
        assertEquals(first, second);
        assertEquals(1, loads.get());
    }

    @Test
    void shouldFallBackWhenRedisUnavailable() {
        Mockito.when(valueOperations.get("test:menus:BOSS"))
                .thenThrow(new IllegalStateException("redis down"));
        TypeReference<List<String>> type = new TypeReference<List<String>>() { };

        List<String> value = cacheService.getOrLoad("menus:BOSS", type,
                () -> Arrays.asList("workbench"));

        assertEquals(Arrays.asList("workbench"), value);
    }

    @Test
    void evictShouldRemoveLocalAndRedisValue() {
        TypeReference<List<String>> type = new TypeReference<List<String>>() { };
        cacheService.getOrLoad("roles", type, () -> Arrays.asList("BOSS"));

        cacheService.evict("roles");
        AtomicInteger loads = new AtomicInteger();
        List<String> reloaded = cacheService.getOrLoad("roles", type, () -> {
            loads.incrementAndGet();
            return Arrays.asList("ADVISER");
        });

        Mockito.verify(redisTemplate).delete("test:roles");
        assertEquals(Arrays.asList("ADVISER"), reloaded);
        assertEquals(1, loads.get());
    }

    @Test
    void nullLoaderResultShouldNotBeCached() {
        TypeReference<String> type = new TypeReference<String>() { };
        assertNull(cacheService.getOrLoad("missing", type, () -> null));
        assertNull(cacheService.getOrLoad("missing", type, () -> null));
    }
}
