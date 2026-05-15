package com.auggie.student_server.controller;

import com.auggie.student_server.entity.Quiz;
import com.auggie.student_server.entity.SysMessage;
import com.auggie.student_server.service.QuizService;
import com.auggie.student_server.service.AIService;
import com.auggie.student_server.service.SysMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizController 测验控制器
 * @Version 1.0.0
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/quiz")
public class QuizController {
    @Autowired
    private QuizService quizService;

    @GetMapping("/findAll")
    public List<Quiz> findAll() {
        return quizService.findAll();
    }

    @GetMapping("/findById/{quizId}")
    public ResponseEntity<?> findById(@PathVariable("quizId") Integer quizId) {
        Quiz quiz = quizService.findById(quizId);
        if (quiz != null) {
            return ResponseEntity.ok(quiz);
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "测验不存在"
            ));
        }
    }

    @PostMapping("/findBySearch")
    public List<Quiz> findBySearch(@RequestBody Map<String, String> map) {
        Integer quizId = null;
        Integer ctid = null;
        String quizTitle = null;
        String quizDescription = null;
        Integer fuzzy = null;

        if (map.containsKey("quizId")) {
            try {
                quizId = Integer.parseInt(map.get("quizId"));
            } catch (Exception e) {
            }
        }
        if (map.containsKey("ctid")) {
            try {
                ctid = Integer.parseInt(map.get("ctid"));
            } catch (Exception e) {
            }
        }
        if (map.containsKey("quizTitle")) {
            quizTitle = map.get("quizTitle");
        }
        if (map.containsKey("quizDescription")) {
            quizDescription = map.get("quizDescription");
        }
        if (map.containsKey("fuzzy")) {
            fuzzy = map.get("fuzzy").equals("true") ? 1 : 0;
        }

        System.out.println("查询测验: " + quizId + ", " + ctid + ", " + quizTitle + ", " + quizDescription + ", " + fuzzy);
        return quizService.findBySearch(quizId, ctid, quizTitle, quizDescription, fuzzy);
    }

    @GetMapping("/findByCtid/{ctid}")
    public List<Quiz> findByCtid(@PathVariable("ctid") Integer ctid) {
        System.out.println("按课程查询测验: " + ctid);
        return quizService.findByCtid(ctid);
    }

    @GetMapping("/findByTeacher/{tid}")
    public List<Quiz> findByTeacher(@PathVariable("tid") Integer tid) {
        System.out.println("按教师查询测验: " + tid);
        return quizService.findByTeacher(tid);
    }

    @GetMapping("/findByStudent/{sid}")
    public List<Quiz> findByStudent(@PathVariable("sid") Integer sid) {
        System.out.println("按学生查询测验: " + sid);
        return quizService.findByStudent(sid);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveQuiz(@RequestBody Quiz quiz) {
        try {
            System.out.println("保存测验接收的数据: " + quiz);
            System.out.println("startTime类型: " + (quiz.getStartTime() != null ? quiz.getStartTime().getClass() : "null"));
            System.out.println("endTime类型: " + (quiz.getEndTime() != null ? quiz.getEndTime().getClass() : "null"));

            // 新创建的测验默认状态为未发布
            if (quiz.getStatus() == null) {
                quiz.setStatus(0);
            }

            boolean success = quizService.save(quiz);
            if (success) {
                System.out.println("保存成功，返回数据: " + quiz);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验创建成功",
                        "quizId", quiz.getQuizId()
                ));
            } else {
                System.out.println("保存失败");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验创建失败"
                ));
            }
        } catch (Exception e) {
            System.out.println("保存测验异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "创建失败: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateQuiz(@RequestBody Quiz quiz) {
        try {
            System.out.println("更新测验接收的数据: " + quiz);
            System.out.println("startTime类型: " + (quiz.getStartTime() != null ? quiz.getStartTime().getClass() : "null"));
            System.out.println("endTime类型: " + (quiz.getEndTime() != null ? quiz.getEndTime().getClass() : "null"));

            boolean success = quizService.updateById(quiz);
            if (success) {
                System.out.println("更新成功，返回数据: " + quiz);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验更新成功"
                ));
            } else {
                System.out.println("更新失败");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验更新失败"
                ));
            }
        } catch (Exception e) {
            System.out.println("更新测验异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "更新失败: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/delete/{quizId}")
    public ResponseEntity<?> deleteQuiz(@PathVariable("quizId") Integer quizId) {
        try {
            boolean success = quizService.deleteById(quizId);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验删除成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "删除失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 发布测验
     * 将测验状态改为已发布（status=1）
     */
    @PostMapping("/publish/{quizId}")
    public ResponseEntity<?> publishQuiz(@PathVariable("quizId") Integer quizId) {
        System.out.println("发布测验请求: " + quizId);
        try {
            Quiz quiz = quizService.findById(quizId);
            if (quiz == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验不存在"
                ));
            }

            quiz.setStatus(1); // 设置为已发布
            boolean success = quizService.updateById(quiz);
            if (success) {
                // 发送消息通知课程学生
                SysMessage message = new SysMessage();
                message.setType(1); // 新课程测验发布
                message.setTitle("新测验发布通知");
                message.setContent("您有新的测验可以参加：【" + quiz.getQuizTitle() + "】，请及时完成！");
                message.setSenderId(0); // 系统发送
                message.setSenderType(0); // 系统类型
                message.setTargetType(2); // 指定课程学生
                message.setTargetId(quiz.getCtid()); // 课程ctid
                message.setRelatedId(quiz.getQuizId()); // 关联测验ID
                sysMessageService.saveAndDistribute(message);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验发布成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验发布失败"
                ));
            }
        } catch (Exception e) {
            System.out.println("发布测验异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "发布失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 取消发布测验
     * 将测验状态改为未发布（status=0）
     */
    @PostMapping("/unpublish/{quizId}")
    public ResponseEntity<?> unpublishQuiz(@PathVariable("quizId") Integer quizId) {
        System.out.println("取消发布测验请求: " + quizId);
        try {
            Quiz quiz = quizService.findById(quizId);
            if (quiz == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "测验不存在"
                ));
            }

            quiz.setStatus(0); // 设置为未发布
            boolean success = quizService.updateById(quiz);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "测验已取消发布"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "取消发布失败"
                ));
            }
        } catch (Exception e) {
            System.out.println("取消发布测验异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "取消发布失败: " + e.getMessage()
            ));
        }
    }

    // ==================== AI协助组卷接口 ====================

    @Autowired
    private AIService aiService;

    @Autowired
    private SysMessageService sysMessageService;

    /**
     * AI协助组卷
     * 功能：根据教师选定的资源和题目结构，调用AI生成符合要求的测验
     */
    @PostMapping("/generateByAI")
    public ResponseEntity<?> generateQuizByAI(@RequestBody Map<String, Object> request) {
        System.out.println("AI组卷请求: " + request);
        try {
            Integer ctid = (Integer) request.get("ctid");
            List<Integer> selectedResources = (List<Integer>) request.get("selectedResources");
            String questionStructure = (String) request.get("questionStructure");
            Integer difficultyLevel = (Integer) request.get("difficultyLevel");

            // 调用AI服务生成测验
            String taskId = aiService.generateQuizByAI(ctid, selectedResources, questionStructure, difficultyLevel);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "AI组卷任务已开始",
                    "taskId", taskId
            ));
        } catch (Exception e) {
            System.out.println("AI组卷请求异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "AI组卷失败: " + e.getMessage(),
                    "taskId", null
            ));
        }
    }

    /**
     * 获取AI组卷状态
     * 功能：查询AI组卷任务的执行状态和进度
     */
    @GetMapping("/generationStatus/{taskId}")
    public ResponseEntity<?> getGenerationStatus(@PathVariable("taskId") String taskId) {
        System.out.println("查询AI组卷状态: " + taskId);
        try {
            AIService.AIGenerationTask task = aiService.getGenerationStatus(taskId);
            if (task == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "任务不存在"
                ));
            }

            Map<String, Object> result = Map.of(
                    "success", true,
                    "taskId", task.getTaskId(),
                    "status", task.getStatus(),
                    "progress", task.getProgress()
            );

            if ("completed".equals(task.getStatus())) {
                result = Map.of(
                        "success", true,
                        "taskId", task.getTaskId(),
                        "status", task.getStatus(),
                        "progress", task.getProgress(),
                        "generatedQuiz", task.getGeneratedQuiz(),
                        "generatedQuestions", task.getGeneratedQuestions()
                );
            } else if ("failed".equals(task.getStatus())) {
                result = Map.of(
                        "success", false,
                        "taskId", task.getTaskId(),
                        "status", task.getStatus(),
                        "progress", task.getProgress(),
                        "error", task.getError()
                );
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.out.println("查询AI组卷状态异常: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "查询状态失败: " + e.getMessage()
            ));
        }
    }
}
