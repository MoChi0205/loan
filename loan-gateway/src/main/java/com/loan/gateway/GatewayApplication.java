package com.loan.gateway;

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
        SpringApplication.run(GatewayApplication.class, args);
    }
}
