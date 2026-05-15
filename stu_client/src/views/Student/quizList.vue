<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <div slot="header">
            <span>测验列表</span>
          </div>

          <!-- 搜索和筛选 -->
          <el-form :inline="true" :model="searchForm" label-width="80px">
            <el-form-item label="课程筛选">
              <el-select v-model="searchForm.ctid" placeholder="全部课程" clearable @change="filterQuizzes">
                <el-option label="全部课程" :value="null"></el-option>
                <el-option
                  v-for="course in selectedCourses"
                  :key="course.ctid"
                  :label="course.cname + ' (' + course.term + ')'"
                  :value="course.ctid">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="测验标题">
              <el-input v-model="searchForm.quizTitle" placeholder="输入测验标题" clearable @input="filterQuizzes"></el-input>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="searchForm.status" placeholder="全部状态" clearable @change="filterQuizzes">
                <el-option label="全部状态" :value="null"></el-option>
                <el-option label="未开始" :value="0"></el-option>
                <el-option label="进行中" :value="1"></el-option>
                <el-option label="已结束" :value="2"></el-option>
              </el-select>
            </el-form-item>
          </el-form>

          <!-- 测验表格 -->
          <el-table :data="filteredQuizzes" border stripe style="width: 100%">
            <el-table-column prop="quizTitle" label="测验标题" width="200" show-overflow-tooltip></el-table-column>
            <el-table-column prop="courseName" label="课程" width="150"></el-table-column>
            <el-table-column prop="term" label="学期" width="120"></el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="180">
              <template slot-scope="scope">
                {{ formatDate(scope.row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="180">
              <template slot-scope="scope">
                {{ formatDate(scope.row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="时长(分钟)" width="100"></el-table-column>
            <el-table-column prop="totalScore" label="总分" width="80"></el-table-column>
            <el-table-column label="状态" width="100">
              <template slot-scope="scope">
                <el-tag :type="getStatusType(scope.row)">
                  {{ getStatusText(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template slot-scope="scope">
                <el-button
                  type="primary"
                  size="small"
                  :disabled="!canTakeQuiz(scope.row)"
                  @click="startQuiz(scope.row)">
                  {{ isQuizStarted(scope.row) ? '开始答题' : '暂未开始' }}
                </el-button>
                <el-button
                  type="text"
                  size="small"
                  :disabled="!scope.row.isSubmitted"
                  @click="viewQuizResult(scope.row)">
                  查看结果
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 空状态 -->
          <div v-if="filteredQuizzes.length === 0" style="text-align: center; padding: 40px 0; color: #909399;">
            <i class="el-icon-document" style="font-size: 48px; margin-bottom: 16px;"></i>
            <p>暂无测验</p>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script>
export default {
  name: 'QuizList',
  data() {
    return {
      // 学生ID
      sid: null,

      // 学生已选课程
      selectedCourses: [],

      // 所有测验数据
      allQuizzes: [],

      // 筛选后的测验数据
      filteredQuizzes: [],

      // 搜索表单
      searchForm: {
        ctid: null,
        quizTitle: '',
        status: null
      }
    };
  },
  created() {
    this.sid = sessionStorage.getItem('sid');
    this.loadSelectedCourses();
    this.loadQuizzes();
  },
  methods: {
    // 加载学生已选课程
    loadSelectedCourses() {
      const term = sessionStorage.getItem('currentTerm') || '26-春季学期';
      const that = this;

      axios.get(`http://localhost:10086/SCT/findBySid/${this.sid}/${term}`)
        .then(function(resp) {
          const courses = resp.data;
          that.selectedCourses = courses.map(course => ({
            ctid: course.ctid,
            cid: course.cid,
            cname: course.cname,
            term: term
          }));
        })
        .catch(function(error) {
          console.error('加载课程失败:', error);
          that.$message.error('加载课程失败');
        });
    },

    // 加载测验数据
    loadQuizzes() {
      const that = this;
      axios.get(`http://localhost:10086/quiz/findByStudent/${this.sid}`)
        .then(function(resp) {
          // 为每个测验添加提交状态
          const quizzes = resp.data.map(quiz => {
            return {
              ...quiz,
              isSubmitted: false
            };
          });

          // 检查每个测验是否已提交
          Promise.all(
            quizzes.map(quiz => {
              return new Promise((resolve, reject) => {
                axios.get(`http://localhost:10086/quizScore/findByQuizAndStudent/${quiz.quizId}/${that.sid}`)
                  .then(function(scoreResp) {
                    if (scoreResp.data && scoreResp.data.isSubmitted) {
                      quiz.isSubmitted = true;
                    }
                    resolve(quiz);
                  })
                  .catch(function() {
                    resolve(quiz);
                  });
              });
            })
          ).then(function(updatedQuizzes) {
            that.allQuizzes = updatedQuizzes;
            that.filteredQuizzes = updatedQuizzes;
          });
        })
        .catch(function(error) {
          console.error('加载测验失败:', error);
          that.$message.error('加载测验失败');
        });
    },

    // 过滤测验
    filterQuizzes() {
      let filtered = this.allQuizzes;

      // 按课程筛选
      if (this.searchForm.ctid) {
        filtered = filtered.filter(quiz => quiz.ctid === this.searchForm.ctid);
      }

      // 按标题筛选
      if (this.searchForm.quizTitle) {
        const keyword = this.searchForm.quizTitle.toLowerCase();
        filtered = filtered.filter(quiz =>
          quiz.quizTitle.toLowerCase().includes(keyword)
        );
      }

      // 按状态筛选
      if (this.searchForm.status !== null) {
        filtered = filtered.filter(quiz => {
          const now = new Date();
          const startTime = new Date(quiz.startTime);
          const endTime = new Date(quiz.endTime);

          if (now < startTime) {
            return this.searchForm.status === 0;
          } else if (now >= startTime && now <= endTime) {
            return this.searchForm.status === 1;
          } else {
            return this.searchForm.status === 2;
          }
        });
      }

      this.filteredQuizzes = filtered;
    },

    // 获取状态类型
    getStatusType(quiz) {
      const now = new Date();
      const startTime = new Date(quiz.startTime);
      const endTime = new Date(quiz.endTime);

      if (now < startTime) return 'warning';
      if (now <= endTime) return 'success';
      return 'info';
    },

    // 获取状态文本
    getStatusText(quiz) {
      const now = new Date();
      const startTime = new Date(quiz.startTime);
      const endTime = new Date(quiz.endTime);

      if (now < startTime) return '未开始';
      if (now <= endTime) return '进行中';
      return '已结束';
    },

    // 格式化日期
    formatDate(dateString) {
      if (!dateString) return '';
      const date = new Date(dateString);
      return date.toLocaleString('zh-CN');
    },

    // 检查测验是否已开始
    isQuizStarted(quiz) {
      return new Date() >= new Date(quiz.startTime);
    },

    // 检查是否可以参加测验
    canTakeQuiz(quiz) {
      const now = new Date();
      const startTime = new Date(quiz.startTime);
      const endTime = new Date(quiz.endTime);

      return now >= startTime && now <= endTime && !quiz.isSubmitted;
    },

    // 开始答题
    startQuiz(quiz) {
      if (this.canTakeQuiz(quiz)) {
        this.$router.push({
          path: '/quizTaking',
          query: { quizId: quiz.quizId, sid: this.sid }
        });
      } else if (quiz.isSubmitted) {
        this.$message.warning('您已提交过该测验');
      } else if (new Date() < new Date(quiz.startTime)) {
        this.$message.warning('测验尚未开始');
      } else {
        this.$message.warning('测验已结束');
      }
    },

    // 查看测验结果
    viewQuizResult(quiz) {
      if (!quiz.isSubmitted) {
        this.$message.warning('请先完成测验并提交');
        return;
      }
      this.$router.push({
        path: '/quizResult',
        query: { quizId: quiz.quizId, sid: this.sid }
      });
    }
  }
};
</script>

<style scoped>
</style>
