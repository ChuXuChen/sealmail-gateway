package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.CourseTeacher;
import com.auggie.student_server.entity.CourseTeacherInfo;
import com.auggie.student_server.entity.SCTInfo;
import com.auggie.student_server.entity.StudentCourseTeacher;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2022/2/10 19:58
 * @Description: StudentCourseTeacherMapper
 * @Version 1.0.0
 */

@Repository
@Mapper
public interface StudentCourseTeacherMapper {

    public List<CourseTeacherInfo> findByStudentId(@Param("sid") Integer sid,
                                                   @Param("term") String term);

    public List<SCTInfo> findBySearch(@Param("sid") Integer sid,
                                      @Param("sname") String sname,
                                      @Param("sFuzzy") Integer sFuzzy,
                                      @Param("cid") Integer cid,
                                      @Param("cname") String cname,
                                      @Param("cFuzzy") Integer cFuzzy,
                                      @Param("tid") Integer tid,
                                      @Param("tname") String tname,
                                      @Param("tFuzzy") Integer tFuzzy,
                                      @Param("lowBound") Integer lowBound,
                                      @Param("highBound") Integer highBound,
                                      @Param("term") String term,
                                      @Param("ctid") Integer ctid);

    /**
     * 根据ctid查询该课程的所有学生
     * @param ctid 课程教师关联ID
     * @return 学生信息列表
     */
    public List<SCTInfo> findByCtid(@Param("ctid") Integer ctid);

    @Select("SELECT DISTINCT sct.term FROM studentms.sct sct")
    public List<String> findAllTerm();

    @Select("SELECT * FROM studentms.sct WHERE sid = #{sct.sid} AND cid = #{sct.cid} AND tid = #{sct.tid} AND term = #{sct.term}")
    public List<StudentCourseTeacher> findBySCT(@Param("sct") StudentCourseTeacher studentCourseTeacher);

    @Insert("INSERT INTO studentms.sct (sid, cid, tid, term) VALUES (#{s.sid}, #{s.cid}, #{s.tid}, #{s.term})")
    public boolean insert(@Param("s")StudentCourseTeacher studentCourseTeacher);

    @Update("UPDATE studentms.sct SET sct.grade = #{grade} WHERE sct.sid = #{sid} AND sct.tid = #{tid} AND sct.cid = #{cid} AND sct.term = #{term}")
    public boolean updateById(@Param("sid") Integer sid,
                              @Param("cid") Integer cid,
                              @Param("tid") Integer tid,
                              @Param("term") String term,
                              @Param("grade") Integer grade);

    @Delete("DELETE FROM studentms.sct WHERE sid = #{sct.sid} AND tid = #{sct.tid} AND cid = #{sct.cid} AND term = #{sct.term}")
    public boolean deleteBySCT(@Param("sct") StudentCourseTeacher sct);

    @Delete("DELETE FROM studentms.sct WHERE cid = #{cid} AND tid = #{tid} AND term = #{term}")
    public boolean deleteByCT(@Param("cid") Integer cid,
                              @Param("tid") Integer tid,
                              @Param("term") String term);

    @Update("UPDATE studentms.sct SET cid = #{newCid}, tid = #{newTid}, term = #{newTerm} WHERE cid = #{oldCid} AND tid = #{oldTid} AND term = #{oldTerm}")
    public boolean updateByCT(@Param("oldCid") Integer oldCid,
                              @Param("oldTid") Integer oldTid,
                              @Param("oldTerm") String oldTerm,
                              @Param("newCid") Integer newCid,
                              @Param("newTid") Integer newTid,
                              @Param("newTerm") String newTerm);
}
