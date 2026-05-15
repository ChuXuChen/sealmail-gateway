<template>
  <div class="quiz-taking-container">
    <el-container>
      <!-- 顶部信息栏 -->
      <el-header class="quiz-header">
        <div class="header-content">
          <div class="quiz-info">
            <h3>{{ quizTitle }}</h3>
            <div class="quiz-meta">
              <span><i class="el-icon-notebook-2"></i> 课程：{{ courseName }}</span>
              <span class="separator">•</span>
              <span><i class="el-icon-medal"></i> 总分：{{ totalScore }}分</span>
              <span class="separator">•</span>
              <span><i class="el-icon-time"></i> 剩余时间：
                <span class="timer" :class="{ 'warning': remainingTime < 600, 'danger': remainingTime < 300 }">
                  {{ formatTime(remainingTime) }}
                </span>
              </span>
            </div>
          </div>
          <div class="header-actions">
            <el-button type="primary" size="large" @click="confirmSubmit" :loading="isSubmitting">
              <i class="el-icon-document-checked"></i> 提交测验
            </el-button>
          </div>
        </div>
      </el-header>

      <el-container class="quiz-main-container">
        <!-- 左侧题目区域 -->
        <el-main class="question-area">
          <div v-if="questions.length > 0" class="current-question">
            <el-card class="question-card" shadow="hover">
              <div slot="header" class="question-card-header">
                <div class="question-title">
                  <span class="question-number">第 {{ activeQuestionIndex + 1 }} 题</span>
                  <span class="question-type-badge" :class="getQuestionTypeClass(questions[activeQuestionIndex].questionType)">
                    {{ getQuestionTypeName(questions[activeQuestionIndex].questionType) }}
                  </span>
                  <span class="question-score">({{ questions[activeQuestionIndex].questionScore }}分)</span>
                </div>
                <div class="question-progress">
                  {{ activeQuestionIndex + 1 }} / {{ questions.length }}
                </div>
              </div>

              <div class="question-content">
                <p class="question-text">{{ questions[activeQuestionIndex].questionContent }}</p>

                <div class="answer-area">
                  <!-- 单选题 -->
                  <template v-if="questions[activeQuestionIndex].questionType === 1">
                    <el-radio-group v-model="studentAnswers[questions[activeQuestionIndex].questionId]" class="option-group">
                      <el-radio :label="option.option" v-for="(option, optIndex) in parseOptions(questions[activeQuestionIndex].options)" :key="optIndex" class="option-item">
                        <span class="option-label">{{ option.option }}.</span>
                        <span class="option-content">{{ option.content }}</span>
                      </el-radio>
                    </el-radio-group>
                  </template>

                  <!-- 多选题 -->
                  <template v-else-if="questions[activeQuestionIndex].questionType === 2">
                    <el-checkbox-group v-model="studentAnswers[questions[activeQuestionIndex].questionId]" class="option-group">
                      <el-checkbox :label="option.option" v-for="(option, optIndex) in parseOptions(questions[activeQuestionIndex].options)" :key="optIndex" class="option-item">
                        <span class="option-label">{{ option.option }}.</span>
                        <span class="option-content">{{ option.content }}</span>
                      </el-checkbox>
                    </el-checkbox-group>
                  </template>

                  <!-- 判断题 -->
                  <template v-else-if="questions[activeQuestionIndex].questionType === 3">
                    <el-radio-group v-model="studentAnswers[questions[activeQuestionIndex].questionId]" class="option-group judge-group">
                      <el-radio :label="'A'" class="judge-option">
                        <i class="el-icon-circle-check" style="color: #67c23a;"></i> 正确
                      </el-radio>
                      <el-radio :label="'B'" class="judge-option">
                        <i class="el-icon-circle-close" style="color: #f56c6c;"></i> 错误
                      </el-radio>
                    </el-radio-group>
                  </template>

                  <!-- 填空题 -->
                  <template v-else-if="questions[activeQuestionIndex].questionType === 4">
                    <el-input
                      v-model="studentAnswers[questions[activeQuestionIndex].questionId]"
                      type="textarea"
                      :rows="3"
                      placeholder="请输入答案..."
                      class="answer-textarea">
                    </el-input>
                  </template>

                  <!-- 简答题 -->
                  <template v-else-if="questions[activeQuestionIndex].questionType === 5">
                    <el-input
                      v-model="studentAnswers[questions[activeQuestionIndex].questionId]"
                      type="textarea"
                      :rows="6"
                      placeholder="请输入答案..."
                      class="answer-textarea">
                    </el-input>
                  </template>
                </div>

                <div class="question-actions">
                  <el-button type="primary" size="medium" @click="saveQuestionAnswer(questions[activeQuestionIndex].questionId)" :loading="savingQuestionId === questions[activeQuestionIndex].questionId">
                    <i class="el-icon-check"></i> 保存答案
                  </el-button>
                  <span v-if="answersSaved[questions[activeQuestionIndex].questionId]" class="save-success">
                    <i class="el-icon-circle-check"></i> 已保存
                  </span>
                </div>
              </div>
            </el-card>

            <!-- 上一题/下一题 -->
            <div class="question-navigation">
              <el-button @click="prevQuestion" :disabled="activeQuestionIndex === 0">
                <i class="el-icon-arrow-left"></i> 上一题
              </el-button>
              <el-button type="primary" @click="nextQuestion" :disabled="activeQuestionIndex === questions.length - 1">
                下一题 <i class="el-icon-arrow-right"></i>
              </el-button>
            </div>
          </div>

          <el-empty v-else description="暂无题目"></el-empty>
        </el-main>

        <!-- 右侧题目导航 -->
        <el-aside width="280px" class="nav-aside">
          <el-card class="nav-card" shadow="never">
            <div slot="header" class="nav-card-header">
              <span><i class="el-icon-s-order"></i> 题目导航</span>
              <span class="answered-count">已答: {{ answeredCount }}/{{ questions.length }}</span>
            </div>
            <div class="question-nav">
              <div
                class="nav-item"
                v-for="(question, index) in questions"
                :key="index"
                @click="goToQuestion(index)"
                :class="{
                  'active': activeQuestionIndex === index,
                  'answered': isAnswered(question),
                  'current': activeQuestionIndex === index
                }">
                {{ index + 1 }}
                <i v-if="isAnswered(question)" class="el-icon-check nav-check-icon"></i>
              </div>
            </div>
            <div class="nav-legend">
              <div class="legend-item">
                <span class="legend-dot"></span>
                <span>未答</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot answered"></span>
                <span>已答</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot active"></span>
                <span>当前</span>
              </div>
            </div>
          </el-card>
        </el-aside>
      </el-container>
    </el-container>

    <!-- 提交确认对话框 -->
    <el-dialog
      title="提交确认"
      :visible.sync="submitConfirmVisible"
      width="500px"
      :close-on-click-modal="false"
      class="submit-dialog">
      <div class="submit-confirm-content">
        <div class="warning-icon">
          <i class="el-icon-warning-outline"></i>
        </div>
        <p class="confirm-text">您确定要提交测验吗？</p>
        <p class="hint-text">提交后无法修改答案，请确认您的答案已全部填写完毕。</p>
        <div class="submit-summary">
          <div class="summary-item">
            <span class="summary-label">已答题目</span>
            <span class="summary-value answered">{{ answeredCount }}</span>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <span class="summary-label">未答题目</span>
            <span class="summary-value unanswered">{{ questions.length - answeredCount }}</span>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <span class="summary-label">完成率</span>
            <span class="summary-value">{{ getCompletionRate() }}%</span>
          </div>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="submitConfirmVisible = false" size="medium">取消</el-button>
        <el-button type="primary" @click="submitQuiz" :loading="isSubmitting" size="medium">确认提交</el-button>
      </span>
    </el-dialog>

    <!-- 自动提交提醒 -->
    <el-dialog
      title="时间警告"
      :visible.sync="autoSubmitWarningVisible"
      width="450px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="warning-dialog">
      <div class="warning-content">
        <div class="warning-icon-large">
          <i class="el-icon-time"></i>
        </div>
        <p class="warning-title">测验即将结束！</p>
        <p class="warning-timer">距离自动提交还有 <span class="countdown">{{ autoSubmitCountdown }}</span> 秒</p>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button type="primary" @click="confirmSubmit" size="medium">立即提交</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'QuizTaking',
  data() {
    return {
      // 测验信息
      quizId: null,
      sid: null,
      quizTitle: '',
      courseName: '',
      duration: 0,
      startTime: '',
      endTime: '',

      // 题目数据
      questions: [],
      activeQuestionIndex: 0,

      // 学生答案
      studentAnswers: {},
      answersSaved: {},
      savingQuestionId: null,

      // 计时
      remainingTime: 0,
      timer: null,

      // 自动提交倒计时
      autoSubmitCountdown: 30,
      autoSubmitTimer: null,
      autoSubmitWarningVisible: false,

      // 对话框
      submitConfirmVisible: false,
      isSubmitting: false
    };
  },
  created() {
    this.quizId = this.$route.query.quizId;
    this.sid = this.$route.query.sid;
    this.loadQuizInfo();
    this.loadQuestions();
  },
  mounted() {
    window.addEventListener('beforeunload', this.handleBeforeUnload);
  },
  beforeDestroy() {
    this.clearTimers();
    window.removeEventListener('beforeunload', this.handleBeforeUnload);
  },
  computed: {
    totalScore() {
      return this.questions.reduce((sum, question) => sum + (question.questionScore || 0), 0);
    },
    answeredCount() {
      return this.questions.filter(q => this.isAnswered(q)).length;
    }
  },
  methods: {
    // 加载测验信息
    loadQuizInfo() {
      const that = this;
      axios.get(`http://localhost:10086/quiz/findById/${this.quizId}`)
        .then(function(resp) {
          that.quizTitle = resp.data.quizTitle;
          that.courseName = resp.data.courseName;
          that.duration = resp.data.duration;
          that.startTime = resp.data.startTime;
          that.endTime = resp.data.endTime;

          // 计算剩余时间：取测验时长和距离结束时间的较小值（秒）
          const now = new Date();
          const endTime = new Date(resp.data.endTime);
          const durationSeconds = resp.data.duration * 60;
          const timeToEnd = Math.max(0, Math.floor((endTime - now) / 1000));
          that.remainingTime = Math.min(durationSeconds, timeToEnd);

          that.checkQuizStatus();
          that.startTimer();
        })
        .catch(function(error) {
          console.error('加载测验信息失败:', error);
          that.$message.error('加载测验信息失败');
        });
    },

    // 加载题目
    loadQuestions() {
      const that = this;
      axios.get(`http://localhost:10086/quizQuestion/findByQuizId/${this.quizId}`)
        .then(function(resp) {
          that.questions = resp.data;

          resp.data.forEach(question => {
            // 根据题目类型初始化答案
            let initialAnswer = '';
            if (question.questionType === 2) { // 多选题
              initialAnswer = [];
            } else if (question.questionType === 3) { // 判断题
              initialAnswer = '';
            }
            that.$set(that.studentAnswers, question.questionId, initialAnswer);
            that.$set(that.answersSaved, question.questionId, false);
          });

          that.loadSavedAnswers();
        })
        .catch(function(error) {
          console.error('加载题目失败:', error);
          that.$message.error('加载题目失败');
        });
    },

    // 加载已保存的答案
    loadSavedAnswers() {
      const that = this;
      axios.get(`http://localhost:10086/quizAnswer/findByQuizAndStudent/${this.quizId}/${this.sid}`)
        .then(function(resp) {
          if (resp.data && resp.data.length > 0) {
            resp.data.forEach(answer => {
              try {
                const parsedAnswer = JSON.parse(answer.studentAnswer);
                that.$set(that.studentAnswers, answer.questionId, parsedAnswer);
              } catch {
                that.$set(that.studentAnswers, answer.questionId, answer.studentAnswer);
              }
              that.$set(that.answersSaved, answer.questionId, true);
            });
          }
        })
        .catch(function() {
          // 无已保存答案时不处理
        });
    },

    // 检查测验状态
    checkQuizStatus() {
      const now = new Date();
      const startTime = new Date(this.startTime);
      const endTime = new Date(this.endTime);

      if (now < startTime) {
        this.$message.error('测验尚未开始');
        setTimeout(() => {
          this.$router.push('/quizList');
        }, 2000);
        return;
      }

      if (now > endTime) {
        this.$message.error('测验已结束');
        setTimeout(() => {
          this.$router.push('/quizList');
        }, 2000);
        return;
      }
    },

    // 启动计时器
    startTimer() {
      if (this.timer) {
        clearInterval(this.timer);
      }

      this.timer = setInterval(() => {
        this.remainingTime--;

        if (this.remainingTime === 30 && !this.autoSubmitWarningVisible) {
          this.autoSubmitWarningVisible = true;
          this.startAutoSubmitCountdown();
        }

        if (this.remainingTime <= 0) {
          this.clearTimers();
          this.autoSubmitQuiz();
        }
      }, 1000);
    },

    // 开始自动提交倒计时
    startAutoSubmitCountdown() {
      this.autoSubmitCountdown = 30;
      if (this.autoSubmitTimer) {
        clearInterval(this.autoSubmitTimer);
      }

      this.autoSubmitTimer = setInterval(() => {
        this.autoSubmitCountdown--;
        if (this.autoSubmitCountdown <= 0) {
          this.clearAutoSubmitTimer();
          this.autoSubmitQuiz();
        }
      }, 1000);
    },

    // 清除计时器
    clearTimers() {
      if (this.timer) {
        clearInterval(this.timer);
        this.timer = null;
      }
    },

    // 清除自动提交计时器
    clearAutoSubmitTimer() {
      if (this.autoSubmitTimer) {
        clearInterval(this.autoSubmitTimer);
        this.autoSubmitTimer = null;
      }
    },

    // 格式化时间
    formatTime(seconds) {
      const minutes = Math.floor(seconds / 60);
      const secs = seconds % 60;
      return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
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

    // 获取题目类型名称
    getQuestionTypeName(type) {
      const types = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' };
      return types[type] || '未知';
    },

    // 获取题目类型样式类
    getQuestionTypeClass(type) {
      const classes = { 1: 'type-single', 2: 'type-multiple', 3: 'type-judge', 4: 'type-fill', 5: 'type-essay' };
      return classes[type] || '';
    },

    // 切换题目
    goToQuestion(index) {
      this.activeQuestionIndex = index;
    },

    // 上一题
    prevQuestion() {
      if (this.activeQuestionIndex > 0) {
        this.activeQuestionIndex--;
      }
    },

    // 下一题
    nextQuestion() {
      if (this.activeQuestionIndex < this.questions.length - 1) {
        this.activeQuestionIndex++;
      }
    },

    // 检查题目是否已答
    isAnswered(question) {
      const answer = this.studentAnswers[question.questionId];
      if (!answer) return false;
      if (typeof answer === 'string') {
        return answer.trim().length > 0;
      } else if (Array.isArray(answer)) {
        return answer.length > 0;
      }
      return !!answer;
    },

    // 保存答案
    saveQuestionAnswer(questionId) {
      const answer = this.studentAnswers[questionId];
      if (!answer) {
        this.$message.warning('请输入答案');
        return;
      }

      this.savingQuestionId = questionId;
      const that = this;
      axios.post('http://localhost:10086/quizAnswer/save', {
        quizId: this.quizId,
        questionId: questionId,
        sid: this.sid,
        studentAnswer: JSON.stringify(answer)
      })
        .then(function(resp) {
          if (resp.data.success) {
            that.$set(that.answersSaved, questionId, true);
            that.$message.success('答案已保存');
            setTimeout(() => {
              that.$set(that.answersSaved, questionId, false);
            }, 2000);
          } else {
            that.$message.error(resp.data.message || '保存失败');
          }
        })
        .catch(function(error) {
          console.error('保存答案失败:', error);
          that.$message.error('保存失败');
        })
        .finally(function() {
          that.savingQuestionId = null;
        });
    },

    // 批量保存所有答案
    saveAllAnswers() {
      const that = this;
      const answersToSave = [];

      this.questions.forEach(question => {
        const answer = this.studentAnswers[question.questionId];
        if (answer) {
          if (typeof answer === 'string' && answer.trim().length > 0) {
            answersToSave.push({
              quizId: this.quizId,
              questionId: question.questionId,
              sid: this.sid,
              studentAnswer: JSON.stringify(answer)
            });
          } else if (Array.isArray(answer) && answer.length > 0) {
            answersToSave.push({
              quizId: this.quizId,
              questionId: question.questionId,
              sid: this.sid,
              studentAnswer: JSON.stringify(answer)
            });
          }
        }
      });

      if (answersToSave.length === 0) {
        return Promise.resolve();
      }

      return new Promise((resolve, reject) => {
        axios.post('http://localhost:10086/quizAnswer/saveBatch', answersToSave)
          .then(function(resp) {
            if (resp.data.success) {
              resolve();
            } else {
              reject(new Error(resp.data.message || '保存失败'));
            }
          })
          .catch(function(error) {
            reject(error);
          });
      });
    },

    // 获取完成率
    getCompletionRate() {
      if (this.questions.length === 0) return 0;
      return Math.round((this.answeredCount / this.questions.length) * 100);
    },

    // 自动提交
    autoSubmitQuiz() {
      this.$message.warning('时间到，自动提交');
      this.submitQuiz();
    },

    // 提交确认
    confirmSubmit() {
      this.autoSubmitWarningVisible = false;
      this.clearAutoSubmitTimer();

      if (this.answeredCount < this.questions.length) {
        this.$confirm(
          `您还有 ${this.questions.length - this.answeredCount} 道题未答，确定要提交吗？`,
          '提交确认',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        ).then(() => {
          this.submitConfirmVisible = true;
        }).catch(() => {});
      } else {
        this.submitConfirmVisible = true;
      }
    },

    // 提交测验
    submitQuiz() {
      if (this.isSubmitting) return;
      this.isSubmitting = true;

      const that = this;

      this.saveAllAnswers().then(() => {
        axios.post(`http://localhost:10086/quizScore/submit/${this.quizId}/${this.sid}`)
          .then(function(resp) {
            if (resp.data.success) {
              that.$message.success('测验提交成功');
              setTimeout(() => {
                that.$router.push({
                  path: '/quizResult',
                  query: { quizId: that.quizId, sid: that.sid }
                });
              }, 1500);
            } else {
              that.$message.error(resp.data.message || '提交失败');
              that.isSubmitting = false;
            }
          })
          .catch(function(error) {
            console.error('提交失败:', error);
            that.$message.error('提交失败');
            that.isSubmitting = false;
          });
      }).catch(function(error) {
        console.error('保存答案失败:', error);
        that.$message.error('保存答案失败，请重试');
        that.isSubmitting = false;
      });
    },

    // 页面离开时的处理
    handleBeforeUnload(event) {
      const unsavedCount = this.questions.length - this.answeredCount;
      if (unsavedCount > 0) {
        event.returnValue = '您还有未答的题目，确定要离开吗？';
        return '您还有未答的题目，确定要离开吗？';
      }
    }
  }
};
</script>

<style scoped>
.quiz-taking-container {
  min-height: 100vh;
  background-color: #f0f2f5;
}

.quiz-header {
  background-color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 0;
  height: auto !important;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 30px;
}

.quiz-info h3 {
  margin: 0;
  font-size: 20px;
  color: #303133;
  font-weight: 600;
}

.quiz-meta {
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.quiz-meta span {
  display: inline-flex;
  align-items: center;
}

.quiz-meta .separator {
  margin: 0 16px;
  color: #909399;
}

.timer {
  font-weight: 600;
  font-size: 16px;
  margin-left: 4px;
}

.timer.warning {
  color: #e6a23c;
}

.timer.danger {
  color: #f56c6c;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.header-actions {
  margin-left: 20px;
}

.quiz-main-container {
  flex-direction: row;
  height: calc(100vh - 100px);
}

.question-area {
  padding: 20px 30px;
  overflow-y: auto;
}

.current-question {
  max-width: 900px;
  margin: 0 auto;
}

.question-card {
  border-radius: 8px;
}

.question-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 0;
}

.question-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-number {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.question-type-badge {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.question-type-badge.type-single {
  background-color: #ecf5ff;
  color: #409eff;
}

.question-type-badge.type-multiple {
  background-color: #f0f9eb;
  color: #67c23a;
}

.question-type-badge.type-judge {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.question-type-badge.type-fill {
  background-color: #f4f4f5;
  color: #909399;
}

.question-type-badge.type-essay {
  background-color: #fef0f0;
  color: #f56c6c;
}

.question-score {
  font-size: 14px;
  color: #909399;
}

.question-progress {
  font-size: 14px;
  color: #606266;
  background-color: #f5f7fa;
  padding: 4px 12px;
  border-radius: 12px;
}

.question-content {
  padding: 10px 0;
}

.question-text {
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
  margin-bottom: 24px;
}

.answer-area {
  margin-bottom: 24px;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  font-size: 15px;
  line-height: 1.6;
}

.option-label {
  font-weight: 600;
  margin-right: 8px;
  color: #606266;
}

.option-content {
  color: #303133;
}

.judge-group {
  flex-direction: row;
  gap: 40px;
}

.judge-option {
  font-size: 16px;
}

.answer-textarea {
  font-size: 15px;
}

.question-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 10px;
}

.save-success {
  color: #67c23a;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.question-navigation {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 20px;
}

.nav-aside {
  background-color: #fff;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
}

.nav-card {
  height: 100%;
  border: none;
  border-radius: 0;
}

.nav-card >>> .el-card__header {
  padding: 16px 20px;
  border-bottom: 1px solid #e4e7ed;
}

.nav-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.answered-count {
  font-size: 13px;
  color: #606266;
  font-weight: normal;
}

.nav-card >>> .el-card__body {
  padding: 20px;
}

.question-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.nav-item {
  width: 42px;
  height: 42px;
  background-color: #f5f7fa;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-weight: 600;
  font-size: 15px;
  color: #606266;
  transition: all 0.2s;
  position: relative;
}

.nav-item:hover {
  background-color: #e4e7ed;
  transform: translateY(-2px);
}

.nav-item.active {
  background-color: #409eff;
  color: white;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.nav-item.answered {
  background-color: #67c23a;
  color: white;
}

.nav-item.answered:hover {
  background-color: #5daf34;
}

.nav-item.current {
  border: 2px solid #409eff;
}

.nav-check-icon {
  position: absolute;
  top: -6px;
  right: -6px;
  font-size: 14px;
  background-color: #fff;
  border-radius: 50%;
}

.nav-legend {
  display: flex;
  gap: 16px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.legend-dot {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background-color: #f5f7fa;
}

.legend-dot.answered {
  background-color: #67c23a;
}

.legend-dot.active {
  background-color: #409eff;
}

.submit-dialog >>> .el-dialog__header {
  padding-bottom: 10px;
}

.submit-confirm-content {
  text-align: center;
}

.warning-icon {
  font-size: 64px;
  color: #e6a23c;
  margin-bottom: 16px;
}

.confirm-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.hint-text {
  font-size: 14px;
  color: #909399;
  margin: 0 0 24px;
}

.submit-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.summary-label {
  font-size: 13px;
  color: #909399;
}

.summary-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.summary-value.answered {
  color: #67c23a;
}

.summary-value.unanswered {
  color: #f56c6c;
}

.summary-divider {
  width: 1px;
  height: 40px;
  background-color: #e4e7ed;
  margin: 0 20px;
}

.dialog-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.warning-dialog >>> .el-dialog__header {
  padding-bottom: 10px;
}

.warning-content {
  text-align: center;
}

.warning-icon-large {
  font-size: 56px;
  color: #e6a23c;
  margin-bottom: 12px;
}

.warning-title {
  font-size: 18px;
  font-weight: 600;
  color: #e6a23c;
  margin: 0 0 8px;
}

.warning-timer {
  font-size: 15px;
  color: #606266;
  margin: 0;
}

.countdown {
  font-size: 28px;
  font-weight: 700;
  color: #f56c6c;
  margin: 0 4px;
}
</style>
