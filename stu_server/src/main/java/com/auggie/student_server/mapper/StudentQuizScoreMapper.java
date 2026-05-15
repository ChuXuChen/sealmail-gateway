package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.StudentQuizScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizScoreMapper 学生测验成绩数据访问接口
 * @Version 1.0.0
 */
@Mapper
@Repository
public interface StudentQuizScoreMapper {

    // select
    public List<StudentQuizScore> findAll();

    public StudentQuizScore findById(@Param("scoreId") Integer scoreId);

    public StudentQuizScore findByQuizAndStudent(@Param("quizId") Integer quizId, @Param("sid") Integer sid);

    public List<StudentQuizScore> findByQuizId(@Param("quizId") Integer quizId);

    public List<StudentQuizScore> findByStudentId(@Param("sid") Integer sid);

    // update
    public int updateById(@Param("score") StudentQuizScore score);

    // insert
    public int save(@Param("score") StudentQuizScore score);

    // delete
    public int deleteById(@Param("scoreId") Integer scoreId);

    public int deleteByQuizId(@Param("quizId") Integer quizId);

    public int deleteByQuizAndStudent(@Param("quizId") Integer quizId, @Param("sid") Integer sid);
}
