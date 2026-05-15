package com.auggie.student_server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: FileStorageService
 * @Version 1.0.0
 */

@Service
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
            System.out.println("文件上传目录创建成功: " + this.fileStorageLocation.toString());
        } catch (Exception ex) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, ex);
        }
    }

    public String storeFile(MultipartFile file) {
        // 原始文件名
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // 检查文件名是否包含非法字符
            if (originalFilename.contains("..")) {
                throw new RuntimeException("文件名包含非法路径序列: " + originalFilename);
            }

            // 生成唯一文件名：UUID + 原始扩展名
            String fileExtension = "";
            if (originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // 复制文件到目标位置
            Path targetLocation = this.fileStorageLocation.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 返回相对路径（仅文件名）
            return uniqueFilename;

        } catch (IOException ex) {
            throw new RuntimeException("无法存储文件: " + originalFilename, ex);
        }
    }

    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("文件未找到: " + filename);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("文件未找到: " + filename, ex);
        }
    }

    public boolean deleteFile(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            return Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new RuntimeException("无法删除文件: " + filename, ex);
        }
    }

    public String getUploadDir() {
        return this.fileStorageLocation.toString();
    }
}