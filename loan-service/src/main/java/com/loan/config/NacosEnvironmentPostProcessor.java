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
        String trustOnlyOverride = System.getProperty("app.gateway.trust-only");
        if (trustOnlyOverride != null && !trustOnlyOverride.trim().isEmpty()) {
            jvmProps.put("app.gateway.trust-only", trustOnlyOverride.trim());
        }
        // 开发模式（本地 IDEA 连 prd）经 -Ddubbo.enabled=false 关闭 Dubbo，避免向生产注册服务；
        // 以最高优先级注入，覆盖 Nacos 配置里的 dubbo.enabled=true
        String dubboEnabled = System.getProperty("dubbo.enabled");
        if (dubboEnabled != null && !dubboEnabled.trim().isEmpty()) {
            jvmProps.put("dubbo.enabled", dubboEnabled.trim());
        }
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
                    "缺少 VM 参数 -D" + key + "；示例: -Dnacos.server-addr=127.0.0.1:8848 -Dnacos.namespace=dev");
        }
        return value.trim();
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
