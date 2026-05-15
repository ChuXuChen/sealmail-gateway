package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.SysUserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface SysUserMessageMapper {
    boolean batchSave(@Param("list") List<SysUserMessage> list);

    List<SysUserMessage> findByUserId(@Param("userId") Integer userId,
                                     @Param("userType") Integer userType,
                                     @Param("isRead") Integer isRead);

    int countUnread(@Param("userId") Integer userId,
                   @Param("userType") Integer userType);

    boolean markAsRead(@Param("id") Integer id);

    boolean markAllAsRead(@Param("userId") Integer userId,
                         @Param("userType") Integer userType);
}
