package com.auggie.student_server.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.util.Date;

/**
 * 用户消息关联实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("SysUserMessage")
public class SysUserMessage {
    private Integer id;
    private Integer userId;
    private Integer userType; // 1-学生 2-教师 3-管理员
    private Integer messageId;
    private Integer isRead; // 0-未读 1-已读
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date readTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    // 关联消息实体
    private SysMessage message;
}
