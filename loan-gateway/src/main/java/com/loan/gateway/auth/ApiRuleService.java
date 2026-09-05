package com.loan.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 鉴权规则加载器：从 Redis 读取 loan-service 下发的接口权限规则。
 *
 * <p>策略（版本号 + 全量缓存）：
 * <ol>
 *   <li>每次请求先 GET 短 key {@code loan:api-perm:version}，与本地缓存版本比对；</li>
 *   <li>版本变化或缓存为空 → GET 全量规则 {@code loan:api-perm:rules} 并更新缓存；</li>
 *   <li>Redis 无规则 → 兜底调用 loan-service 内部接口 {@code /internal/api-perm/rules} 拉取。</li>
 * </ol>
 * 授权变更由 loan-service 写后递增版本号，网关几乎实时生效。
 *
 * @author loan-platform
 */
@Slf4j
@Component
public class ApiRuleService {

    /** 规则版本 key（短值，每请求比对） */
    private static final String VERSION_KEY = "loan:api-perm:version";

    /** 全量规则 key */
    private static final String RULE_KEY = "loan:api-perm:rules";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    /** 内部接口令牌（与服务端配置一致） */
    @Value("${internal.api.token:loan-internal-token}")
    private String internalToken;

    /** 服务端地址（兜底拉取规则用） */
    @Value("${loan.service.base-url:http://127.0.0.1:8080/loan}")
    private String serviceBaseUrl;

    /** 本地缓存版本号 */
    private volatile String cachedVersion = "";

    /** 本地缓存规则 */
    private volatile Map<String, Object> cachedRules;

    /**
     * 构造：初始化 WebClient。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  Jackson
     */
    public ApiRuleService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 加载鉴权规则（带本地缓存与版本比对）。
     *
     * @return 规则 Map；不可用时返回 null
     */
    public Mono<Map<String, Object>> loadRules() {
        return redisTemplate.opsForValue().get(VERSION_KEY)
                .defaultIfEmpty("")
                .flatMap(version -> {
                    if (cachedRules != null && version.equals(cachedVersion)) {
                        return Mono.just(cachedRules);
                    }
                    return fetchFullRules().map(rules -> {
                        cachedRules = rules;
                        cachedVersion = version;
                        return rules;
                    });
                })
                .onErrorResume(e -> {
                    log.warn("[Gateway] 读取鉴权规则失败，使用缓存或放行决策：{}", e.getMessage());
                    return cachedRules != null ? Mono.just(cachedRules) : Mono.empty();
                });
    }

    /**
     * 拉取全量规则：Redis → 内部接口兜底。
     *
     * @return 规则 Map
     */
    private Mono<Map<String, Object>> fetchFullRules() {
        return redisTemplate.opsForValue().get(RULE_KEY)
                .flatMap(json -> {
                    try {
                        Map<String, Object> rules = objectMapper.readValue(json,
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                });
                        return Mono.just(rules);
                    } catch (Exception e) {
                        log.warn("[Gateway] 规则 JSON 解析失败，走兜底: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(fetchFromService());
    }

    /**
     * 从 loan-service 内部接口兜底拉取规则。
     *
     * @return 规则 Map
     */
    private Mono<Map<String, Object>> fetchFromService() {
        return webClient.get()
                .uri(serviceBaseUrl + "/internal/api-perm/rules?token=" + internalToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(body -> {
                    try {
                        Map<String, Object> resp = objectMapper.readValue(body,
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                });
                        Object data = resp.get("data");
                        if (data instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rules = (Map<String, Object>) data;
                            return Mono.just(rules);
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("[Gateway] 兜底规则响应解析失败: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("[Gateway] 兜底拉取规则失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 版本号 Key（供测试）。
     *
     * @return key 名
     */
    public static String versionKey() {
        return VERSION_KEY;
    }
}
