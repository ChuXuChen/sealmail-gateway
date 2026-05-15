package com.auggie.student_server.service;

import com.auggie.student_server.entity.QuizQuestion;
import com.auggie.student_server.mapper.QuizQuestionMapper;
import com.auggie.student_server.mapper.StudentQuizAnswerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizQuestionService 测验题目服务类
 * @Version 1.0.0
 */
@Service
@Transactional
public class QuizQuestionService {
    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private StudentQuizAnswerMapper studentQuizAnswerMapper;

    public List<QuizQuestion> findAll() {
        return quizQuestionMapper.findAll();
    }

    public QuizQuestion findById(Integer questionId) {
        return quizQuestionMapper.findById(questionId);
    }

    public List<QuizQuestion> findByQuizId(Integer quizId) {
        return quizQuestionMapper.findByQuizId(quizId);
    }

    public boolean updateById(QuizQuestion question) {
        return quizQuestionMapper.updateById(question);
    }

    public boolean save(QuizQuestion question) {
        return quizQuestionMapper.save(question);
    }

    public boolean saveBatch(List<QuizQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return false;
        }
        return quizQuestionMapper.saveBatch(questions);
    }

    public boolean deleteById(Integer questionId) {
        // 先删除该题目的所有答题记录
        studentQuizAnswerMapper.deleteByQuestionId(questionId);
        // 再删除题目
        return quizQuestionMapper.deleteById(questionId);
    }

    public boolean deleteByQuizId(Integer quizId) {
        return quizQuestionMapper.deleteByQuizId(quizId);
    }
}
