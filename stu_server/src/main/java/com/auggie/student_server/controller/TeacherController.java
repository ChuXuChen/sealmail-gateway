package com.auggie.student_server.controller;

import com.auggie.student_server.annotation.RequiresRoles;
import com.auggie.student_server.entity.Student;
import com.auggie.student_server.entity.Teacher;
import com.auggie.student_server.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2022/2/9 11:02
 * @Description: TeacherController
 * @Version 1.0.0
 */

@RestController
@CrossOrigin("*")
@RequestMapping("/teacher")
public class TeacherController {
    @Autowired
    private TeacherService teacherService;

    @PostMapping("/addTeacher")
    @RequiresRoles("3")
    public boolean addTeacher(@RequestBody Teacher teacher) {
        return teacherService.save(teacher);
    }

    @PostMapping("/login")
    public boolean login(@RequestBody Teacher teacher) {
        System.out.println("正在验证教师登陆 " + teacher);
        Teacher t = teacherService.findById(teacher.getTid());
        System.out.println("数据库教师信息" + t);
        if (t == null || !t.getPassword().equals(teacher.getPassword())) {
            return false;
        }
        else {
            return true;
        }
    }

    @GetMapping("/findById/{tid}")
    @RequiresRoles({"1", "2", "3"})
    public Teacher findById(@PathVariable("tid") Integer tid) {
        System.out.println("正在查询教师信息 By id " + tid);
        return teacherService.findById(tid);
    }

    @PostMapping("/findBySearch")
    @RequiresRoles("3")
    public List<Teacher> findBySearch(@RequestBody Map<String, String> map) {
        return teacherService.findBySearch(map);
    }

    @GetMapping("/deleteById/{tid}")
    @RequiresRoles("3")
    public boolean deleteById(@PathVariable("tid") int tid) {
        System.out.println("正在删除教师 tid：" + tid);
        return teacherService.deleteById(tid);
    }

    @PostMapping("/updateTeacher")
    @RequiresRoles({"2", "3"})
    public boolean updateTeacher(@RequestBody Teacher teacher) {
        System.out.println("更新 " + teacher);
        return teacherService.updateById(teacher);
    }

    @GetMapping("/resetPassword/{tid}")
    @RequiresRoles("3")
    public boolean resetPassword(@PathVariable int tid) {
        System.out.println("重置教师密码 tid：" + tid);
        Teacher teacher = teacherService.findById(tid);
        if (teacher == null || "admin".equals(teacher.getTname())) {
            return false;
        }
        teacher.setPassword("123");
        // 执行更新，只要没有异常就返回成功
        try {
            teacherService.updateById(teacher);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
