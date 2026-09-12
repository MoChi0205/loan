package com.loan.infrastructure.oss;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.endpoint.SuffixEndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(name="loan.oss.mode", havingValue="tencent")
public class TencentCosClientConfiguration {
    @Bean(destroyMethod="shutdown")
    public COSClient tencentCosClient(OssProperties p) {
        OssProperties.Tencent c=p.getTencent();
        if(c==null||!c.isEnabled()||!StringUtils.hasText(c.getRegion())||!StringUtils.hasText(c.getSecretId())||!StringUtils.hasText(c.getSecretKey())||!StringUtils.hasText(p.getBucket())) throw new IllegalStateException("腾讯云 COS 配置不完整");
        COSCredentials cred=StringUtils.hasText(c.getSessionToken())?new BasicSessionCredentials(c.getSecretId(),c.getSecretKey(),c.getSessionToken()):new BasicCOSCredentials(c.getSecretId(),c.getSecretKey());
        ClientConfig cfg=new ClientConfig(new Region(c.getRegion())); cfg.setHttpProtocol(HttpProtocol.https);
        if(StringUtils.hasText(c.getEndpoint())) cfg.setEndpointBuilder(new SuffixEndpointBuilder(c.getEndpoint()));
        return new COSClient(cred,cfg);
    }
}
