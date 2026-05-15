package com.auggie.student_server.service;

import com.auggie.student_server.entity.SCTInfo;
import com.auggie.student_server.service.SCTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计业务逻辑服务
 */
@Service
public class StatisticsService {

    @Autowired
    private SCTService sctService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private CourseTeacherService courseTeacherService;

    /**
     * 获取成绩分布统计
     */
    public List<Map<String, Object>> getGradeDistribution(Integer ctid, Integer tid, String term) {
        Map<String, String> params = new HashMap<>();
        if (ctid != null) params.put("ctid", ctid.toString());
        if (tid != null) params.put("tid", tid.toString());
        if (term != null) params.put("term", term);

        List<SCTInfo> sctList = sctService.findBySearch(params);

        // 统计不同分数段的人数
        int[] ranges = {0, 60, 70, 80, 90, 101};
        String[] labels = {"不及格", "60-69", "70-79", "80-89", "90-100"};
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < labels.length; i++) {
            int start = ranges[i];
            int end = ranges[i + 1];
            long count = sctList.stream()
                    .filter(sct -> sct.getGrade() != null && sct.getGrade() >= start && sct.getGrade() < end)
                    .count();
            Map<String, Object> item = new HashMap<>();
            item.put("name", labels[i]);
            item.put("value", count);
            result.add(item);
        }

        return result;
    }

    /**
     * 获取分数段统计
     */
    public List<Map<String, Object>> getScoreSegment(Integer ctid, Integer tid, String term) {
        return getGradeDistribution(ctid, tid, term);
    }

    /**
     * 获取课程平均分排行
     */
    public List<Map<String, Object>> getCourseAverageRank(String term, Integer limit) {
        Map<String, String> params = new HashMap<>();
        if (term != null) params.put("term", term);

        List<SCTInfo> sctList = sctService.findBySearch(params);

        // 按课程分组计算平均分
        Map<Integer, List<SCTInfo>> courseGroup = sctList.stream()
                .collect(Collectors.groupingBy(SCTInfo::getCid));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Integer, List<SCTInfo>> entry : courseGroup.entrySet()) {
            Integer cid = entry.getKey();
            List<SCTInfo> scores = entry.getValue();
            if (scores.isEmpty()) continue;

            double average = scores.stream()
                    .filter(sct -> sct.getGrade() != null)
                    .mapToDouble(SCTInfo::getGrade)
                    .average()
                    .orElse(0);

            Map<String, Object> item = new HashMap<>();
            item.put("courseId", cid);
            item.put("courseName", scores.get(0).getCname());
            item.put("averageScore", BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue());
            item.put("studentCount", scores.size());
            result.add(item);
        }

        // 按平均分排序
        result.sort((a, b) -> Double.compare((Double) b.get("averageScore"), (Double) a.get("averageScore")));

        // 限制返回数量
        if (limit != null && limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }

        return result;
    }

    /**
     * 获取教师授课统计
     */
    public List<Map<String, Object>> getTeacherCourseStats(Integer tid, String term) {
        Map<String, String> params = new HashMap<>();
        params.put("tid", tid.toString());
        if (term != null) params.put("term", term);

        List<SCTInfo> sctList = sctService.findBySearch(params);

        // 按课程分组
        Map<Integer, List<SCTInfo>> courseGroup = sctList.stream()
                .collect(Collectors.groupingBy(SCTInfo::getCid));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Integer, List<SCTInfo>> entry : courseGroup.entrySet()) {
            Integer cid = entry.getKey();
            List<SCTInfo> scores = entry.getValue();
            if (scores.isEmpty()) continue;

            double average = scores.stream()
                    .filter(sct -> sct.getGrade() != null)
                    .mapToDouble(SCTInfo::getGrade)
                    .average()
                    .orElse(0);

            long passCount = scores.stream()
                    .filter(sct -> sct.getGrade() != null && sct.getGrade() >= 60)
                    .count();

            double passRate = scores.size() > 0 ? (double) passCount / scores.size() * 100 : 0;

            Map<String, Object> item = new HashMap<>();
            item.put("courseId", cid);
            item.put("courseName", scores.get(0).getCname());
            item.put("studentCount", scores.size());
            item.put("averageScore", BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue());
            item.put("passRate", BigDecimal.valueOf(passRate).setScale(2, RoundingMode.HALF_UP).doubleValue());
            result.add(item);
        }

        return result;
    }

    /**
     * 获取测验完成情况统计（暂返回模拟数据）
     */
    public List<Map<String, Object>> getQuizCompletion(Integer tid, Integer ctid) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 可后续扩展测验相关统计
        return result;
    }

    /**
     * 获取学生人数按专业分布（暂返回模拟数据）
     */
    public List<Map<String, Object>> getStudentMajorDistribution() {
        List<Map<String, Object>> result = new ArrayList<>();
        // 可后续扩展专业相关统计
        return result;
    }

    /**
     * 获取系统概览统计
     */
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> result = new HashMap<>();

        // 统计基础数据
        long studentCount = studentService.getLength();
        long teacherCount = teacherService.getLength();
        long courseCount = courseTeacherService.findBySearch(null, null, null).size();

        // 统计所有选课记录数量
        List<SCTInfo> allSct = sctService.findBySearch(new HashMap<>());
        long totalSelection = allSct.size();

        // 统计有成绩的记录
        long gradedCount = allSct.stream().filter(sct -> sct.getGrade() != null).count();

        // 计算整体平均分
        double totalAverage = allSct.stream()
                .filter(sct -> sct.getGrade() != null)
                .mapToDouble(SCTInfo::getGrade)
                .average()
                .orElse(0.0);

        // 统计不同分数段人数
        long excellentCount = allSct.stream().filter(sct -> sct.getGrade() != null && sct.getGrade() >= 90).count();
        long goodCount = allSct.stream().filter(sct -> sct.getGrade() != null && sct.getGrade() >= 80 && sct.getGrade() < 90).count();
        long passCount = allSct.stream().filter(sct -> sct.getGrade() != null && sct.getGrade() >= 60 && sct.getGrade() < 80).count();
        long failCount = allSct.stream().filter(sct -> sct.getGrade() != null && sct.getGrade() < 60).count();

        // 返回前端需要的字段
        result.put("studentCount", studentCount);
        result.put("teacherCount", teacherCount);
        result.put("courseCount", courseCount);
        result.put("courseSelectionCount", totalSelection); // 先返回总选课人次，后续可以按当前学期筛选
        result.put("totalSelection", totalSelection);
        result.put("gradedCount", gradedCount);
        result.put("totalAverage", BigDecimal.valueOf(totalAverage).setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.put("excellentCount", excellentCount);
        result.put("goodCount", goodCount);
        result.put("passCount", passCount);
        result.put("failCount", failCount);

        return result;
    }
}
