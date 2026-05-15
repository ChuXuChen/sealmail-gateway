package com.auggie.student_server.service;

import com.auggie.student_server.entity.Student;
import com.auggie.student_server.entity.SysMessage;
import com.auggie.student_server.entity.SysUserMessage;
import com.auggie.student_server.entity.Teacher;
import com.auggie.student_server.mapper.StudentMapper;
import com.auggie.student_server.mapper.SysUserMessageMapper;
import com.auggie.student_server.mapper.TeacherMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserMessageService {

    @Autowired
    private SysUserMessageMapper sysUserMessageMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    /**
     * 查询用户消息列表
     */
    public List<SysUserMessage> findByUserId(Integer userId, Integer userType, Integer isRead) {
        List<SysUserMessage> list = sysUserMessageMapper.findByUserId(userId, userType, isRead);
        for (SysUserMessage userMessage : list) {
            SysMessage message = userMessage.getMessage();
            if (message != null && message.getSenderId() != 0) { // 排除系统消息
                String senderName = "";
                switch (message.getSenderType()) {
                    case 1: // 管理员
                        senderName = "管理员";
                        break;
                    case 2: // 教师
                        Teacher teacher = teacherMapper.findById(message.getSenderId());
                        senderName = teacher != null ? teacher.getTname() : "未知教师";
                        break;
                    case 3: // 学生
                        Student student = studentMapper.findById(message.getSenderId());
                        senderName = student != null ? student.getSname() : "未知学生";
                        break;
                }
                message.setSenderName(senderName);
            }
        }
        return list;
    }

    /**
     * 查询未读消息数量
     */
    public int countUnread(Integer userId, Integer userType) {
        return sysUserMessageMapper.countUnread(userId, userType);
    }

    /**
     * 标记单条消息为已读
     */
    public boolean markAsRead(Integer id) {
        return sysUserMessageMapper.markAsRead(id);
    }

    /**
     * 标记所有消息为已读
     */
    public boolean markAllAsRead(Integer userId, Integer userType) {
        return sysUserMessageMapper.markAllAsRead(userId, userType);
    }
}
