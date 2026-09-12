package com.loan.infrastructure.oss;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/** 统一对象存储抽象，沿用 tse 的 upload/open/exists/预签名契约。 */
public interface OssStorageService {
    void upload(String objectKey, InputStream input, long contentLength);
    OssObjectOpenResult openObject(String objectKey);
    boolean objectExists(String objectKey);
    default Optional<String> createPresignedDownloadUrl(String objectKey, Duration ttl) { return Optional.empty(); }
    void delete(String objectKey);
}
