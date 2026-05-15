package com.auggie.student_server.controller;

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
 * @Description: QuizQuestionController 测验题目控制器
 * @Version 1.0.0
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/quizQuestion")
public class QuizQuestionController {
    @Autowired
    private QuizQuestionService quizQuestionService;

    @GetMapping("/findAll")
    public List<QuizQuestion> findAll() {
        return quizQuestionService.findAll();
    }

    @GetMapping("/findById/{questionId}")
    public ResponseEntity<?> findById(@PathVariable("questionId") Integer questionId) {
        QuizQuestion question = quizQuestionService.findById(questionId);
        if (question != null) {
            return ResponseEntity.ok(question);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "题目不存在"
            ));
        }
    }

    @GetMapping("/findByQuizId/{quizId}")
    public List<QuizQuestion> findByQuizId(@PathVariable("quizId") Integer quizId) {
        System.out.println("按测验查询题目: " + quizId);
        return quizQuestionService.findByQuizId(quizId);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveQuestion(@RequestBody QuizQuestion question) {
        try {
            System.out.println("保存题目: " + question);
            boolean success = quizQuestionService.save(question);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "题目保存成功",
                        "questionId", question.getQuestionId()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "题目保存失败"
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
    public ResponseEntity<?> saveBatchQuestions(@RequestBody List<QuizQuestion> questions) {
        try {
            System.out.println("批量保存题目，数量: " + (questions != null ? questions.size() : 0));
            boolean success = quizQuestionService.saveBatch(questions);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "题目批量保存成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "题目批量保存失败"
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
    public ResponseEntity<?> updateQuestion(@RequestBody QuizQuestion question) {
        try {
            System.out.println("更新题目: " + question);
            boolean success = quizQuestionService.updateById(question);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "题目更新成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "题目更新失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/delete/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable("questionId") Integer questionId) {
        try {
            boolean success = quizQuestionService.deleteById(questionId);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "题目删除成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "题目删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/deleteByQuizId/{quizId}")
    public ResponseEntity<?> deleteByQuizId(@PathVariable("quizId") Integer quizId) {
        try {
            boolean success = quizQuestionService.deleteByQuizId(quizId);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验题目删除成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验题目删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }
}
