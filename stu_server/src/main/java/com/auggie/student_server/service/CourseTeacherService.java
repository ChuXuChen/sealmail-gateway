package com.auggie.student_server.service;

import com.auggie.student_server.entity.Course;
import com.auggie.student_server.entity.CourseTeacher;
import com.auggie.student_server.entity.CourseTeacherInfo;
import com.auggie.student_server.mapper.CourseTeacherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2022/2/10 16:50
 * @Description: CourseTeacherService
 * @Version 1.0.0
 */

import com.auggie.student_server.entity.StudentCourseTeacher;
import com.auggie.student_server.mapper.StudentCourseTeacherMapper;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseTeacherService {
    @Autowired
    private CourseTeacherMapper courseTeacherMapper;

    @Autowired
    private StudentCourseTeacherMapper studentCourseTeacherMapper;

    public boolean insertCourseTeacher(Integer cid, Integer tid, String term) {
        return courseTeacherMapper.insertCourseTeacher(cid, tid, term);
    }

    public List<java.util.Map<String, Object>> findMyCourse(Integer tid, String term) {
        return courseTeacherMapper.findMyCourse(tid, term);
    }

    public List<CourseTeacherInfo> findCourseTeacherInfo(Map<String, String> map) {
        Integer tid = null, cid = null;
        Integer tFuzzy = null, cFuzzy = null;
        String tname = null, cname = null;
        if (map.containsKey("tid")) {
            try {
                tid = Integer.parseInt(map.get("tid"));
            }
            catch (Exception e) {
            }
        }
        if (map.containsKey("cid")) {
            try {
                cid = Integer.parseInt(map.get("cid"));
            }
            catch (Exception e) {
            }
        }
        if (map.containsKey("tname")) {
            tname = map.get("tname");
        }
        if (map.containsKey("cname")) {
            cname = map.get("cname");
        }
        if (map.containsKey("tFuzzy")) {
            tFuzzy = (map.get("tFuzzy").equals("true")) ? 1 : 0;
        }
        if (map.containsKey("cFuzzy")) {
            cFuzzy = (map.get("cFuzzy").equals("true")) ? 1 : 0;
        }
        System.out.println("ct 模糊查询" + map);
        System.out.println(courseTeacherMapper.findCourseTeacherInfo(tid, tname, tFuzzy, cid, cname, cFuzzy));
        return courseTeacherMapper.findCourseTeacherInfo(tid, tname, tFuzzy, cid, cname, cFuzzy);
    }

    public List<CourseTeacher> findBySearch(Integer cid, Integer tid, String term) {
        return courseTeacherMapper.findBySearch(cid, tid, term);
    }

    public List<CourseTeacher> findBySearch(Map<String, String> map) {
        Integer cid = null;
        Integer tid = null;
        String  term = null;

        if (map.containsKey("term")) {
            term = map.get("term");
        }

        if (map.containsKey("tid")) {
            try {
                tid = Integer.parseInt(map.get("tid"));
            }
            catch (Exception e) {
            }
        }

        if (map.containsKey("cid")) {
            try {
                cid = Integer.parseInt(map.get("cid"));
            }
            catch (Exception e) {
            }
        }
        System.out.println("开课表查询：" + map);
        return courseTeacherMapper.findBySearch(cid, tid, term);
    }

    @Transactional
    public boolean deleteById(CourseTeacher courseTeacher) {
        System.out.println("准备删除 SCT: cid=" + courseTeacher.getCid() + ", tid=" + courseTeacher.getTid() + ", term=" + courseTeacher.getTerm());
        try {
            // 删除关联的选课记录，即使没有记录也不算失败
            studentCourseTeacherMapper.deleteByCT(
                courseTeacher.getCid(),
                courseTeacher.getTid(),
                courseTeacher.getTerm()
            );
            System.out.println("删除 SCT 完成（可能删除了0条或多条）");
        } catch (Exception e) {
            System.out.println("删除 SCT 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        // 2. 删除CT记录
        System.out.println("准备删除 CT: cid=" + courseTeacher.getCid() + ", tid=" + courseTeacher.getTid() + ", term=" + courseTeacher.getTerm());
        boolean ctDeleted = courseTeacherMapper.deleteById(courseTeacher);
        System.out.println("删除 CT 结果: " + ctDeleted);

        // 3. 只要CT删除成功就算成功
        return ctDeleted;
    }

    public List<CourseTeacher> findByCtid(Integer ctid) {
        return courseTeacherMapper.findByCtid(ctid);
    }

    @Transactional
    public boolean updateById(CourseTeacher courseTeacher) {
        // 1. 验证数据完整性
        if (courseTeacher.getCtid() == null || courseTeacher.getCid() == null ||
            courseTeacher.getTid() == null || courseTeacher.getTerm() == null) {
            System.out.println("更新失败：数据不完整");
            return false;
        }

        // 2. 检查是否存在相同的课程-教师-学期组合（排除当前记录）
        List<CourseTeacher> existing = courseTeacherMapper.findBySearch(
            courseTeacher.getCid(),
            courseTeacher.getTid(),
            courseTeacher.getTerm()
        );

        // 如果存在其他记录（不是当前要修改的记录），则不允许修改
        for (CourseTeacher ct : existing) {
            if (!ct.getCtid().equals(courseTeacher.getCtid())) {
                System.out.println("更新失败：存在重复的开课记录");
                return false; // 存在重复记录
            }
        }

        // 3. 获取原始记录（用于更新学生选课记录）
        List<CourseTeacher> originalList = courseTeacherMapper.findByCtid(courseTeacher.getCtid());
        if (originalList.isEmpty()) {
            System.out.println("更新失败：找不到原始记录，ctid=" + courseTeacher.getCtid());
            return false;
        }
        CourseTeacher original = originalList.get(0);

        // 4. 执行开课记录更新
        boolean updated = courseTeacherMapper.updateById(courseTeacher);
        if (!updated) {
            System.out.println("更新失败：数据库更新失败");
            return false;
        }

        // 5. 如果cid、tid或term发生变化，更新学生选课记录
        if (!original.getCid().equals(courseTeacher.getCid()) ||
            !original.getTid().equals(courseTeacher.getTid()) ||
            !original.getTerm().equals(courseTeacher.getTerm())) {

            System.out.println("检测到开课信息变化，更新学生选课记录");
            System.out.println("原始: cid=" + original.getCid() + ", tid=" + original.getTid() + ", term=" + original.getTerm());
            System.out.println("新的: cid=" + courseTeacher.getCid() + ", tid=" + courseTeacher.getTid() + ", term=" + courseTeacher.getTerm());

            boolean sctUpdated = studentCourseTeacherMapper.updateByCT(
                original.getCid(), original.getTid(), original.getTerm(),
                courseTeacher.getCid(), courseTeacher.getTid(), courseTeacher.getTerm()
            );

            if (!sctUpdated) {
                System.out.println("警告：学生选课记录更新失败，但开课记录已更新");
                // 这里不返回false，因为开课记录已经更新成功
            }
        }

        System.out.println("开课记录更新成功: ctid=" + courseTeacher.getCtid());
        return true;
    }
}
