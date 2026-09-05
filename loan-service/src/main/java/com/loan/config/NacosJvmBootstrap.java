package com.loan.config;

import org.springframework.boot.SpringApplication;

import java.util.Properties;

/**
 * 启动前的 JVM/Environment 引导。
 *
 * <p>对齐 tse {@code NacosJvmBootstrap}：{@code @EnableNacos} 注解内的 {@code ${nacos.server-addr}} 在
 * Spring 占位符解析前就会初始化，仅靠 VM {@code -Dnacos.*} 不够；本类在 main 阶段把
 * nacos.server-addr / nacos.namespace 同时写入 JVM 系统属性与 Spring defaultProperties。
 *
 * @author loan-platform
 */
public final class NacosJvmBootstrap {

    private NacosJvmBootstrap() {
    }

    /**
     * 校验并注入 Nacos 连接参数。
     *
     * @param application SpringApplication 实例
     */
    public static void prepare(SpringApplication application) {
        String serverAddr = requireJvm("nacos.server-addr");
        String namespace = requireJvm("nacos.namespace");

        // 远端 Nacos HTTP=9848、gRPC=9948（偏移 +100）
        System.setProperty("nacos.server.grpc.port.offset", "100");
        System.setProperty("nacos.config.server-addr", serverAddr);
        System.setProperty("nacos.config.namespace", namespace);

        Properties defaults = new Properties();
        defaults.setProperty("nacos.server-addr", serverAddr);
        defaults.setProperty("nacos.namespace", namespace);
        defaults.setProperty("nacos.config.server-addr", serverAddr);
        defaults.setProperty("nacos.config.namespace", namespace);
        defaults.setProperty("nacos.server.grpc.port.offset", "100");
        application.setDefaultProperties(defaults);
    }

    private static String requireJvm(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "缺少 VM 参数 -D" + key + "；示例: -Dnacos.server-addr=127.0.0.1:8848 -Dnacos.namespace=dev");
        }
        return value.trim();
    }
}
