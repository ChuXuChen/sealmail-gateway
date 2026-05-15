package com.auggie.student_server.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: Resource
 * @Version 1.0.0
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("Resource")
public class Resource {
    private Integer rid;
    private Integer ctid;
    private String filename;
    private String filepath;
    private Long filesize;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime uploadTime;
    private String description;
    private String courseName;
    private String teacherName;
    private String term;
}