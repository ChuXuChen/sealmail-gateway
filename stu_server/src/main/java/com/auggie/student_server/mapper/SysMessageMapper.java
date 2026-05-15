package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface SysMessageMapper {
    boolean save(@Param("message") SysMessage message);

    SysMessage findById(@Param("messageId") Integer messageId);

    List<SysMessage> findBySearch(@Param("message") SysMessage message);
}
