package com.auggie.student_server.controller;

import com.auggie.student_server.entity.StudentQuizScore;
import com.auggie.student_server.service.StudentQuizScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizScoreController 学生测验成绩控制器
 * @Version 1.0.0
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/quizScore")
public class StudentQuizScoreController {
    @Autowired
    private StudentQuizScoreService studentQuizScoreService;

    @GetMapping("/findAll")
    public List<StudentQuizScore> findAll() {
        return studentQuizScoreService.findAll();
    }

    @GetMapping("/findById/{scoreId}")
    public ResponseEntity<?> findById(@PathVariable("scoreId") Integer scoreId) {
        StudentQuizScore score = studentQuizScoreService.findById(scoreId);
        if (score != null) {
            return ResponseEntity.ok(score);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "成绩记录不存在"
            ));
        }
    }

    @GetMapping("/findByQuizAndStudent/{quizId}/{sid}")
    public ResponseEntity<?> findByQuizAndStudent(
            @PathVariable("quizId") Integer quizId,
            @PathVariable("sid") Integer sid) {
        System.out.println("按测验和学生查询成绩: quizId=" + quizId + ", sid=" + sid);
        StudentQuizScore score = studentQuizScoreService.findByQuizAndStudent(quizId, sid);
        if (score != null) {
            // 确保 totalScore 有值，如果没有则使用 quizTotalScore
            if (score.getTotalScore() == null && score.getQuizTotalScore() != null) {
                score.setTotalScore(score.getQuizTotalScore());
            }
            System.out.println("返回成绩数据 - totalScore: " + score.getTotalScore() +
                             ", quizTotalScore: " + score.getQuizTotalScore() +
                             ", obtainedScore: " + score.getObtainedScore());
            return ResponseEntity.ok(score);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "成绩记录不存在"
            ));
        }
    }

    @GetMapping("/findByQuizId/{quizId}")
    public List<StudentQuizScore> findByQuizId(@PathVariable("quizId") Integer quizId) {
        System.out.println("按测验查询成绩: " + quizId);
        return studentQuizScoreService.findByQuizId(quizId);
    }

    @GetMapping("/findByStudentId/{sid}")
    public List<StudentQuizScore> findByStudentId(@PathVariable("sid") Integer sid) {
        System.out.println("按学生查询成绩: " + sid);
        return studentQuizScoreService.findByStudentId(sid);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveScore(@RequestBody StudentQuizScore score) {
        try {
            System.out.println("保存成绩: " + score);
            boolean success = studentQuizScoreService.save(score);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "成绩保存成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "成绩保存失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "保存失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateScore(@RequestBody StudentQuizScore score) {
        try {
            System.out.println("更新成绩: " + score);
            boolean success = studentQuizScoreService.updateById(score);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "成绩更新成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "成绩更新失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/delete/{scoreId}")
    public ResponseEntity<?> deleteScore(@PathVariable("scoreId") Integer scoreId) {
        try {
            boolean success = studentQuizScoreService.deleteById(scoreId);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "成绩记录删除成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "成绩记录删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/submit/{quizId}/{sid}")
    public ResponseEntity<?> submitQuiz(
            @PathVariable("quizId") Integer quizId,
            @PathVariable("sid") Integer sid) {
        try {
            System.out.println("提交测验: quizId=" + quizId + ", sid=" + sid);
            StudentQuizScore score = studentQuizScoreService.submitQuiz(quizId, sid);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "测验提交成功",
                    "data", score
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "提交失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/findAllStudentsWithScores/{quizId}")
    public ResponseEntity<?> findAllStudentsWithScores(@PathVariable("quizId") Integer quizId) {
        try {
            System.out.println("查询所有学生成绩: quizId=" + quizId);
            List<StudentQuizScore> scores = studentQuizScoreService.findAllStudentsWithScores(quizId);
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "查询失败: " + e.getMessage()
            ));
        }
    }
}
