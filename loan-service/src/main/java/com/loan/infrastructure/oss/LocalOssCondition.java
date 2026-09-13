package com.loan.infrastructure.oss;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 本地 OSS 兜底装配条件。
 *
 * <p>Nacos 中 {@code loan.oss.mode} 可能缺失、为空，或取值不在 {local, aliyun, tencent}。
 * 若本地实现也依赖精确匹配（如 {@code @ConditionalOnProperty(havingValue="local")}），
 * 当 mode 缺失/非法时三个实现会同时落空，容器中不存在任何 {@link OssStorageService} Bean。
 *
 * <p>改用本条件：本地实现在「非云模式」时兜底启用，保证始终存在一个实现；
 * 云实现（aliyun / tencent）仍由各自的 {@code @ConditionalOnProperty} 精确匹配、互斥装配。
 * 注意不用 {@code @ConditionalOnMissingBean}——在 {@code @ComponentScan} 扫描的
 * {@code @Service} 上，它判断的是「已注册的 BeanDefinition」而非「最终装配的 Bean」，
 * 云实现的 BeanDefinition 会先被扫描注册，导致本地误判为「已存在实现」而放弃装配。
 */
public class LocalOssCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("loan.oss.mode");
        // 未配置或为空 → 兜底本地
        if (mode == null || mode.trim().isEmpty()) {
            return true;
        }
        // 云模式 → 本地不装配（由 Aliyun/Tencent 互斥装配）；其余取值一律兜底本地
        return !"aliyun".equals(mode.trim()) && !"tencent".equals(mode.trim());
    }
}
