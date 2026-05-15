package com.auggie.student_server.task;

import com.auggie.student_server.entity.Quiz;
import com.auggie.student_server.entity.SysMessage;
import com.auggie.student_server.mapper.QuizMapper;
import com.auggie.student_server.service.SysMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Component
public class MessageTask {

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private SysMessageService sysMessageService;

    /**
     * 每天凌晨1点执行，检查即将到期的测验，发送提醒
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkQuizExpire() {
        // 查询所有已发布且未结束的测验
        Quiz query = new Quiz();
        query.setStatus(1); // 已发布
        List<Quiz> quizList = quizMapper.findBySearch(query, 0);

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.HOUR_OF_DAY, 24);
        Date tomorrow = calendar.getTime();

        for (Quiz quiz : quizList) {
            Date endTime = quiz.getEndTime();
            if (endTime == null) {
                continue;
            }
            // 如果测验结束时间在当前时间和24小时后之间，说明还有不到1天结束
            if (endTime.after(now) && endTime.before(tomorrow)) {
                // 发送提醒消息
                SysMessage message = new SysMessage();
                message.setType(2); // 测验1天到期提醒
                message.setTitle("测验即将结束提醒");
                message.setContent("您的测验【" + quiz.getQuizTitle() + "】还有不到1天就要结束了，请及时完成！");
                message.setSenderId(0); // 系统发送
                message.setSenderType(0); // 系统类型
                message.setTargetType(2); // 指定课程学生
                message.setTargetId(quiz.getCtid()); // 课程ctid
                message.setRelatedId(quiz.getQuizId()); // 关联测验ID
                sysMessageService.saveAndDistribute(message);
            }
        }
    }
}
