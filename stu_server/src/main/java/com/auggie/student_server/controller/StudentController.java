package com.auggie.student_server.controller;

import com.auggie.student_server.annotation.RequiresRoles;
import com.auggie.student_server.entity.Student;
import com.auggie.student_server.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Auther: auggie
 * @Date: 2022/2/8 17:37
 * @Description: StudentController
 * @Version 1.0.0
 */

@RestController
@CrossOrigin("*")
@RequestMapping("/student")
public class StudentController {
    @Autowired
    private StudentService studentService;

    @PostMapping("/addStudent")
    @RequiresRoles("3")
    public boolean addStudent(@RequestBody Student student) {
        System.out.println("正在保存学生对象" + student);
        return studentService.save(student);
    }

    @PostMapping("/login")
    public boolean login(@RequestBody Student student) {
        System.out.println("正在验证学生登陆 " + student);
        Student s = studentService.findById(student.getSid());
        if (s == null || !s.getPassword().equals(student.getPassword())) {
            return false;
        }
        else {
            return true;
        }
    }

    @PostMapping("/findBySearch")
    @RequiresRoles({"2", "3"})
    public List<Student> findBySearch(@RequestBody Map<String, String> map) {
        Integer sid = null;
        String sname = null;
        Integer fuzzy = null;
        if (map.containsKey("sid")) {
            try {
                sid = Integer.parseInt(map.get("sid"));
            }
            catch (Exception e) {
            }
        }
        if (map.containsKey("sname")) {
            sname = map.get("sname");
        }
        if (map.containsKey("fuzzy")) {
            fuzzy = map.get("fuzzy").equals("true") ? 1 : 0;
        }
        System.out.println(map);
        System.out.println("查询类型：" + sid  + ", " + sname + ", " + fuzzy);
        return studentService.findBySearch(sid, sname, fuzzy);
    }

    @GetMapping("/findById/{sid}")
    @RequiresRoles({"1", "2", "3"})
    public Student findById(@PathVariable("sid") Integer sid) {
        System.out.println("正在查询学生信息 By id " + sid);
        return studentService.findById(sid);
    }

    @GetMapping("/findByPage/{page}/{size}")
    @RequiresRoles("3")
    public List<Student> findByPage(@PathVariable("page") int page, @PathVariable("size") int size) {
        System.out.println("查询学生列表分页 " + page + " " + size);
        return studentService.findByPage(page, size);
    }

    @GetMapping("/getLength")
    @RequiresRoles("3")
    public Integer getLength() {
        return studentService.getLength();
    }

    @GetMapping("/deleteById/{sid}")
    @RequiresRoles("3")
    public boolean deleteById(@PathVariable("sid") int sid) {
        System.out.println("正在删除学生 sid：" + sid);
        return studentService.deleteById(sid);
    }

    @PostMapping("/updateStudent")
    @RequiresRoles({"1", "3"})
    public boolean updateStudent(@RequestBody Student student) {
        System.out.println("更新 " + student);
        return studentService.updateById(student);
    }

    @GetMapping("/resetPassword/{sid}")
    @RequiresRoles("3")
    public boolean resetPassword(@PathVariable("sid") int sid) {
        System.out.println("重置学生密码 sid：" + sid);
        Student student = studentService.findById(sid);
        if (student == null) {
            return false;
        }
        student.setPassword("123");
        // 执行更新，只要没有异常就返回成功
        try {
            studentService.updateById(student);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
