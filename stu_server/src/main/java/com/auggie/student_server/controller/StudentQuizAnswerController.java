package com.auggie.student_server.controller;

import com.auggie.student_server.entity.StudentQuizAnswer;
import com.auggie.student_server.service.StudentQuizAnswerService;
import com.auggie.student_server.service.AIService;
import com.auggie.student_server.entity.QuizQuestion;
import com.auggie.student_server.service.QuizQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizAnswerController 学生答题控制器
 * @Version 1.0.0
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/quizAnswer")
public class StudentQuizAnswerController {
    @Autowired
    private StudentQuizAnswerService studentQuizAnswerService;

    @GetMapping("/findAll")
    public List<StudentQuizAnswer> findAll() {
        return studentQuizAnswerService.findAll();
    }

    @GetMapping("/findById/{answerId}")
    public ResponseEntity<?> findById(@PathVariable("answerId") Integer answerId) {
        StudentQuizAnswer answer = studentQuizAnswerService.findById(answerId);
        if (answer != null) {
            return ResponseEntity.ok(answer);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "答题记录不存在"
            ));
        }
    }

    @GetMapping("/findByQuizId/{quizId}")
    public List<StudentQuizAnswer> findByQuizId(@PathVariable("quizId") Integer quizId) {
        System.out.println("按测验查询答题记录: " + quizId);
        return studentQuizAnswerService.findByQuizId(quizId);
    }

    @GetMapping("/findByQuizAndStudent/{quizId}/{sid}")
    public List<StudentQuizAnswer> findByQuizAndStudent(
            @PathVariable("quizId") Integer quizId,
            @PathVariable("sid") Integer sid) {
        System.out.println("按测验和学生查询答题记录: quizId=" + quizId + ", sid=" + sid);
        return studentQuizAnswerService.findByQuizAndStudent(quizId, sid);
    }

    @GetMapping("/findByQuestionId/{questionId}")
    public List<StudentQuizAnswer> findByQuestionId(@PathVariable("questionId") Integer questionId) {
        System.out.println("按题目查询答题记录: " + questionId);
        return studentQuizAnswerService.findByQuestionId(questionId);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveAnswer(@RequestBody StudentQuizAnswer answer) {
        try {
            System.out.println("保存答案: " + answer);
            boolean success = studentQuizAnswerService.save(answer);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "答案保存成功",
                        "answerId", answer.getAnswerId()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "答案保存失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "保存失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/saveBatch")
    public ResponseEntity<?> saveBatchAnswers(@RequestBody List<StudentQuizAnswer> answers) {
        try {
            System.out.println("批量保存答案，数量: " + (answers != null ? answers.size() : 0));
            boolean success = studentQuizAnswerService.saveBatch(answers);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "答案批量保存成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "答案批量保存失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量保存失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateAnswer(@RequestBody StudentQuizAnswer answer) {
        try {
            System.out.println("更新答案: " + answer);
            boolean success = studentQuizAnswerService.updateById(answer);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "答案更新成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "答案更新失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/delete/{answerId}")
    public ResponseEntity<?> deleteAnswer(@PathVariable("answerId") Integer answerId) {
        try {
            boolean success = studentQuizAnswerService.deleteById(answerId);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "答题记录删除成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "答题记录删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    // ==================== AI智能阅卷接口 ====================

    @Autowired
    private AIService aiService;

    @Autowired
    private QuizQuestionService quizQuestionService;

    /**
     * AI智能评分
     * 功能：对单个题目答案进行AI评分
     */
    @PostMapping("/gradeByAI")
    public ResponseEntity<?> gradeByAI(@RequestBody Map<String, Object> request) {
        System.out.println("AI评分请求: " + request);
        try {
            Integer answerId = (Integer) request.get("answerId");
            String questionContent = (String) request.get("questionContent");
            String correctAnswer = (String) request.get("correctAnswer");
            String studentAnswer = (String) request.get("studentAnswer");
            // 获取题目分值，默认为10分
            Float questionScore = 10.0f;
            if (request.containsKey("questionScore")) {
                Object scoreObj = request.get("questionScore");
                if (scoreObj instanceof Number) {
                    questionScore = ((Number) scoreObj).floatValue();
                }
            }

            // 调用AI服务进行评分，传入题目分值
            Map<String, Object> aiResult = aiService.gradeAnswerByAI(answerId, questionContent, correctAnswer, studentAnswer, questionScore);

            if ((Boolean) aiResult.get("success")) {
                StudentQuizAnswer answer = studentQuizAnswerService.findById(answerId);
                if (answer != null) {
                    answer.setAiScore((Float) aiResult.get("aiScore"));
                    answer.setAiAnalysis((String) aiResult.get("aiAnalysis"));
                    answer.setGradingStatus(1); // AI评分完成
                    answer.setScore(answer.getAiScore());
                    studentQuizAnswerService.updateById(answer);
                }

                return ResponseEntity.ok(aiResult);
            } else {
                return ResponseEntity.badRequest().body(aiResult);
            }
        } catch (Exception e) {
            System.out.println("AI评分请求异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "AI评分失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量AI评分
     * 功能：对整个测验的所有主观题进行批量AI评分
     */
    @PostMapping("/batchGradeByAI/{quizId}")
    public ResponseEntity<?> batchGradeByAI(@PathVariable("quizId") Integer quizId) {
        System.out.println("批量AI评分请求: quizId=" + quizId);
        try {
            Map<String, Object> result = aiService.batchGradeByAI(quizId);
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            System.out.println("批量AI评分请求异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量AI评分失败: " + e.getMessage()
            ));
        }
    }
}
