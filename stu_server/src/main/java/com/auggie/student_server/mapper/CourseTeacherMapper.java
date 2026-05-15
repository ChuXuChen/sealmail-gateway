package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.Course;
import com.auggie.student_server.entity.CourseTeacher;
import com.auggie.student_server.entity.CourseTeacherInfo;
import lombok.Data;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2022/2/10 16:43
 * @Description: CourseTeacherMapper
 * @Version 1.0.0
 */
@Repository
@Mapper
public interface CourseTeacherMapper {

    @Insert("INSERT INTO studentms.ct (cid, tid, term) VALUES (#{cid}, #{tid}, #{term})")
    public boolean insertCourseTeacher(@Param("cid") Integer cid,
                                       @Param("tid") Integer tid,
                                       @Param("term") String term);

    public List<CourseTeacher> findBySearch(@Param("cid") Integer cid,
                                            @Param("tid") Integer tid,
                                            @Param("term") String term);

    public List<java.util.Map<String, Object>> findMyCourse(@Param("tid") Integer tid,
                                     @Param("term") String term);

    public List<CourseTeacherInfo> findCourseTeacherInfo(@Param("tid") Integer tid,
                                                         @Param("tname") String tname,
                                                         @Param("tFuzzy") Integer tFuzzy,
                                                         @Param("cid") Integer cid,
                                                         @Param("cname") String cname,
                                                         @Param("cFuzzy") Integer cFuzzy);

    @Delete("DELETE FROM studentms.ct WHERE cid = #{c.cid} AND tid = #{c.tid} AND term = #{c.term}")
    public boolean deleteById(@Param("c") CourseTeacher courseTeacher);

    @Update("UPDATE studentms.ct SET cid = #{c.cid}, tid = #{c.tid}, term = #{c.term} WHERE ctid = #{c.ctid}")
    public boolean updateById(@Param("c") CourseTeacher courseTeacher);

    @Select("SELECT * FROM studentms.ct WHERE ctid = #{ctid}")
    public List<CourseTeacher> findByCtid(@Param("ctid") Integer ctid);
}
