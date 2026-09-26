package com.loan.infrastructure.oss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Conditional;

import java.io.*;
import java.nio.file.*;

/** 仅当 Nacos 明确选择 local 模式时启用；生产不得依赖本地磁盘。 */
@Service
@Conditional(LocalOssCondition.class)
public class LocalOssStorageService implements OssStorageService {
    private final Path root;
    public LocalOssStorageService(@Value("${loan.upload.base-dir}") String baseDir) {
        this.root = Paths.get(baseDir).toAbsolutePath().normalize();
    }
    private Path path(String key) { return root.resolve(key).normalize(); }
    public void upload(String key, InputStream in, long length) {
        try { Path p=path(key); if(!p.startsWith(root)) throw new IOException("非法文件路径"); Files.createDirectories(p.getParent()); Files.copy(in,p,StandardCopyOption.REPLACE_EXISTING); }
        catch(IOException e){ throw new IllegalStateException("文件上传失败",e); }
    }
    public OssObjectOpenResult openObject(String key) {
        try { Path p=path(key); if(!p.startsWith(root)||!Files.exists(p)) throw new FileNotFoundException(key); return new OssObjectOpenResult(Files.newInputStream(p),Files.size(p),Files.probeContentType(p)); }
        catch(IOException e){ throw new IllegalStateException("文件不存在",e); }
    }
    public boolean objectExists(String key){ Path p=path(key); return p.startsWith(root)&&Files.isRegularFile(p); }
    public void delete(String key){ try { Files.deleteIfExists(path(key)); } catch(IOException e){ throw new IllegalStateException("文件删除失败",e); } }
}
