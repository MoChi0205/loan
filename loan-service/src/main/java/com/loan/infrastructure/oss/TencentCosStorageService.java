package com.loan.infrastructure.oss;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

@Slf4j @Service @RequiredArgsConstructor
@ConditionalOnProperty(name="loan.oss.mode", havingValue="tencent")
public class TencentCosStorageService implements OssStorageService {
    private final OssProperties properties; private final COSClient client;
    public void upload(String key, InputStream in, long length){ ObjectMetadata m=new ObjectMetadata(); if(length>0)m.setContentLength(length); client.putObject(new PutObjectRequest(properties.getBucket(),key,in,m)); }
    public OssObjectOpenResult openObject(String key){ COSObject o=client.getObject(new GetObjectRequest(properties.getBucket(),key)); ObjectMetadata m=o.getObjectMetadata(); return new OssObjectOpenResult(o.getObjectContent(),m==null?-1:m.getContentLength(),m==null?null:m.getContentType()); }
    public boolean objectExists(String key){ try{return client.doesObjectExist(properties.getBucket(),key);}catch(RuntimeException e){log.warn("COS 存在性检查失败 key={}",key,e);return false;} }
    public Optional<String> createPresignedDownloadUrl(String key, Duration ttl){if(ttl==null||ttl.isZero()||ttl.isNegative())return Optional.empty(); URL u=client.generatePresignedUrl(properties.getBucket(),key,new Date(System.currentTimeMillis()+ttl.toMillis()),HttpMethodName.GET); return Optional.ofNullable(u).map(URL::toString);}
    public void delete(String key){client.deleteObject(properties.getBucket(),key);}
}
