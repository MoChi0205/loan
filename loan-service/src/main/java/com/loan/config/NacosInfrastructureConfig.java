package com.loan.config;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.spring.context.annotation.EnableNacos;
import com.alibaba.nacos.spring.context.annotation.config.NacosPropertySource;
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
@EnableNacos(globalProperties = @NacosProperties(
        serverAddr = "${nacos.server-addr}",
        namespace = "${nacos.namespace}"
))
@NacosPropertySource(dataId = "application.properties", groupId = "loan", autoRefreshed = true)
public class NacosInfrastructureConfig {
}
