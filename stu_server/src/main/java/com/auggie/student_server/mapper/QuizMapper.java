package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.Quiz;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizMapper 测验数据访问接口
 * @Version 1.0.0
 */
@Mapper
@Repository
public interface QuizMapper {

    // select
    public List<Quiz> findAll();

    public Quiz findById(@Param("quizId") Integer quizId);

    public List<Quiz> findBySearch(@Param("quiz") Quiz quiz, @Param("fuzzy") Integer fuzzy);

    public List<Quiz> findByCtid(@Param("ctid") Integer ctid);

    public List<Quiz> findByTeacher(@Param("tid") Integer tid);

    public List<Quiz> findByStudent(@Param("sid") Integer sid);

    // update
    public boolean updateById(@Param("quiz") Quiz quiz);

    // insert
    public boolean save(@Param("quiz") Quiz quiz);

    // delete
    public boolean deleteById(@Param("quizId") Integer quizId);
}
