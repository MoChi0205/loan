package com.loan.infrastructure.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "loan.oss")
public class OssProperties {
    /** 必须由 Nacos 明确提供，禁止代码默认本地存储。 */
    private String mode;
    private String bucket;
    private Aliyun aliyun = new Aliyun();
    private Tencent tencent = new Tencent();
    @Data public static class Aliyun {
        private boolean enabled = false;
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
    }
    @Data public static class Tencent {
        private boolean enabled = false;
        private String region;
        private String secretId;
        private String secretKey;
        private String sessionToken;
        private String endpoint;
    }
}
