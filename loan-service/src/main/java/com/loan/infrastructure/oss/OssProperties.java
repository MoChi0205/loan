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
    private String mode = "local";
    private String bucket;
    private Aliyun aliyun = new Aliyun();
    @Data public static class Aliyun {
        private boolean enabled = false;
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
    }
}
