package com.loan.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置（会话 / 验证码 / 缓存）。
 *
 * <p>统一使用 {@link StringRedisTemplate} + Jackson 手动 JSON 序列化，避免序列化器与实体耦合。
 *
 * @author loan-platform
 */
@Configuration
public class RedisConfig {

    /**
     * 全局共享的 Jackson 序列化器（供 Redis JSON 序列化复用）。
     *
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }

    /**
     * 字符串 Redis 模板。
     *
     * @param connectionFactory 连接工厂
     * @return StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // 云 Redis 会主动回收空闲 TCP 连接。连接池若直接借出已失效连接，命令会一直
        // 等到 spring.redis.timeout 才触发重连，最明显的表现就是验证码校验固定卡 10 秒。
        // 借用连接前先做轻量校验，避免把失效连接交给登录、会话和缓存链路。
        if (connectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuce = (LettuceConnectionFactory) connectionFactory;
            lettuce.setValidateConnection(true);
            // 远程开发 Redis 前存在会回收空闲连接的网络层。共享单连接在回收后无法在
            // 当前命令内恢复，验证码 GET 就会卡满 10 秒；每次操作借用独立连接可让
            // Lettuce 在命令发出前完成建连和认证，避免复用半失效的共享通道。
            lettuce.setShareNativeConnection(false);
        }
        return new StringRedisTemplate(connectionFactory);
    }
}
