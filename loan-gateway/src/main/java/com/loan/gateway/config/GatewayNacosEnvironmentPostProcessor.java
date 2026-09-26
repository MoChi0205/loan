package com.loan.gateway.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** 网关启动前从指定 Nacos 拉取全部基础设施配置；不提供本地默认连接。 */
public class GatewayNacosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String server = required("nacos.server-addr");
        String namespace = required("nacos.namespace");
        Map<String, Object> properties = new HashMap<>();
        properties.put("nacos.server-addr", server);
        properties.put("nacos.namespace", namespace);
        // 同时覆盖 Alibaba Nacos SDK 使用的 spring.cloud.* 键，避免 SDK 在启动日志中
        // 显示 127.0.0.1:8848 / 空 namespace，并在部分 starter 版本中回退到本地 Nacos。
        properties.put("spring.cloud.nacos.server-addr", server);
        properties.put("spring.cloud.nacos.config.server-addr", server);
        properties.put("spring.cloud.nacos.config.namespace", namespace);
        properties.put("spring.cloud.nacos.config.group", "loan");
        properties.put("spring.cloud.nacos.discovery.server-addr", server);
        properties.put("spring.cloud.nacos.discovery.namespace", namespace);
        properties.put("spring.cloud.nacos.discovery.group", "loan");
        properties.put("spring.cloud.nacos.config.enabled", "false");
        properties.put("spring.cloud.nacos.discovery.enabled", "false");
        // application.properties 是服务与网关共用配置，不能让服务端 server.port=9080
        // 覆盖网关端口；网关固定使用 9088。
        properties.put("server.port", "9088");
        properties.put("spring.application.name", "loan-gateway");
        MapPropertySource jvm = new MapPropertySource("loanGatewayNacosJvm", properties);
        environment.getPropertySources().addFirst(jvm);
        environment.getPropertySources().addAfter("loanGatewayNacosJvm",
                new PropertiesPropertySource("loanGatewayNacosRemote", load(server, namespace)));
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少 VM 参数 -D" + key + "；网关必须从 Nacos 读取基础设施配置");
        }
        return value.trim();
    }

    private static Properties load(String server, String namespace) {
        try {
            String query = "dataId=application.properties&group=loan&tenant="
                    + URLEncoder.encode(namespace, StandardCharsets.UTF_8.name());
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://" + server + "/nacos/v1/cs/configs?" + query).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Nacos HTTP " + connection.getResponseCode()
                        + "，无法读取网关配置");
            }
            Properties properties = new Properties();
            try (InputStream input = connection.getInputStream()) {
                properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            }
            if (properties.isEmpty()) throw new IllegalStateException("Nacos 网关配置为空");
            return properties;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("拉取 Nacos 网关配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
