package com.auggie.student_server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizAnswer 学生答题记录实体
 * @Version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("StudentQuizAnswer")
public class StudentQuizAnswer {
    private Integer answerId;
    private Integer quizId;
    private Integer questionId;
    private Integer sid;
    private String studentAnswer;
    private Boolean isCorrect;
    private Float score;
    private LocalDateTime answerTime;

    // AI功能预留字段
    private Float aiScore;
    private String aiAnalysis;
    private Float teacherScore;
    private String teacherComment;
    private Integer gradingStatus; // 0-待评，1-AI评分，2-教师评分，3-最终评分

    // 关联信息
    private String studentName;
    private String questionContent;
    private Integer questionType;
    private Float questionScore;

    // 正确答案（用于评阅参考）
    private String correctAnswer;
}
