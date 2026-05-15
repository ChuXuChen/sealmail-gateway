package com.auggie.student_server.controller;

import com.auggie.student_server.entity.Student;
import com.auggie.student_server.entity.SysUserMessage;
import com.auggie.student_server.entity.Teacher;
import com.auggie.student_server.service.StudentService;
import com.auggie.student_server.service.SysMessageService;
import com.auggie.student_server.service.SysUserMessageService;
import com.auggie.student_server.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/message")
@CrossOrigin("*")
public class SysMessageController {

    @Autowired
    private SysUserMessageService sysUserMessageService;

    @Autowired
    private SysMessageService sysMessageService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private TeacherService teacherService;

    /**
     * 查询当前用户未读消息数量
     */
    @GetMapping("/unread/count")
    public Map<String, Object> getUnreadCount(@RequestParam Integer userId,
                                             @RequestParam Integer userType) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = sysUserMessageService.countUnread(userId, userType);
            result.put("success", true);
            result.put("count", count);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 查询当前用户消息列表
     */
    @GetMapping("/list")
    public Map<String, Object> getMessageList(@RequestParam Integer userId,
                                             @RequestParam Integer userType,
                                             @RequestParam(required = false) Integer isRead) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<SysUserMessage> list = sysUserMessageService.findByUserId(userId, userType, isRead);
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 标记单条消息为已读
     */
    @PostMapping("/markRead")
    public Map<String, Object> markAsRead(@RequestBody Map<String, Integer> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer id = params.get("id");
            boolean success = sysUserMessageService.markAsRead(id);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 标记所有消息为已读
     */
    @PostMapping("/markAllRead")
    public Map<String, Object> markAllAsRead(@RequestBody Map<String, Integer> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer userId = params.get("userId");
            Integer userType = params.get("userType");
            boolean success = sysUserMessageService.markAllAsRead(userId, userType);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 管理员发送全体消息
     */
    @PostMapping("/admin/sendAll")
    public Map<String, Object> sendAllMessage(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String title = params.get("title");
            String content = params.get("content");
            boolean success = sysMessageService.sendAllMessage(title, content);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 教师向课程学生发送消息
     */
    @PostMapping("/sendToCourse")
    public Map<String, Object> sendMessageToCourse(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Object ctidObj = params.get("ctid");
            Object tidObj = params.get("tid");

            if (ctidObj == null || tidObj == null) {
                result.put("success", false);
                result.put("message", "缺少必要参数");
                return result;
            }

            Integer ctid = Integer.valueOf(ctidObj.toString());
            String title = (String) params.get("title");
            String content = (String) params.get("content");
            Integer tid = Integer.valueOf(tidObj.toString());

            boolean success = sysMessageService.sendMessageToCourse(ctid, title, content, tid);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 发送私信
     */
    @PostMapping("/sendPrivate")
    public Map<String, Object> sendPrivateMessage(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Object senderIdObj = params.get("senderId");
            Object senderTypeObj = params.get("senderType");
            Object targetIdObj = params.get("targetId");
            Object targetUserTypeObj = params.get("targetUserType");

            if (senderIdObj == null || senderTypeObj == null || targetIdObj == null || targetUserTypeObj == null) {
                result.put("success", false);
                result.put("message", "缺少必要参数");
                return result;
            }

            Integer senderId = Integer.valueOf(senderIdObj.toString());
            Integer senderType = Integer.valueOf(senderTypeObj.toString());
            Integer targetId = Integer.valueOf(targetIdObj.toString());
            Integer targetUserType = Integer.valueOf(targetUserTypeObj.toString());
            String title = (String) params.get("title");
            String content = (String) params.get("content");

            boolean success = sysMessageService.sendPrivateMessage(senderId, senderType, targetId, targetUserType, title, content);
            result.put("success", success);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 搜索用户（学生和教师）
     */
    @GetMapping("/searchUser")
    public Map<String, Object> searchUser(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> userList = new ArrayList<>();
            boolean isNumber = keyword.matches("\\d+");

            // 搜索学生：ID和名字同时搜索
            List<Student> studentList = new ArrayList<>();
            if (isNumber) {
                // 数字的话同时搜学生ID和名字
                Integer id = Integer.parseInt(keyword);
                List<Student> sidList = studentService.findBySearch(id, null, 0); // 精确匹配ID
                List<Student> snameList = studentService.findBySearch(null, keyword, 1); // 模糊匹配名字
                // 合并去重
                Set<Integer> sidSet = new HashSet<>();
                for (Student s : sidList) {
                    if (!sidSet.contains(s.getSid())) {
                        sidSet.add(s.getSid());
                        studentList.add(s);
                    }
                }
                for (Student s : snameList) {
                    if (!sidSet.contains(s.getSid())) {
                        sidSet.add(s.getSid());
                        studentList.add(s);
                    }
                }
            } else {
                // 非数字只搜名字
                studentList = studentService.findBySearch(null, keyword, 1);
            }
            // 添加学生到结果
            for (Student student : studentList) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", student.getSid());
                user.put("name", student.getSname());
                user.put("type", 1); // 学生类型（与系统统一：1=学生，2=教师，3=管理员）
                userList.add(user);
            }

            // 搜索教师：ID和名字同时搜索
            List<Teacher> teacherList = new ArrayList<>();
            // 先搜名字
            Map<String, String> teacherNameQuery = new HashMap<>();
            teacherNameQuery.put("tname", keyword);
            teacherNameQuery.put("fuzzy", "true");
            List<Teacher> tnameList = teacherService.findBySearch(teacherNameQuery);
            teacherList.addAll(tnameList);
            // 如果是数字，再搜教师ID
            if (isNumber) {
                Map<String, String> teacherIdQuery = new HashMap<>();
                teacherIdQuery.put("tid", keyword);
                teacherIdQuery.put("fuzzy", "false"); // 精确匹配ID
                List<Teacher> tidList = teacherService.findBySearch(teacherIdQuery);
                // 合并去重
                Set<Integer> tidSet = new HashSet<>();
                for (Teacher t : teacherList) {
                    tidSet.add(t.getTid());
                }
                for (Teacher t : tidList) {
                    if (!tidSet.contains(t.getTid())) {
                        teacherList.add(t);
                    }
                }
            }
            // 添加教师到结果
            for (Teacher teacher : teacherList) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", teacher.getTid());
                user.put("name", teacher.getTname());
                // 管理员类型为3，普通教师为2
                user.put("type", "admin".equals(teacher.getTname()) ? 3 : 2);
                userList.add(user);
            }

            result.put("success", true);
            result.put("data", userList);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
