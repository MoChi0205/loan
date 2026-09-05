package com.loan.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 启动前通过 Nacos Open API（HTTP）直接拉取配置。
 *
 * <p>对齐 tse 实现：绕过 nacos-spring SDK 的 gRPC 端口偏移问题（在 Spring 最早阶段直接用 HTTP
 * 拉取配置，再以 PropertiesPropertySource 注入 Environment，让 @EnableNacos / @NacosPropertySource /
 * @Value 等所有占位符解析都直接可用）。
 *
 * @author loan-platform
 */
final class NacosRemoteConfigLoader {

    private NacosRemoteConfigLoader() {
    }

    /**
     * 拉取 Nacos 配置。
     *
     * @param serverAddr nacos server（host:port）
     * @param namespace  nacos namespace
     * @param dataId     dataId
     * @param group      group
     * @return 解析后的 Properties
     */
    static Properties load(String serverAddr, String namespace, String dataId, String group) {
        try {
            String query = "dataId=" + encode(dataId)
                    + "&group=" + encode(group)
                    + "&tenant=" + encode(namespace == null ? "" : namespace);
            URL url = new URL("http://" + serverAddr + "/nacos/v1/cs/configs?" + query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(
                        "Nacos HTTP " + status + ": " + url + " namespace=" + namespace);
            }
            String content = readBody(conn);
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Nacos 配置为空: dataId=" + dataId + ", group=" + group + ", namespace=" + namespace);
            }
            Properties props = new Properties();
            props.load(new StringReader(content));
            return props;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "拉取 Nacos 配置失败: " + dataId + "@" + group + " namespace=" + namespace
                            + " server=" + serverAddr,
                    e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        byte[] buf = new byte[8192];
        StringBuilder body = new StringBuilder();
        try (InputStream in = conn.getInputStream()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                body.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        return body.toString();
    }
}
