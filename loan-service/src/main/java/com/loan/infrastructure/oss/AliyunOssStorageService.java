package com.loan.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/** 阿里云 OSS 实现，契约与 tse 保持一致。客户端按请求创建并关闭，避免连接泄漏。 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "loan.oss.mode", havingValue = "aliyun")
public class AliyunOssStorageService implements OssStorageService {
    private final OssProperties properties;
    private OSS client() {
        OssProperties.Aliyun c = properties.getAliyun();
        if (c == null || !c.isEnabled() || isBlank(c.getEndpoint()) || isBlank(properties.getBucket())) {
            throw new IllegalStateException("阿里云 OSS 未配置完整");
        }
        return new OSSClientBuilder().build(c.getEndpoint(), c.getAccessKeyId(), c.getAccessKeySecret());
    }
    public void upload(String key, InputStream input, long length) {
        OSS c = client(); try { c.putObject(properties.getBucket(), key, input); } finally { c.shutdown(); }
    }
    public OssObjectOpenResult openObject(String key) {
        OSS c = client();
        try { OSSObject o = c.getObject(properties.getBucket(), key); ObjectMetadata m=o.getObjectMetadata();
            return new OssObjectOpenResult(o.getObjectContent(), m == null ? -1L : m.getContentLength(), m == null ? null : m.getContentType());
        } catch (RuntimeException e) { c.shutdown(); throw new IllegalStateException("OSS 文件不存在: " + key, e); }
    }
    public boolean objectExists(String key) { OSS c=client(); try { return c.doesObjectExist(properties.getBucket(), key); } catch (RuntimeException e) { log.warn("OSS 存在性检查失败 key={}",key,e); return false; } finally { c.shutdown(); } }
    public Optional<String> createPresignedDownloadUrl(String key, Duration ttl) { if (ttl==null||ttl.isZero()||ttl.isNegative()) return Optional.empty(); OSS c=client(); try { URL u=c.generatePresignedUrl(properties.getBucket(),key,new Date(System.currentTimeMillis()+ttl.toMillis()), HttpMethod.GET); return Optional.ofNullable(u).map(URL::toString); } finally { c.shutdown(); } }
    public void delete(String key) { OSS c=client(); try { c.deleteObject(properties.getBucket(),key); } finally { c.shutdown(); } }
    private static boolean isBlank(String s){ return s==null||s.trim().isEmpty(); }
}
