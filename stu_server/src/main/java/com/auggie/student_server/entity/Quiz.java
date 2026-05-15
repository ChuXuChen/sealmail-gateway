package com.auggie.student_server.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: Quiz 测验实体
 * @Version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("Quiz")
public class Quiz {
    private Integer quizId;
    private Integer ctid;
    private String quizTitle;
    private String quizDescription;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    private Integer duration;
    private Float totalScore;
    private Integer status; // 测验状态：0-未发布，1-已发布
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // AI功能预留字段
    private Boolean isAIGenerated;
    private String aiModel;
    private Integer difficultyLevel;
    private String questionStructure;

    // 关联信息
    private String courseName;
    private String teacherName;
    private String term;
}
