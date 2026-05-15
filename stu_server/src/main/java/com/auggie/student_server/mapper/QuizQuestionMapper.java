package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizQuestionMapper 测验题目数据访问接口
 * @Version 1.0.0
 */
@Mapper
@Repository
public interface QuizQuestionMapper {

    // select
    public List<QuizQuestion> findAll();

    public QuizQuestion findById(@Param("questionId") Integer questionId);

    public List<QuizQuestion> findByQuizId(@Param("quizId") Integer quizId);

    // update
    public boolean updateById(@Param("question") QuizQuestion question);

    // insert
    public boolean save(@Param("question") QuizQuestion question);

    public boolean saveBatch(@Param("questions") List<QuizQuestion> questions);

    // delete
    public boolean deleteById(@Param("questionId") Integer questionId);

    public boolean deleteByQuizId(@Param("quizId") Integer quizId);
}
