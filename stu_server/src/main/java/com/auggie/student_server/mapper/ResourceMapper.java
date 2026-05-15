package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: ResourceMapper
 * @Version 1.0.0
 */

@Mapper
@Repository
public interface ResourceMapper {

    // select
    public List<Resource> findAll();

    public Resource findById(@Param("rid") Integer rid);

    public List<Resource> findBySearch(@Param("resource") Resource resource, @Param("fuzzy") Integer fuzzy);

    public List<Resource> findByCtid(@Param("ctid") Integer ctid);

    public List<Resource> findByTeacher(@Param("tid") Integer tid);

    public List<Resource> findByStudent(@Param("sid") Integer sid);

    // update
    public boolean updateById(@Param("resource") Resource resource);

    // insert
    public boolean save(@Param("resource") Resource resource);

    // delete
    public boolean deleteById(@Param("rid") Integer rid);

}