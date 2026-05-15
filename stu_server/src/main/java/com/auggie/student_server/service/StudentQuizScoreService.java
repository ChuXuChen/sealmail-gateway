package com.auggie.student_server.service;

import com.auggie.student_server.entity.Quiz;
import com.auggie.student_server.entity.QuizQuestion;
import com.auggie.student_server.entity.SCTInfo;
import com.auggie.student_server.entity.StudentQuizAnswer;
import com.auggie.student_server.entity.StudentQuizScore;
import com.auggie.student_server.mapper.QuizMapper;
import com.auggie.student_server.mapper.QuizQuestionMapper;
import com.auggie.student_server.mapper.StudentQuizAnswerMapper;
import com.auggie.student_server.mapper.StudentQuizScoreMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizScoreService 学生测验成绩服务类
 * @Version 1.0.0
 */
@Service
@Transactional
public class StudentQuizScoreService {
    @Autowired
    private StudentQuizScoreMapper studentQuizScoreMapper;

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private StudentQuizAnswerMapper studentQuizAnswerMapper;

    @Autowired
    private SCTService sctService;

    public List<StudentQuizScore> findAll() {
        return studentQuizScoreMapper.findAll();
    }

    public StudentQuizScore findById(Integer scoreId) {
        return studentQuizScoreMapper.findById(scoreId);
    }

    public StudentQuizScore findByQuizAndStudent(Integer quizId, Integer sid) {
        return studentQuizScoreMapper.findByQuizAndStudent(quizId, sid);
    }

    public List<StudentQuizScore> findByQuizId(Integer quizId) {
        return studentQuizScoreMapper.findByQuizId(quizId);
    }

    public List<StudentQuizScore> findByStudentId(Integer sid) {
        return studentQuizScoreMapper.findByStudentId(sid);
    }

    public boolean updateById(StudentQuizScore score) {
        return studentQuizScoreMapper.updateById(score) > 0;
    }

    public boolean save(StudentQuizScore score) {
        return studentQuizScoreMapper.save(score) > 0;
    }

    public boolean deleteById(Integer scoreId) {
        return studentQuizScoreMapper.deleteById(scoreId) > 0;
    }

    public boolean deleteByQuizId(Integer quizId) {
        return studentQuizScoreMapper.deleteByQuizId(quizId) > 0;
    }

    public boolean deleteByQuizAndStudent(Integer quizId, Integer sid) {
        return studentQuizScoreMapper.deleteByQuizAndStudent(quizId, sid) > 0;
    }

    /**
     * 提交测验并计算总分
     */
    public StudentQuizScore submitQuiz(Integer quizId, Integer sid) {
        // 检查是否已提交
        StudentQuizScore existing = studentQuizScoreMapper.findByQuizAndStudent(quizId, sid);
        if (existing != null && Boolean.TRUE.equals(existing.getIsSubmitted())) {
            return existing;
        }

        // 获取测验信息
        Quiz quiz = quizMapper.findById(quizId);
        if (quiz == null) {
            throw new RuntimeException("测验不存在");
        }

        // 获取所有题目
        List<QuizQuestion> questions = quizQuestionMapper.findByQuizId(quizId);

        // 获取学生答案
        List<StudentQuizAnswer> answers = studentQuizAnswerMapper.findByQuizAndStudent(quizId, sid);

        // 对未评分的答案进行自动评分
        for (QuizQuestion question : questions) {
            StudentQuizAnswer answer = answers.stream()
                    .filter(a -> a.getQuestionId() != null && a.getQuestionId().equals(question.getQuestionId()))
                    .findFirst()
                    .orElse(null);

            if (answer != null && answer.getIsCorrect() == null) {
                System.out.println("开始评分 - 题目ID: " + question.getQuestionId() +
                                 ", 题目类型: " + question.getQuestionType() +
                                 ", 题目分数: " + question.getQuestionScore());
                int questionType = question.getQuestionType();
                // 单选题、多选题、判断题自动评分
                if (questionType == 1 || questionType == 2 || questionType == 3) {
                    String studentAnswer = parseStudentAnswer(answer.getStudentAnswer());
                    String correctAnswer = parseCorrectAnswer(question.getCorrectAnswer(), questionType);
                    System.out.println("答案比较 - 学生答案: " + studentAnswer +
                                     ", 正确答案: " + correctAnswer);
                    if (studentAnswer != null && correctAnswer != null) {
                        boolean isCorrect;
                        if (questionType == 2) {
                            // 多选题
                            isCorrect = compareMultiChoiceAnswers(studentAnswer, correctAnswer);
                        } else {
                            // 单选题、判断题：直接比较字符串
                            isCorrect = studentAnswer.equals(correctAnswer);
                        }
                        System.out.println("评分结果: " + isCorrect);
                        answer.setIsCorrect(isCorrect);
                        Float score = isCorrect ? question.getQuestionScore() : 0f;
                        answer.setScore(score);
                        answer.setGradingStatus(1); // AI评分完成
                        System.out.println("设置分数: " + score);
                        studentQuizAnswerMapper.updateById(answer);
                    }
                }
            } else if (answer != null) {
                System.out.println("跳过评分 - 题目ID: " + question.getQuestionId() +
                                 ", 已评分: " + answer.getIsCorrect() +
                                 ", 当前分数: " + answer.getScore());
            }
        }

        // 重新获取已更新的答案
        answers = studentQuizAnswerMapper.findByQuizAndStudent(quizId, sid);

        // 计算总分（确保每个题目只计算一次）
        float totalScore = quiz.getTotalScore();
        float obtainedScore = 0;
        java.util.Set<Integer> processedQuestionIds = new java.util.HashSet<>();
        System.out.println("开始计算总分，测验总分: " + totalScore);
        for (StudentQuizAnswer answer : answers) {
            System.out.println("答案ID: " + answer.getAnswerId() +
                             ", 题目ID: " + answer.getQuestionId() +
                             ", 是否正确: " + answer.getIsCorrect() +
                             ", 得分: " + answer.getScore());
            // 确保每个题目只计算一次分数
            if (answer.getQuestionId() != null && !processedQuestionIds.contains(answer.getQuestionId())) {
                if (answer.getScore() != null) {
                    obtainedScore += answer.getScore();
                }
                processedQuestionIds.add(answer.getQuestionId());
            } else if (answer.getQuestionId() != null) {
                System.out.println("跳过重复题目: " + answer.getQuestionId());
            }
        }
        System.out.println("计算完成，实得分: " + obtainedScore);

        // 创建或更新成绩记录
        StudentQuizScore score;
        if (existing == null) {
            score = new StudentQuizScore();
            score.setQuizId(quizId);
            score.setSid(sid);
            score.setTotalScore(totalScore);
            score.setObtainedScore(obtainedScore);
            score.setSubmitTime(LocalDateTime.now());
            score.setIsSubmitted(true);
            score.setIsGraded(true); // 默认已评分，主观题后续可更新
            score.setQuizTitle(quiz.getQuizTitle());
            studentQuizScoreMapper.save(score);
        } else {
            existing.setTotalScore(totalScore);
            existing.setObtainedScore(obtainedScore);
            existing.setSubmitTime(LocalDateTime.now());
            existing.setIsSubmitted(true);
            existing.setIsGraded(true);
            existing.setQuizTitle(quiz.getQuizTitle());
            studentQuizScoreMapper.updateById(existing);
            score = existing;
        }

        return score;
    }

    /**
     * 解析学生答案，处理JSON字符串
     */
    private String parseStudentAnswer(String studentAnswer) {
        if (studentAnswer == null) {
            return null;
        }
        String trimmed = studentAnswer.trim();
        // 处理JSON格式的字符串（如 "\"A\""、"\"1\""、"\"true\""、"\"正确\""、"\"错误\""）
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                String parsed = trimmed.substring(1, trimmed.length() - 1);
                // 判断题特殊处理：将各种格式的答案统一转换为 "A" 或 "B"
                if ("1".equals(parsed) || "true".equalsIgnoreCase(parsed) || "正确".equals(parsed) || "A".equalsIgnoreCase(parsed)) {
                    return "A";
                } else if ("0".equals(parsed) || "false".equalsIgnoreCase(parsed) || "错误".equals(parsed) || "B".equalsIgnoreCase(parsed)) {
                    return "B";
                }
                return parsed;
            } catch (Exception e) {
                return trimmed;
            }
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        // 处理判断题的特殊情况：直接比较字符串格式的答案
        if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "正确".equals(trimmed) || "A".equalsIgnoreCase(trimmed)) {
            return "A";
        } else if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed) || "错误".equals(trimmed) || "B".equalsIgnoreCase(trimmed)) {
            return "B";
        }
        return trimmed;
    }

    /**
     * 解析正确答案，统一格式
     */
    private String parseCorrectAnswer(String correctAnswer, int questionType) {
        if (correctAnswer == null) {
            return null;
        }
        String trimmed = correctAnswer.trim();

        // 判断题特殊处理：如果正确答案是 '正确' 或 '错误'，转换为 'A' 或 'B' 与学生答案保持一致
        if (questionType == 3) {
            if ("正确".equals(trimmed) || "A".equals(trimmed) || "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
                return "A";
            } else if ("错误".equals(trimmed) || "B".equals(trimmed) || "0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                return "B";
            }
        }

        return trimmed;
    }

    /**
     * 比较多选题答案
     */
    private boolean compareMultiChoiceAnswers(String studentAnswer, String correctAnswer) {
        try {
            // 规范化学生答案
            java.util.List<String> studentList;
            if (studentAnswer.startsWith("[") && studentAnswer.endsWith("]")) {
                // 解析JSON数组
                ObjectMapper mapper = new ObjectMapper();
                studentList = mapper.readValue(studentAnswer, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            } else {
                // 逗号分隔
                studentList = java.util.Arrays.asList(studentAnswer.split(","));
            }

            // 规范化正确答案
            java.util.List<String> correctList;
            if (correctAnswer.startsWith("[") && correctAnswer.endsWith("]")) {
                ObjectMapper mapper = new ObjectMapper();
                correctList = mapper.readValue(correctAnswer, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            } else {
                correctList = java.util.Arrays.asList(correctAnswer.split(","));
            }

            // 排序后比较
            java.util.Collections.sort(studentList);
            java.util.Collections.sort(correctList);
            return studentList.equals(correctList);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 重新计算学生测验总分并更新成绩记录
     * @param quizId 测验ID
     * @param sid 学生ID
     * @return 更新后的成绩记录
     */
    public StudentQuizScore recalculateScore(Integer quizId, Integer sid) {
        // 检查成绩记录是否存在
        StudentQuizScore existing = studentQuizScoreMapper.findByQuizAndStudent(quizId, sid);
        if (existing == null) {
            throw new RuntimeException("成绩记录不存在，无法重新计算");
        }

        // 获取测验信息
        Quiz quiz = quizMapper.findById(quizId);
        if (quiz == null) {
            throw new RuntimeException("测验不存在");
        }

        // 获取学生答案
        List<StudentQuizAnswer> answers = studentQuizAnswerMapper.findByQuizAndStudent(quizId, sid);

        // 计算总分
        float totalScore = quiz.getTotalScore();
        float obtainedScore = 0;
        java.util.Set<Integer> processedQuestionIds = new java.util.HashSet<>();
        System.out.println("重新计算总分，测验总分: " + totalScore);
        for (StudentQuizAnswer answer : answers) {
            System.out.println("答案ID: " + answer.getAnswerId() +
                             ", 题目ID: " + answer.getQuestionId() +
                             ", 是否正确: " + answer.getIsCorrect() +
                             ", 得分: " + answer.getScore());
            // 确保每个题目只计算一次分数
            if (answer.getQuestionId() != null && !processedQuestionIds.contains(answer.getQuestionId())) {
                if (answer.getScore() != null) {
                    obtainedScore += answer.getScore();
                }
                processedQuestionIds.add(answer.getQuestionId());
            } else if (answer.getQuestionId() != null) {
                System.out.println("跳过重复题目: " + answer.getQuestionId());
            }
        }
        System.out.println("重新计算完成，实得分: " + obtainedScore);

        // 更新成绩记录
        existing.setTotalScore(totalScore);
        existing.setObtainedScore(obtainedScore);
        existing.setIsGraded(true); // 标记为已评分
        existing.setQuizTitle(quiz.getQuizTitle());

        // 执行更新，即使没有字段变化也认为成功（因为分数可能没有变化）
        studentQuizScoreMapper.updateById(existing);

        return existing;
    }

    /**
     * 获取测验所属课程的所有学生的成绩列表，包括未提交的学生
     * @param quizId 测验ID
     * @return 学生成绩列表
     */
    public List<StudentQuizScore> findAllStudentsWithScores(Integer quizId) {
        System.out.println("开始查询所有学生成绩，quizId=" + quizId);

        // 获取测验信息
        Quiz quiz = quizMapper.findById(quizId);
        if (quiz == null) {
            System.out.println("测验不存在，quizId=" + quizId);
            throw new RuntimeException("测验不存在");
        }
        System.out.println("获取到测验信息: " + quiz);
        System.out.println("测验的ctid: " + quiz.getCtid());

        if (quiz.getCtid() == null) {
            System.out.println("测验的ctid为空，无法查询学生列表");
            throw new RuntimeException("测验信息不完整，缺少课程关联信息");
        }

        // 获取该课程的所有学生
        List<SCTInfo> students = sctService.findByCtid(quiz.getCtid());
        System.out.println("查询到学生数量: " + (students != null ? students.size() : 0));
        if (students != null) {
            for (SCTInfo student : students) {
                System.out.println("学生: " + student);
            }
        }

        // 获取已提交的成绩
        List<StudentQuizScore> scores = studentQuizScoreMapper.findByQuizId(quizId);
        System.out.println("查询到已提交成绩数量: " + (scores != null ? scores.size() : 0));

        // 创建学生ID到成绩的映射
        java.util.Map<Integer, StudentQuizScore> scoreMap = new java.util.HashMap<>();
        if (scores != null) {
            for (StudentQuizScore score : scores) {
                scoreMap.put(score.getSid(), score);
            }
        }

        // 构建结果列表
        List<StudentQuizScore> result = new java.util.ArrayList<>();
        if (students != null) {
            for (SCTInfo student : students) {
                StudentQuizScore score = scoreMap.get(student.getSid());
                if (score != null) {
                    // 已有成绩记录，确保学生姓名设置
                    if (score.getStudentName() == null || score.getStudentName().isEmpty()) {
                        score.setStudentName(student.getSname());
                    }
                    result.add(score);
                } else {
                    // 没有成绩记录，创建默认记录
                    StudentQuizScore newScore = new StudentQuizScore();
                    newScore.setQuizId(quizId);
                    newScore.setSid(student.getSid());
                    newScore.setStudentName(student.getSname());
                    newScore.setTotalScore(quiz.getTotalScore());
                    newScore.setObtainedScore(null);
                    newScore.setIsSubmitted(false);
                    newScore.setIsGraded(false);
                    newScore.setQuizTitle(quiz.getQuizTitle());
                    result.add(newScore);
                }
            }
        }

        System.out.println("返回结果数量: " + result.size());
        return result;
    }
}
