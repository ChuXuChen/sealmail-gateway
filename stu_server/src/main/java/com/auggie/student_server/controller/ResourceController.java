package com.auggie.student_server.controller;

import com.auggie.student_server.entity.Resource;
import com.auggie.student_server.service.FileStorageService;
import com.auggie.student_server.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: ResourceController
 * @Version 1.0.0
 */

@RestController
@CrossOrigin("*")
@RequestMapping("/resource")
public class ResourceController {
    @Autowired
    private ResourceService resourceService;

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ctid") Integer ctid,
            @RequestParam(value = "description", required = false) String description) {

        try {
            // 存储文件
            String storedFilename = fileStorageService.storeFile(file);

            // 创建资源记录
            Resource resource = new Resource();
            resource.setCtid(ctid);
            resource.setFilename(file.getOriginalFilename());
            resource.setFilepath(storedFilename);
            resource.setFilesize(file.getSize());
            resource.setUploadTime(LocalDateTime.now());
            resource.setDescription(description);

            // 保存到数据库
            boolean success = resourceService.save(resource);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "文件上传成功",
                        "filename", storedFilename,
                        "rid", resource.getRid()
                ));
            } else {
                // 如果数据库保存失败，删除已存储的文件
                fileStorageService.deleteFile(storedFilename);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "数据库保存失败"
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文件上传失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/download/{rid}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(@PathVariable("rid") Integer rid) {
        try {
            // 获取资源信息
            Resource resource = resourceService.findById(rid);
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }

            // 加载文件
            org.springframework.core.io.Resource fileResource = fileStorageService.loadFileAsResource(resource.getFilepath());

            // 设置响应头
            String contentType = "application/octet-stream";
            String encodedFilename = java.net.URLEncoder.encode(resource.getFilename(), "UTF-8").replaceAll("\\+", "%20");
            String headerValue = "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                    .body(fileResource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/findBySearch")
    public List<Resource> findBySearch(@RequestBody Map<String, String> map) {
        Integer rid = null;
        Integer ctid = null;
        String filename = null;
        String description = null;
        Integer fuzzy = null;

        if (map.containsKey("rid")) {
            try {
                rid = Integer.parseInt(map.get("rid"));
            } catch (Exception e) {
                // 忽略转换异常
            }
        }
        if (map.containsKey("ctid")) {
            try {
                ctid = Integer.parseInt(map.get("ctid"));
            } catch (Exception e) {
                // 忽略转换异常
            }
        }
        if (map.containsKey("filename")) {
            filename = map.get("filename");
        }
        if (map.containsKey("description")) {
            description = map.get("description");
        }
        if (map.containsKey("fuzzy")) {
            fuzzy = map.get("fuzzy").equals("true") ? 1 : 0;
        }

        System.out.println("查询资源: " + rid + ", " + ctid + ", " + filename + ", " + description + ", " + fuzzy);
        return resourceService.findBySearch(rid, ctid, filename, description, fuzzy);
    }

    @GetMapping("/findByCtid/{ctid}")
    public List<Resource> findByCtid(@PathVariable("ctid") Integer ctid) {
        System.out.println("按课程查询资源: " + ctid);
        return resourceService.findByCtid(ctid);
    }

    @GetMapping("/findByTeacher/{tid}")
    public List<Resource> findByTeacher(@PathVariable("tid") Integer tid) {
        System.out.println("按教师查询资源: " + tid);
        return resourceService.findByTeacher(tid);
    }

    @GetMapping("/findByStudent/{sid}")
    public List<Resource> findByStudent(@PathVariable("sid") Integer sid) {
        System.out.println("按学生查询资源: " + sid);
        return resourceService.findByStudent(sid);
    }

    @GetMapping("/delete/{rid}")
    public ResponseEntity<?> deleteResource(@PathVariable("rid") Integer rid) {
        try {
            // 获取资源信息
            Resource resource = resourceService.findById(rid);
            if (resource == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "资源不存在"
                ));
            }

            // 删除数据库记录
            boolean dbSuccess = resourceService.deleteById(rid);
            if (!dbSuccess) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "数据库删除失败"
                ));
            }

            // 删除物理文件
            boolean fileSuccess = fileStorageService.deleteFile(resource.getFilepath());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "资源删除成功",
                    "fileDeleted", fileSuccess
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateResource(@RequestBody Resource resource) {
        try {
            System.out.println("更新资源: " + resource);
            boolean success = resourceService.updateById(resource);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "资源更新成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "资源更新失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/findById/{rid}")
    public ResponseEntity<?> findById(@PathVariable("rid") Integer rid) {
        Resource resource = resourceService.findById(rid);
        if (resource != null) {
            return ResponseEntity.ok(resource);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "资源不存在"
            ));
        }
    }
}