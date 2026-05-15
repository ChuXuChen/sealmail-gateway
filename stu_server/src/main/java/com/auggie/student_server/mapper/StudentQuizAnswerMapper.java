package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.StudentQuizAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: StudentQuizAnswerMapper 学生答题记录数据访问接口
 * @Version 1.0.0
 */
@Mapper
@Repository
public interface StudentQuizAnswerMapper {

    // select
    public List<StudentQuizAnswer> findAll();

    public StudentQuizAnswer findById(@Param("answerId") Integer answerId);

    public List<StudentQuizAnswer> findByQuizId(@Param("quizId") Integer quizId);

    public List<StudentQuizAnswer> findByQuizAndStudent(@Param("quizId") Integer quizId, @Param("sid") Integer sid);

    public List<StudentQuizAnswer> findByQuestionId(@Param("questionId") Integer questionId);

    // update
    public int updateById(@Param("answer") StudentQuizAnswer answer);

    // insert
    public int save(@Param("answer") StudentQuizAnswer answer);

    public int saveBatch(@Param("answers") List<StudentQuizAnswer> answers);

    // delete
    public int deleteById(@Param("answerId") Integer answerId);

    public int deleteByQuizId(@Param("quizId") Integer quizId);

    public int deleteByQuizAndStudent(@Param("quizId") Integer quizId, @Param("sid") Integer sid);

    public int deleteByQuestionId(@Param("questionId") Integer questionId);
}
