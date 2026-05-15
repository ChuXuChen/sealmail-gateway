<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <div slot="header">
            <span>测验结果</span>
            <el-button type="text" size="small" style="float: right;" @click="goBack">
              <i class="el-icon-arrow-left"></i> 返回测验列表
            </el-button>
          </div>

          <!-- 测验信息 -->
          <el-descriptions title="测验信息" :column="2">
            <el-descriptions-item label="测验标题">
              {{ quizTitle }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ formatDate(submitTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="总分">
              {{ quizTotalScore || totalScore }} 分
            </el-descriptions-item>
            <el-descriptions-item label="实得分">
              <span style="font-size: 24px; font-weight: bold; color: #409eff;">
                {{ obtainedScore }} 分
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 得分率单独显示 -->
          <div style="margin-top: 30px;">
            <h3 style="margin-bottom: 15px; color: #303133;">得分率</h3>
            <el-progress
              :percentage="scorePercentage"
              :stroke-width="20"
              :show-text="true"
              :text-inside="false"
              :stroke-linecap="'round'"
              :color="scorePercentage >= 90 ? '#67c23a' : scorePercentage >= 60 ? '#e6a23c' : '#f56c6c'">
            </el-progress>
          </div>

          <!-- 题目详情 -->
          <el-collapse v-model="activeNames">
            <el-collapse-item title="题目详情" name="1">
              <div v-for="(question, index) in questions" :key="index" class="question-item">
                <el-card :class="{
                  'correct': question.isCorrect,
                  'wrong': !question.isCorrect && question.isAnswered && (question.questionType === 1 || question.questionType === 2 || question.questionType === 3 || (question.questionType === 4 || question.questionType === 5) && question.gradingStatus === 2),
                  'pending': question.isAnswered && (question.questionType === 4 || question.questionType === 5) && question.gradingStatus !== 2
                }">
                  <div class="question-header">
                    <span class="question-number">{{ index + 1 }}. </span>
                    <span class="question-content">{{ question.questionContent }}</span>
                    <span class="question-score">({{ question.questionScore }}分)</span>
                    <el-tag :type="getQuestionStatusType(question)" size="small">
                      {{ getQuestionStatusText(question) }}
                    </el-tag>
                  </div>
                  <div class="question-body">
                    <!-- 选项 -->
                    <template v-if="question.questionType === 1 || question.questionType === 2">
                      <div class="options">
                        <div
                          v-for="(option, optIndex) in parseOptions(question.options)"
                          :key="optIndex"
                          class="option"
                          :class="{
                            'correct': isCorrectOption(question, option.option),
                            'selected': isSelectedOption(question, option.option),
                            'wrong': !isCorrectOption(question, option.option) && isSelectedOption(question, option.option)
                          }">
                          {{ option.option }}. {{ option.content }}
                        </div>
                      </div>
                    </template>

                    <!-- 学生答案 -->
                    <div class="answer-section">
                      <p class="answer-label">您的答案：</p>
                      <p class="answer-content">{{ formatAnswer(question.studentAnswer, question.questionType) }}</p>
                    </div>

                    <!-- 正确答案 -->
                    <div class="answer-section">
                      <p class="answer-label">正确答案：</p>
                      <p class="answer-content">{{ formatAnswer(question.correctAnswer, question.questionType) }}</p>
                    </div>

                    <!-- 教师评分 -->
                    <template v-if="question.gradingStatus === 2">
                      <div class="answer-section">
                        <p class="answer-label">教师评分：</p>
                        <p class="answer-content">
                          <span style="font-weight: bold; color: #409eff;">
                            {{ question.teacherScore !== null && question.teacherScore !== undefined ? question.teacherScore + '分' : '未评分' }}
                          </span>
                          <template v-if="question.teacherComment && question.teacherComment.trim() !== ''">
                            <br>
                            <span style="color: #666;">评语：{{ question.teacherComment }}</span>
                          </template>
                        </p>
                      </div>
                    </template>

                    <!-- 解析 -->
                    <template v-if="question.analysis">
                      <div class="answer-section">
                        <p class="answer-label">解析：</p>
                        <p class="answer-content">{{ question.analysis }}</p>
                      </div>
                    </template>
                  </div>
                </el-card>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script>
export default {
  name: 'QuizResult',
  data() {
    return {
      quizId: null,
      sid: null,
      quizTitle: '',
      totalScore: null,
      quizTotalScore: null,
      obtainedScore: null,
      submitTime: '',
      questions: [],
      activeNames: ['1']
    };
  },
  created() {
    this.quizId = this.$route.query.quizId;
    this.sid = this.$route.query.sid;
    this.loadScoreInfo();
    this.loadQuestions();
  },
  computed: {
    scorePercentage() {
      const total = Number(this.quizTotalScore) || Number(this.totalScore) || 0;
      const obtained = Number(this.obtainedScore) || 0;
      if (total === 0) return 0;
      return Math.round((obtained / total) * 100);
    }
  },
  methods: {
    // 返回测验列表
    goBack() {
      this.$router.push('/quizList');
    },

    // 加载成绩信息
    loadScoreInfo() {
      const that = this;
      axios.get(`http://localhost:10086/quizScore/findByQuizAndStudent/${this.quizId}/${this.sid}`)
        .then(function(resp) {
          const data = resp.data;
          console.log('加载成绩信息:', data);
          // 检查响应格式：可能是直接返回成绩对象，也可能是包含success字段的错误响应
          if (data.quizId) {
            // 直接返回成绩对象
            that.quizTitle = data.quizTitle;
            that.totalScore = data.totalScore;
            that.quizTotalScore = data.quizTotalScore;
            that.obtainedScore = data.obtainedScore;
            that.submitTime = data.submitTime;
          } else if (data.success === false) {
            that.$message.error(data.message || '成绩记录不存在');
          } else {
            that.$message.error('加载失败');
          }
        })
        .catch(function(error) {
          console.error('加载成绩失败:', error);
          that.$message.error('加载成绩失败');
        });
    },

    // 加载题目和答案
    loadQuestions() {
      const that = this;
      axios.get(`http://localhost:10086/quizAnswer/findByQuizAndStudent/${this.quizId}/${this.sid}`)
        .then(function(resp) {
          const answers = resp.data;
          console.log('加载答案:', answers);

          // 加载题目信息
          axios.get(`http://localhost:10086/quizQuestion/findByQuizId/${that.quizId}`)
            .then(function(questionsResp) {
              const questions = questionsResp.data;
              console.log('加载题目:', questions);

              // 合并题目和答案
              that.questions = questions.map(question => {
                const answer = answers.find(item => item.questionId === question.questionId);
                const isSubjective = question.questionType === 4 || question.questionType === 5;
                const isTeacherGraded = answer && answer.gradingStatus === 2;

                return {
                  ...question,
                  studentAnswer: that.parseStudentAnswer(answer ? answer.studentAnswer : ''),
                  // 主观题：如果教师已批阅，使用answer.isCorrect；否则为null显示待批改
                  isCorrect: isSubjective ? (isTeacherGraded ? answer.isCorrect : null) : (answer ? answer.isCorrect : null),
                  isAnswered: !!answer && (answer.studentAnswer && answer.studentAnswer.trim() !== ''),
                  score: answer ? answer.score : 0,
                  gradingStatus: answer ? answer.gradingStatus : null,
                  teacherScore: answer ? answer.teacherScore : null,
                  teacherComment: answer ? answer.teacherComment : null
                };
              });
              console.log('合并后的数据:', that.questions);
            })
            .catch(function(error) {
              console.error('加载题目失败:', error);
              that.questions = [];
              that.$message.error('加载题目失败');
            });
        })
        .catch(function(error) {
          console.error('加载答案失败:', error);
          that.$message.error('加载答案失败');
        });
    },

    // 解析学生答案
    parseStudentAnswer(answerStr) {
      if (!answerStr) {
        return '';
      }
      try {
        // 尝试解析JSON字符串
        let parsed = JSON.parse(answerStr);
        // 判断题特殊处理：如果是 1/0 或 true/false，转换为 A/B
        if (parsed === 1 || parsed === true) {
          return 'A';
        } else if (parsed === 0 || parsed === false) {
          return 'B';
        }
        return parsed;
      } catch (error) {
        // 不是JSON格式，检查是否是true/false字符串
        const lowerCase = answerStr.toLowerCase();
        if (lowerCase === 'true') {
          return 'A';
        } else if (lowerCase === 'false') {
          return 'B';
        } else if (answerStr === '1') {
          return 'A';
        } else if (answerStr === '0') {
          return 'B';
        }
        return answerStr;
      }
    },

    // 格式化日期
    formatDate(dateString) {
      if (!dateString) return '';
      const date = new Date(dateString);
      return date.toLocaleString('zh-CN');
    },

    // 解析选项
    parseOptions(optionsStr) {
      if (!optionsStr) return [];
      try {
        const parsed = JSON.parse(optionsStr);

        // 格式1: 数组格式 [{"option":"A","content":"xxx"}]
        if (Array.isArray(parsed)) {
          return parsed.map((opt, index) => ({
            option: opt.option || opt.label || String.fromCharCode(65 + index),
            content: opt.content || opt.value || opt.text || ''
          }));
        }

        // 格式2: 对象格式 {"A":"内容","B":"内容"}
        if (typeof parsed === 'object' && parsed !== null) {
          return Object.keys(parsed).map(key => ({
            option: key,
            content: parsed[key]
          }));
        }

        return [];
      } catch (error) {
        console.error('解析选项失败:', error, optionsStr);
        return [];
      }
    },

    // 格式化答案
    formatAnswer(answerStr, questionType) {
      if (!answerStr) return '未作答';
      try {
        let answer = answerStr;
        try {
          // 尝试解析JSON
          answer = JSON.parse(answerStr);
        } catch (e) {
          // 不是JSON，使用原始值
        }

        // 处理数组（多选题）
        if (Array.isArray(answer)) {
          return answer.join(', ');
        }

        // 转换为字符串
        let answerStrVal = String(answer);

        // 处理AI生成的带括号答案格式，如"B(错误)"、"A(正确)"
        if (questionType === 3) {
          const match = answerStrVal.match(/^[AB]\((正确|错误)\)$/);
          if (match) {
            answerStrVal = match[1];
          }
        }

        // 只有判断题特殊处理：将A/B转换为"正确"/"错误"
        if (questionType === 3) {
          if (answerStrVal === 'A' || answerStrVal === '1' || answerStrVal.toLowerCase() === 'true' || answerStrVal === '正确') {
            return '正确';
          } else if (answerStrVal === 'B' || answerStrVal === '0' || answerStrVal.toLowerCase() === 'false' || answerStrVal === '错误') {
            return '错误';
          }
        }

        return answerStrVal;
      } catch (error) {
        // 只有判断题特殊处理
        if (questionType === 3) {
          if (answerStr === 'A' || answerStr === '1' || answerStr.toLowerCase() === 'true') {
            return '正确';
          } else if (answerStr === 'B' || answerStr === '0' || answerStr.toLowerCase() === 'false') {
            return '错误';
          }
        }
        return answerStr;
      }
    },

    // 检查是否为正确选项
    isCorrectOption(question, option) {
      const correctAnswer = this.formatAnswer(question.correctAnswer, question.questionType);
      return correctAnswer.includes(option);
    },

    // 获取题目状态类型
    getQuestionStatusType(question) {
      const isSubjective = question.questionType === 4 || question.questionType === 5;
      const isTeacherGraded = question.gradingStatus === 2;

      // 主观题：根据批阅状态显示
      if (isSubjective) {
        if (isTeacherGraded) {
          // 教师已批阅，根据是否正确显示
          return question.isCorrect ? 'success' : 'danger';
        } else {
          // 待批改
          return 'warning';
        }
      }
      // 其他题型显示正确/错误
      return question.isCorrect ? 'success' : 'danger';
    },

    // 获取题目状态文本
    getQuestionStatusText(question) {
      const isSubjective = question.questionType === 4 || question.questionType === 5;
      const isTeacherGraded = question.gradingStatus === 2;

      // 主观题：根据批阅状态显示
      if (isSubjective) {
        if (isTeacherGraded) {
          // 教师已批阅，根据是否正确显示
          return question.isCorrect ? '正确' : '错误';
        } else {
          // 待批改
          return '待批改';
        }
      }
      // 其他题型显示正确/错误
      return question.isCorrect ? '正确' : '错误';
    },

    // 检查是否为选中选项
    isSelectedOption(question, option) {
      const studentAnswer = this.formatAnswer(question.studentAnswer, question.questionType);
      return studentAnswer.includes(option);
    }
  }
};
</script>

<style scoped>
.question-item {
  margin-bottom: 15px;
}

.question-item .el-card {
  border-left: 4px solid #dcdfe6;
}

.question-item .el-card.correct {
  border-left-color: #67c23a;
}

.question-item .el-card.wrong {
  border-left-color: #f56c6c;
}

.question-item .el-card.pending {
  border-left-color: #e6a23c;
}

.question-header {
  display: flex;
  align-items: center;
}

.question-number {
  font-weight: bold;
}

.question-content {
  flex: 1;
  margin-right: 10px;
}

.question-score {
  margin-right: 10px;
  color: #606266;
  font-size: 14px;
}

.question-body {
  margin-top: 15px;
  padding-left: 25px;
}

.options {
  margin-bottom: 20px;
}

.option {
  padding: 8px 10px;
  margin-bottom: 5px;
  border-radius: 4px;
  cursor: default;
}

.option.correct {
  background-color: #f0f9ff;
  color: #0050b3;
}

.option.selected {
  background-color: #f0f9ff;
  border: 1px solid #91d5ff;
}

.option.wrong {
  background-color: #fff2f0;
  color: #cf1322;
  border: 1px solid #ffa39e;
}

.answer-section {
  margin-bottom: 15px;
}

.answer-label {
  font-weight: bold;
  margin-bottom: 5px;
  color: #606266;
}

.answer-content {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  word-break: break-all;
}
</style>
