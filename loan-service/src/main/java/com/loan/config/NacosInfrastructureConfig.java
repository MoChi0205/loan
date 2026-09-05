package com.loan.config;

import org.springframework.context.annotation.Configuration;

/**
 * Nacos 连接与 {@code application.properties} 全量加载（MySQL/Redis/JWT 等）。
 *
 * <p>对齐 tse {@code NacosInfrastructureConfig}：启动前必须在 VM 指定
 * {@code -Dnacos.server-addr}、{@code -Dnacos.namespace=dev|prd}。
 * loan 独立 group=loan（与 tse 的 group=tse 隔离，同实例共存）。
 *
 * @author loan-platform
 */
@Configuration
// 配置由 NacosEnvironmentPostProcessor 通过 HTTP 预加载一次。
// 不再叠加 @EnableNacos/@NacosPropertySource，避免重复创建 Nacos 客户端和 Spring 子上下文。
public class NacosInfrastructureConfig {
}
