package com.loan.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.Profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 在 Spring 上下文创建前注入 Nacos 连接参数并预拉取 {@code application.properties}。
 *
 * <p>对齐 tse {@code NacosEnvironmentPostProcessor}：保证 {@code @EnableNacos} / {@code @NacosPropertySource} /
 * {@code @Value} 启动阶段能正确解析 {@code ${nacos.server-addr}} / {@code ${nacos.namespace}} 等占位符，
 * 同时把 Nacos prd 配置作为最高优先级 PropertySource 注入 Environment，绕开 nacos-spring SDK 0.2.12
 * 加载配置的已知 bug。
 *
 * @author loan-platform
 */
public class NacosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String JVM_SOURCE = "loanNacosJvm";
    static final String REMOTE_SOURCE = "loanNacosRemoteApplication";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // L3 集成测试 / 离线测试：跳过远程 Nacos 拉取（配置由 application-l3.properties /
        // 本地兜底提供）。生产不会激活 l3/offline 剖面，也不会设置 -Dloan.nacos.disabled，
        // 故生产行为完全不变。
        if (isNacosFetchDisabled(environment)) {
            return;
        }

        String serverAddr = requireJvm("nacos.server-addr");
        String namespace = requireJvm("nacos.namespace");

        // 远端 Nacos HTTP=9848、gRPC=9948（偏移 +100，非 SDK 默认 +1000）
        System.setProperty("nacos.server.grpc.port.offset", "100");

        // 1) 把 Nacos 连接参数以最高优先级 MapPropertySource 加入 environment（覆盖本地占位符解析）
        Map<String, Object> jvmProps = new HashMap<>(8);
        jvmProps.put("nacos.server-addr", serverAddr);
        jvmProps.put("nacos.namespace", namespace);
        jvmProps.put("nacos.config.server-addr", serverAddr);
        jvmProps.put("nacos.config.namespace", namespace);
        jvmProps.put("nacos.server.grpc.port.offset", "100");
        // 仅允许启动脚本需要的有限 JVM 参数覆盖远端同名值。Nacos 远端配置被放在
        // JVM_SOURCE 之后，若不显式透传，server.port 等标准 -D 参数会反而被远端覆盖。
        copyJvmOverride(jvmProps, "server.port");
        copyJvmOverride(jvmProps, "app.gateway.trust-only");
        copyJvmOverride(jvmProps, "dubbo.enabled");
        copyJvmOverride(jvmProps, "spring.cloud.nacos.discovery.register-enabled");
        copyJvmOverride(jvmProps, "spring.cloud.nacos.discovery.enabled");
        copyJvmOverride(jvmProps, "loan.auth.dev-sms-code-visible");
        environment.getPropertySources().addFirst(new MapPropertySource(JVM_SOURCE, jvmProps));

        // 2) 直接 HTTP 拉取 Nacos prd 配置，作为 PropertiesPropertySource 加入 environment
        Properties remote = NacosRemoteConfigLoader.load(serverAddr, namespace, "application.properties", "loan");
        environment.getPropertySources().addAfter(JVM_SOURCE, new PropertiesPropertySource(REMOTE_SOURCE, remote));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 校验必填的 VM 参数（-D）。
     */
    private static String requireJvm(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "缺少 VM 参数 -D" + key + "；必须显式指定 Nacos，禁止回退本地基础设施");
        }
        return value.trim();
    }

    private static void copyJvmOverride(Map<String, Object> target, String key) {
        String value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value.trim());
        }
    }

    /**
     * 是否跳过远程 Nacos 拉取：L3 / offline 剖面或显式 -Dloan.nacos.disabled=true。
     */
    private static boolean isNacosFetchDisabled(ConfigurableEnvironment environment) {
        if (Boolean.getBoolean("loan.nacos.disabled")) {
            return true;
        }
        return environment.acceptsProfiles(Profiles.of("l3", "offline"));
    }
}
