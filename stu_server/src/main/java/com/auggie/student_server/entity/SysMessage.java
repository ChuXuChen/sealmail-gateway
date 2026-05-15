package com.auggie.student_server.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.util.Date;

/**
 * 系统消息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("SysMessage")
public class SysMessage {
    private Integer messageId;
    private Integer type; // 1-新课程测验发布 2-测验1天到期提醒 3-管理员全体通知 4-教师课程通知 5-私信
    private String title;
    private String content;
    private Integer senderId; // 发送者ID，系统发送为0
    private Integer senderType; // 0-系统 1-管理员 2-教师 3-学生
    private Integer targetType; // 1-全体用户 2-指定课程学生 3-指定用户
    private Integer targetId; // 目标ID，全体为0
    private Integer relatedId; // 关联ID，测验ID/课程ID
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private Integer isDeleted;

    // 非数据库字段
    private String senderName; // 发送人姓名，用于返回前端
    private Integer targetUserType; // 目标用户类型：1-学生 2-教师 3-管理员，用于接收前端参数
}
