package com.meiyetec.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {
    /**
     * 把上传文件落到本地存储，返回相对路径（相对于 storage/dcep/qualification/）。
     * 调用方负责拼接前缀 /static/dcep/qualification/{path}。
     */
    String store(String materialType, MultipartFile file) throws IOException;

    /**
     * 根据相对路径删除。
     */
    void delete(String relativePath);

    /**
     * 拼接可访问 URL（前端用于 <el-image :src>）。
     */
    String url(String relativePath);
}