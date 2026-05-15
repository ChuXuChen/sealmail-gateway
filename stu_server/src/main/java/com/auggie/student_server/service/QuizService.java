package com.auggie.student_server.service;

import com.auggie.student_server.entity.Quiz;
import com.auggie.student_server.mapper.QuizMapper;
import com.auggie.student_server.mapper.QuizQuestionMapper;
import com.auggie.student_server.mapper.StudentQuizAnswerMapper;
import com.auggie.student_server.mapper.StudentQuizScoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: QuizService 测验服务类
 * @Version 1.0.0
 */
@Service
@Transactional
public class QuizService {
    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private StudentQuizAnswerMapper studentQuizAnswerMapper;

    @Autowired
    private StudentQuizScoreMapper studentQuizScoreMapper;

    public List<Quiz> findAll() {
        return quizMapper.findAll();
    }

    public Quiz findById(Integer quizId) {
        return quizMapper.findById(quizId);
    }

    public List<Quiz> findBySearch(Integer quizId, Integer ctid, String quizTitle, String quizDescription, Integer fuzzy) {
        Quiz quiz = new Quiz();
        quiz.setQuizId(quizId);
        quiz.setCtid(ctid);
        quiz.setQuizTitle(quizTitle);
        quiz.setQuizDescription(quizDescription);
        fuzzy = (fuzzy == null) ? 0 : fuzzy;

        return quizMapper.findBySearch(quiz, fuzzy);
    }

    public List<Quiz> findByCtid(Integer ctid) {
        return quizMapper.findByCtid(ctid);
    }

    public List<Quiz> findByTeacher(Integer tid) {
        return quizMapper.findByTeacher(tid);
    }

    public List<Quiz> findByStudent(Integer sid) {
        return quizMapper.findByStudent(sid);
    }

    public boolean updateById(Quiz quiz) {
        return quizMapper.updateById(quiz);
    }

    public boolean save(Quiz quiz) {
        return quizMapper.save(quiz);
    }

    public boolean deleteById(Integer quizId) {
        // 先删除关联的答题记录
        try {
            studentQuizAnswerMapper.deleteByQuizId(quizId);
        } catch (Exception e) {
            System.out.println("删除答题记录时出错: " + e.getMessage());
        }

        // 再删除关联的成绩记录
        try {
            studentQuizScoreMapper.deleteByQuizId(quizId);
        } catch (Exception e) {
            System.out.println("删除成绩记录时出错: " + e.getMessage());
        }

        // 再删除关联的题目
        try {
            quizQuestionMapper.deleteByQuizId(quizId);
        } catch (Exception e) {
            System.out.println("删除题目时出错: " + e.getMessage());
        }

        // 最后删除测验
        return quizMapper.deleteById(quizId);
    }
}
