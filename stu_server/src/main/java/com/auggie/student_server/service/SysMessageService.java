package com.auggie.student_server.service;

import com.auggie.student_server.entity.Student;
import com.auggie.student_server.entity.Teacher;
import com.auggie.student_server.entity.SCTInfo;
import com.auggie.student_server.entity.SysMessage;
import com.auggie.student_server.entity.SysUserMessage;
import com.auggie.student_server.mapper.StudentMapper;
import com.auggie.student_server.mapper.TeacherMapper;
import com.auggie.student_server.mapper.StudentCourseTeacherMapper;
import com.auggie.student_server.mapper.SysMessageMapper;
import com.auggie.student_server.mapper.SysUserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysMessageService {

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SysUserMessageMapper sysUserMessageMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private StudentCourseTeacherMapper studentCourseTeacherMapper;

    /**
     * 保存消息并分发给目标用户
     */
    @Transactional
    public boolean saveAndDistribute(SysMessage message) {
        // 保存消息主记录
        boolean saveResult = sysMessageMapper.save(message);
        if (!saveResult) {
            return false;
        }

        Integer messageId = message.getMessageId();
        List<SysUserMessage> userMessageList = new ArrayList<>();

        // 根据目标类型分发消息
        switch (message.getTargetType()) {
            case 1: // 全体用户（学生、教师和管理员）
                // 查询所有学生
                List<Student> studentList = studentMapper.findAll();
                for (Student student : studentList) {
                    SysUserMessage userMessage = new SysUserMessage();
                    userMessage.setUserId(student.getSid());
                    userMessage.setUserType(1); // 学生类型
                    userMessage.setMessageId(messageId);
                    userMessageList.add(userMessage);
                }
                // 查询所有教师
                List<Teacher> teacherList = teacherMapper.findAll();
                for (Teacher teacher : teacherList) {
                    // 跳过管理员，管理员单独作为userType=3添加
                    if ("admin".equals(teacher.getTname())) {
                        continue;
                    }
                    SysUserMessage userMessage = new SysUserMessage();
                    userMessage.setUserId(teacher.getTid());
                    userMessage.setUserType(2); // 教师类型
                    userMessage.setMessageId(messageId);
                    userMessageList.add(userMessage);
                }
                // 查询并添加所有管理员
                List<Teacher> adminList = teacherMapper.findBySearch(null, "admin", 0);
                if (adminList != null && !adminList.isEmpty()) {
                    for (Teacher admin : adminList) {
                        SysUserMessage adminMessage = new SysUserMessage();
                        adminMessage.setUserId(admin.getTid());
                        adminMessage.setUserType(3); // 管理员类型
                        adminMessage.setMessageId(messageId);
                        userMessageList.add(adminMessage);
                    }
                }
                break;
            case 2: // 指定课程学生
                // 查询该课程的所有选课学生
                List<SCTInfo> sctList = studentCourseTeacherMapper.findByCtid(message.getTargetId());
                for (SCTInfo sct : sctList) {
                    SysUserMessage userMessage = new SysUserMessage();
                    userMessage.setUserId(sct.getSid());
                    userMessage.setUserType(1); // 学生类型
                    userMessage.setMessageId(messageId);
                    userMessageList.add(userMessage);
                }
                break;
            case 3: // 指定用户
                SysUserMessage userMessage = new SysUserMessage();
                userMessage.setUserId(message.getTargetId());

                // 校验目标用户类型：如果是教师用户且是管理员，自动更正为管理员类型3
                Integer targetUserType = message.getTargetUserType();
                if (targetUserType == 2) { // 如果传的是教师类型，检查是不是管理员
                    Teacher teacher = teacherMapper.findById(message.getTargetId());
                    if (teacher != null && "admin".equals(teacher.getTname())) {
                        targetUserType = 3;
                    }
                }

                userMessage.setUserType(targetUserType);
                userMessage.setMessageId(messageId);
                userMessageList.add(userMessage);
                break;
        }

        if (userMessageList.size() > 0) {
            return sysUserMessageMapper.batchSave(userMessageList);
        }

        return true;
    }

    /**
     * 发送全体消息
     */
    public boolean sendAllMessage(String title, String content) {
        SysMessage message = new SysMessage();
        message.setType(3); // 管理员全体通知
        message.setTitle(title);
        message.setContent(content);
        message.setSenderId(1); // 管理员ID，默认1
        message.setSenderType(1); // 管理员类型
        message.setTargetType(1); // 全体用户（学生、教师和管理员）
        message.setTargetId(0);
        return saveAndDistribute(message);
    }

    /**
     * 教师向课程学生发送消息
     */
    public boolean sendMessageToCourse(Integer ctid, String title, String content, Integer tid) {
        SysMessage message = new SysMessage();
        message.setType(4); // 教师课程通知
        message.setTitle(title);
        message.setContent(content);
        message.setSenderId(tid); // 教师ID
        message.setSenderType(2); // 教师类型
        message.setTargetType(2); // 指定课程学生
        message.setTargetId(ctid); // 课程ctid
        return saveAndDistribute(message);
    }

    /**
     * 发送私信
     */
    public boolean sendPrivateMessage(Integer senderId, Integer senderType, Integer targetId, Integer targetUserType, String title, String content) {
        SysMessage message = new SysMessage();
        message.setType(5); // 私信
        message.setTitle(title);
        message.setContent(content);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setTargetType(3); // 指定用户
        message.setTargetId(targetId);
        message.setTargetUserType(targetUserType);
        return saveAndDistribute(message);
    }
}
