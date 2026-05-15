package com.auggie.student_server.service;

import com.auggie.student_server.entity.QuizQuestion;
import com.auggie.student_server.entity.StudentQuizAnswer;
import com.auggie.student_server.mapper.QuizQuestionMapper;
import com.auggie.student_server.mapper.StudentQuizAnswerMapper;
import com.auggie.student_server.service.StudentQuizScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizAnswerService 学生答题服务类
 * @Version 1.0.0
 */
@Service
@Transactional
public class StudentQuizAnswerService {
    @Autowired
    private StudentQuizAnswerMapper studentQuizAnswerMapper;

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private StudentQuizScoreService studentQuizScoreService;

    public List<StudentQuizAnswer> findAll() {
        return studentQuizAnswerMapper.findAll();
    }

    public StudentQuizAnswer findById(Integer answerId) {
        return studentQuizAnswerMapper.findById(answerId);
    }

    public List<StudentQuizAnswer> findByQuizId(Integer quizId) {
        return studentQuizAnswerMapper.findByQuizId(quizId);
    }

    public List<StudentQuizAnswer> findByQuizAndStudent(Integer quizId, Integer sid) {
        return studentQuizAnswerMapper.findByQuizAndStudent(quizId, sid);
    }

    public List<StudentQuizAnswer> findByQuestionId(Integer questionId) {
        return studentQuizAnswerMapper.findByQuestionId(questionId);
    }

    public boolean updateById(StudentQuizAnswer answer) {
        // 先查询当前记录，确保存在
        StudentQuizAnswer existingAnswer = studentQuizAnswerMapper.findById(answer.getAnswerId());
        if (existingAnswer == null) {
            return false;
        }

        // 执行更新，即使没有字段变化也认为成功（因为教师的保存操作是有效的）
        studentQuizAnswerMapper.updateById(answer);

        // 只要记录存在，就返回成功
        if (answer.getQuizId() != null && answer.getSid() != null) {
            try {
                // 更新答案后重新计算总分
                studentQuizScoreService.recalculateScore(answer.getQuizId(), answer.getSid());
                System.out.println("答案更新后重新计算总分: quizId=" + answer.getQuizId() + ", sid=" + answer.getSid());
            } catch (Exception e) {
                System.err.println("重新计算总分失败: " + e.getMessage());
                // 不因为重新计算失败而影响答案更新
            }
        }
        return true;
    }

    public boolean save(StudentQuizAnswer answer) {
        // 避免重复保存：先删除该学生该题的旧答案
        if (answer.getQuizId() != null && answer.getQuestionId() != null && answer.getSid() != null) {
            // 查询是否已存在该题的答案
            List<StudentQuizAnswer> existingAnswers = studentQuizAnswerMapper.findByQuizAndStudent(answer.getQuizId(), answer.getSid());
            for (StudentQuizAnswer existing : existingAnswers) {
                if (existing.getQuestionId().equals(answer.getQuestionId())) {
                    studentQuizAnswerMapper.deleteById(existing.getAnswerId());
                }
            }
        }

        // 自动评分逻辑（客观题）
        if (answer.getQuestionId() != null) {
            QuizQuestion question = quizQuestionMapper.findById(answer.getQuestionId());
            if (question != null) {
                int questionType = question.getQuestionType();
                // 单选题、多选题、判断题自动评分
                if (questionType == 1 || questionType == 2 || questionType == 3) {
                    String studentAnswer = parseStudentAnswer(answer.getStudentAnswer(), questionType);
                    String correctAnswer = parseCorrectAnswer(question.getCorrectAnswer(), questionType);
                    if (studentAnswer != null && correctAnswer != null) {
                        boolean isCorrect;
                        if (questionType == 2) {
                            // 多选题：处理JSON数组格式的学生答案和逗号分隔的正确答案
                            isCorrect = compareMultiChoiceAnswers(studentAnswer, correctAnswer);
                        } else {
                            // 单选题、判断题：直接比较字符串
                            isCorrect = studentAnswer.equals(correctAnswer);
                        }
                        answer.setIsCorrect(isCorrect);
                        answer.setScore(isCorrect ? question.getQuestionScore() : 0f);
                        answer.setGradingStatus(1); // AI评分完成
                    }
                } else if (questionType == 4 || questionType == 5) {
                    // 填空题和简答题：不自动评分，设置为待批改状态
                    answer.setIsCorrect(null); // 不设置对错
                    answer.setScore(null); // 不设置分数
                    answer.setGradingStatus(0); // 待教师批改
                }
            }
        }
        return studentQuizAnswerMapper.save(answer) > 0;
    }

    public boolean saveBatch(List<StudentQuizAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return false;
        }

        // 避免重复保存：先删除该学生该测验的所有旧答案
        if (!answers.isEmpty()) {
            Integer quizId = answers.get(0).getQuizId();
            Integer sid = answers.get(0).getSid();
            if (quizId != null && sid != null) {
                studentQuizAnswerMapper.deleteByQuizAndStudent(quizId, sid);
            }
        }

        // 对批量保存的答案进行自动评分
        for (StudentQuizAnswer answer : answers) {
            // 跳过没有 questionId 的答案
            if (answer.getQuestionId() == null) {
                continue;
            }
            QuizQuestion question = quizQuestionMapper.findById(answer.getQuestionId());
            if (question != null) {
                int questionType = question.getQuestionType();
                if (questionType == 1 || questionType == 2 || questionType == 3) {
                    String studentAnswer = parseStudentAnswer(answer.getStudentAnswer(), questionType);
                    String correctAnswer = parseCorrectAnswer(question.getCorrectAnswer(), questionType);
                    if (studentAnswer != null && correctAnswer != null) {
                        boolean isCorrect;
                        if (questionType == 2) {
                            isCorrect = compareMultiChoiceAnswers(studentAnswer, correctAnswer);
                        } else {
                            isCorrect = studentAnswer.equals(correctAnswer);
                        }
                        answer.setIsCorrect(isCorrect);
                        answer.setScore(isCorrect ? question.getQuestionScore() : 0f);
                        answer.setGradingStatus(1); // AI评分完成
                    }
                } else if (questionType == 4 || questionType == 5) {
                    // 填空题和简答题：不自动评分，设置为待批改状态
                    answer.setIsCorrect(null); // 不设置对错
                    answer.setScore(null); // 不设置分数
                    answer.setGradingStatus(0); // 待教师批改
                }
            }
        }
        // 过滤掉没有 questionId 的答案再保存
        List<StudentQuizAnswer> validAnswers = answers.stream()
                .filter(a -> a.getQuestionId() != null)
                .collect(java.util.stream.Collectors.toList());
        if (validAnswers.isEmpty()) {
            return false;
        }
        return studentQuizAnswerMapper.saveBatch(validAnswers) > 0;
    }

    /**
     * 解析学生答案，处理JSON字符串
     */
    private String parseStudentAnswer(String studentAnswer, int questionType) {
        if (studentAnswer == null) {
            return null;
        }
        // 去除首尾空格
        String trimmed = studentAnswer.trim();
        // 处理JSON格式的字符串（如 "\"A\""、"\"1\""、"\"true\""、"\"正确\""、"\"错误\""）
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                String parsed = trimmed.substring(1, trimmed.length() - 1);
                // 只有判断题做特殊处理：将各种格式的答案统一转换为 "A" 或 "B"
                if (questionType == 3) {
                    if ("1".equals(parsed) || "true".equalsIgnoreCase(parsed) || "正确".equals(parsed) || "A".equalsIgnoreCase(parsed)) {
                        return "A";
                    } else if ("0".equals(parsed) || "false".equalsIgnoreCase(parsed) || "错误".equals(parsed) || "B".equalsIgnoreCase(parsed)) {
                        return "B";
                    }
                }
                return parsed;
            } catch (Exception e) {
                return trimmed;
            }
        }
        // 尝试解析JSON数组（多选题）
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        // 只有判断题处理这种格式
        if (questionType == 3) {
            if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "正确".equals(trimmed) || "A".equalsIgnoreCase(trimmed)) {
                return "A";
            } else if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed) || "错误".equals(trimmed) || "B".equalsIgnoreCase(trimmed)) {
                return "B";
            }
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

        // 处理AI生成的带括号答案格式，如"B(错误)"、"A(正确)"
        if (trimmed.matches("^[AB]\\((正确|错误)\\)$")) {
            trimmed = trimmed.substring(0, 1);
        }

        // 只有判断题统一处理：将 '正确'/'错误'、'1'/'0'、'true'/'false' 统一转换为 'A'/'B'
        if (questionType == 3) {
            if ("正确".equals(trimmed) || "A".equals(trimmed) || "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
                return "A";
            } else if ("错误".equals(trimmed) || "B".equals(trimmed) || "0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                return "B";
            }
        }

        return trimmed;
    }

    public boolean deleteById(Integer answerId) {
        return studentQuizAnswerMapper.deleteById(answerId) > 0;
    }

    public boolean deleteByQuizId(Integer quizId) {
        return studentQuizAnswerMapper.deleteByQuizId(quizId) > 0;
    }

    public boolean deleteByQuizAndStudent(Integer quizId, Integer sid) {
        return studentQuizAnswerMapper.deleteByQuizAndStudent(quizId, sid) > 0;
    }

    /**
     * 规范化答案字符串，处理JSON序列化
     */
    private String normalizeAnswer(String answer) {
        if (answer == null) return null;
        try {
            // 尝试解析JSON
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(answer, Object.class);
            if (parsed instanceof List) {
                // 数组：排序并连接
                List<?> list = (List<?>) parsed;
                List<String> stringList = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        stringList.add(String.valueOf(item));
                    }
                }
                Collections.sort(stringList);
                return String.join(",", stringList);
            } else {
                // 单个值：转换为字符串
                return String.valueOf(parsed);
            }
        } catch (Exception e) {
            // 不是有效的JSON，检查是否是引号包裹的字符串
            if (answer.length() >= 2 && answer.startsWith("\"") && answer.endsWith("\"")) {
                return answer.substring(1, answer.length() - 1);
            }
            return answer;
        }
    }

    /**
     * 比较多选题答案
     * @param studentAnswer JSON格式的学生答案，如 ["A","C"] 或 "[\"A\",\"C\"]"
     * @param correctAnswer 逗号分隔的正确答案，如 "A,C" 或 JSON格式的 "[\"A\",\"C\"]"
     * @return 答案是否正确
     */
    private boolean compareMultiChoiceAnswers(String studentAnswer, String correctAnswer) {
        String normalizedStudent = normalizeAnswer(studentAnswer);
        String normalizedCorrect = normalizeAnswer(correctAnswer);
        return normalizedStudent.equals(normalizedCorrect);
    }
}
