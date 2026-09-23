package com.loan.gateway;

import com.loan.gateway.config.GatewayNacosEnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类（LOAN-GATEWAY）：Web + 小程序统一入口，全局接口鉴权 + 路由转发。
 *
 * <p>职责：所有外部请求先过 {@code ApiAuthGlobalFilter}（JWT 认证 + 角色×接口×端鉴权），
 * 通过后按路由转发到 loan-service。鉴权规则由 loan-service 下发到 Redis（loan:api-perm:rules）。
 *
 * @author loan-platform
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        // EnvironmentPostProcessor 校验并拉取 Nacos；显式引用保持启动约束可见。
        String nacosServer = System.getProperty("nacos.server-addr");
        String nacosNamespace = System.getProperty("nacos.namespace");
        if (nacosServer == null || nacosNamespace == null
                || nacosServer.trim().isEmpty() || nacosNamespace.trim().isEmpty()) {
            throw new IllegalStateException("网关必须通过 -Dnacos.server-addr 与 -Dnacos.namespace 启动");
        }
        // Nacos Spring Boot starter 的 Bootstrap 初始化早于普通 EnvironmentPostProcessor。
        // 在 SpringApplication 创建前同步设置 SDK 使用的键，避免其先回退到 127.0.0.1:8848。
        System.setProperty("nacos.config.server-addr", nacosServer.trim());
        System.setProperty("nacos.config.namespace", nacosNamespace.trim());
        System.setProperty("nacos.config.group", "loan");
        System.setProperty("spring.cloud.nacos.server-addr", nacosServer.trim());
        System.setProperty("spring.cloud.nacos.config.server-addr", nacosServer.trim());
        System.setProperty("spring.cloud.nacos.config.namespace", nacosNamespace.trim());
        System.setProperty("spring.cloud.nacos.config.group", "loan");
        System.setProperty("nacos.server.grpc.port.offset", "100");
        // 防止共享 Nacos 配置中的服务端口覆盖网关端口。
        System.setProperty("server.port", "9088");
        SpringApplication.run(GatewayApplication.class, args);
    }
}
