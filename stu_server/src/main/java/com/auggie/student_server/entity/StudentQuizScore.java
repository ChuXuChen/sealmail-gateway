package com.auggie.student_server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizScore 学生测验成绩实体
 * @Version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("StudentQuizScore")
public class StudentQuizScore {
    private Integer scoreId;
    private Integer quizId;
    private Integer sid;
    private Float totalScore;
    private Float obtainedScore;
    private LocalDateTime submitTime;
    private Boolean isSubmitted = false;
    private Boolean isGraded = false;

    // 关联信息
    private String studentName;
    private String quizTitle;
    private Float quizTotalScore;
}
