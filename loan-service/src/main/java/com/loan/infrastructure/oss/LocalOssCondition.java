package com.loan.infrastructure.oss;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 本地 OSS 显式装配条件。
 *
 * <p>只有离线测试配置明确设置 {@code loan.oss.mode=local} 时才启用。
 * 缺失或非法模式不再静默回退本地磁盘，应用会因缺少存储实现而立即失败。
 */
public class LocalOssCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("loan.oss.mode");
        // 仅 Nacos 明确选择 local 时装配；缺失/非法值禁止落本地磁盘。
        return "local".equalsIgnoreCase(mode == null ? null : mode.trim());
    }
}
