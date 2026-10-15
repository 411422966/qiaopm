package com.meiyetec.service.impl;

import com.meiyetec.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * 本地文件系统存储实现。
 *
 * 存储根目录: ${app.storage.dir:./storage/dcep/qualification}
 * URL 前缀 : /static/dcep/qualification/{relative}
 *
 * 替换为 OSS/MinIO 时只需另写一个 @Primary 的实现，URL 前缀约定保持不变。
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.storage.dir:./storage/dcep/qualification}")
    private String storageDir;

    /** URL 前缀，统一通过前端 /api 代理访问 */
    private static final String URL_PREFIX = "/api/static/dcep/qualification/";

    private Path rootPath;

    @PostConstruct
    void init() throws IOException {
        rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(rootPath);
    }

    @Override
    public String store(String materialType, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("空文件");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) ext = original.substring(dot).toLowerCase();
        // 安全扩展名白名单
        if (!ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|bmp)")) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }
        // 防路径穿越：materialType 只能是字母数字下划线
        String safeType = materialType.replaceAll("[^a-zA-Z0-9_]", "");
        if (safeType.isEmpty()) safeType = "other";

        String name = safeType + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;
        Path target = rootPath.resolve(name).normalize();
        if (!target.startsWith(rootPath)) {
            throw new SecurityException("非法的存储路径");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return;
        try {
            Path p = rootPath.resolve(relativePath).normalize();
            if (p.startsWith(rootPath)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    @Override
    public String url(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        return URL_PREFIX + relativePath;
    }
}