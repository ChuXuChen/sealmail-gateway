package com.auggie.student_server.controller;

import com.auggie.student_server.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计接口控制器
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 获取成绩分布统计
     */
    @GetMapping("/grade/distribution")
    public List<Map<String, Object>> getGradeDistribution(
            @RequestParam(required = false) Integer ctid,
            @RequestParam(required = false) Integer tid,
            @RequestParam(required = false) String term) {
        return statisticsService.getGradeDistribution(ctid, tid, term);
    }

    /**
     * 获取分数段统计
     */
    @GetMapping("/grade/score-segment")
    public List<Map<String, Object>> getScoreSegment(
            @RequestParam(required = false) Integer ctid,
            @RequestParam(required = false) Integer tid,
            @RequestParam(required = false) String term) {
        return statisticsService.getScoreSegment(ctid, tid, term);
    }

    /**
     * 获取课程平均分排行
     */
    @GetMapping("/course/average-rank")
    public List<Map<String, Object>> getCourseAverageRank(
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "10") Integer limit) {
        return statisticsService.getCourseAverageRank(term, limit);
    }

    /**
     * 获取教师授课统计
     */
    @GetMapping("/teacher/course-stats")
    public List<Map<String, Object>> getTeacherCourseStats(
            @RequestParam Integer tid,
            @RequestParam(required = false) String term) {
        return statisticsService.getTeacherCourseStats(tid, term);
    }

    /**
     * 获取测验完成情况统计
     */
    @GetMapping("/quiz/completion")
    public List<Map<String, Object>> getQuizCompletion(
            @RequestParam(required = false) Integer tid,
            @RequestParam(required = false) Integer ctid) {
        return statisticsService.getQuizCompletion(tid, ctid);
    }

    /**
     * 获取学生人数按专业分布
     */
    @GetMapping("/student/major-distribution")
    public List<Map<String, Object>> getStudentMajorDistribution() {
        return statisticsService.getStudentMajorDistribution();
    }

    /**
     * 获取系统概览统计
     */
    @GetMapping("/systemOverview")
    public Map<String, Object> getSystemOverview() {
        return statisticsService.getSystemOverview();
    }
}
