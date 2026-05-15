package com.auggie.student_server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizQuestion 测验题目实体
 * @Version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("QuizQuestion")
public class QuizQuestion {
    private Integer questionId;
    private Integer quizId;
    private Integer questionType; // 1-单选题，2-多选题，3-判断题，4-填空题，5-简答题
    private String questionContent;
    private Float questionScore;
    private String options; // JSON格式存储选项
    private String correctAnswer;
    private Integer questionOrder;

    // AI功能预留字段
    private Boolean isAIGenerated;
    private Float aiConfidence;
    private Integer sourceResource;
}
