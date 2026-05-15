<template>
  <div class="statistics-page">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" label-width="80px">
        <el-form-item label="学期">
          <el-select v-model="filterForm.term" placeholder="请选择学期" @change="loadData">
            <el-option
              v-for="term in termList"
              :key="term"
              :label="term"
              :value="term">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="filterForm.ctid" placeholder="全部课程" @change="loadData" clearable>
            <el-option
              v-for="course in teacherCourses"
              :key="course.ctid"
              :label="course.cname"
              :value="course.ctid">
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 课程统计概览卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6" v-for="(stats, index) in courseStats" :key="index">
        <el-card :body-style="{ padding: '20px' }">
          <div class="stats-card">
            <div class="stats-title">{{ stats.courseName }}</div>
            <div class="stats-value">{{ stats.averageScore }}</div>
            <div class="stats-label">平均分</div>
            <div class="stats-footer">
              <span style="color: #67C23A;">及格率：{{ stats.passRate }}%</span>
              <span style="float: right;">学生数：{{ stats.studentCount }}人</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 成绩分布和分数段 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="12">
        <el-card title="成绩分布">
          <div ref="gradePieChart" class="chart-container" style="height: 400px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card title="分数段统计">
          <div ref="scoreBarChart" class="chart-container" style="height: 400px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'TeacherStatistics',
  data() {
    return {
      filterForm: {
        term: sessionStorage.getItem('currentTerm') || '',
        ctid: null
      },
      termList: [],
      teacherCourses: [],
      courseStats: [],
      gradePieChart: null,
      scoreBarChart: null
    }
  },
  created() {
    this.loadTerms()
  },
  watch: {
    'filterForm.term': {
      handler(newVal) {
        if (newVal) {
          this.loadTeacherCourses()
          this.loadData()
        }
      },
      immediate: true
    }
  },
  mounted() {
    this.initCharts()
    this.loadData()
  },
  beforeDestroy() {
    if (this.gradePieChart) this.gradePieChart.dispose()
    if (this.scoreBarChart) this.scoreBarChart.dispose()
  },
  methods: {
    // 初始化图表
    initCharts() {
      this.gradePieChart = echarts.init(this.$refs.gradePieChart)
      this.scoreBarChart = echarts.init(this.$refs.scoreBarChart)

      // 响应式
      window.addEventListener('resize', () => {
        this.gradePieChart.resize()
        this.scoreBarChart.resize()
      })
    },

    // 加载学期列表
    loadTerms() {
      axios.get('http://localhost:10086/SCT/findAllTerm')
        .then(res => {
          this.termList = res.data
          if (this.termList.length > 0 && !this.filterForm.term) {
            this.filterForm.term = this.termList[0]
          }
        })
        .catch(err => {
          console.error('加载学期失败', err)
        })
    },

    // 加载教师授课课程列表
    loadTeacherCourses() {
      const tid = sessionStorage.getItem('tid')
      if (!tid || !this.filterForm.term) return

      // 调用专门的查询教师自己课程的接口，保证只会返回自己的课程
      axios.get(`http://localhost:10086/courseTeacher/findMyCourse/${tid}/${this.filterForm.term}`)
        .then(res => {
          this.teacherCourses = res.data.map(course => ({
            ctid: course.ctid,
            cid: course.cid,
            cname: course.cname
          }))
        })
        .catch(err => {
          console.error('加载课程失败', err)
        })
    },

    // 加载所有统计数据
    loadData() {
      this.loadCourseStats()
      this.loadGradeDistribution()
      this.loadScoreSegment()
    },

    // 加载教师授课课程统计
    loadCourseStats() {
      const tid = sessionStorage.getItem('tid')
      if (!tid) return

      const params = {
        tid: tid
      }
      if (this.filterForm.term) params.term = this.filterForm.term

      axios.get('http://localhost:10086/statistics/teacher/course-stats', { params })
        .then(res => {
          this.courseStats = res.data
        })
        .catch(err => {
          console.error('加载课程统计失败', err)
        })
    },

    // 加载成绩分布
    loadGradeDistribution() {
      const tid = sessionStorage.getItem('tid')
      if (!tid) return

      const params = {
        tid: tid
      }
      if (this.filterForm.term) params.term = this.filterForm.term
      if (this.filterForm.ctid) params.ctid = this.filterForm.ctid

      axios.get('http://localhost:10086/statistics/grade/distribution', { params })
        .then(res => {
          const data = res.data
          const option = {
            tooltip: {
              trigger: 'item',
              formatter: '{a} <br/>{b}: {c}人 ({d}%)'
            },
            legend: {
              orient: 'vertical',
              left: 'left'
            },
            series: [
              {
                name: '成绩分布',
                type: 'pie',
                radius: ['40%', '70%'],
                avoidLabelOverlap: false,
                itemStyle: {
                  borderRadius: 10,
                  borderColor: '#fff',
                  borderWidth: 2
                },
                label: {
                  show: true,
                  formatter: '{b}: {d}%'
                },
                emphasis: {
                  label: {
                    show: true,
                    fontSize: 16,
                    fontWeight: 'bold'
                  }
                },
                labelLine: {
                  show: true
                },
                data: data.map(item => ({
                  name: item.name,
                  value: item.value
                }))
              }
            ]
          }
          this.gradePieChart.setOption(option)
        })
        .catch(err => {
          console.error('加载成绩分布失败', err)
        })
    },

    // 加载分数段统计
    loadScoreSegment() {
      const tid = sessionStorage.getItem('tid')
      if (!tid) return

      const params = {
        tid: tid
      }
      if (this.filterForm.term) params.term = this.filterForm.term
      if (this.filterForm.ctid) params.ctid = this.filterForm.ctid

      axios.get('http://localhost:10086/statistics/grade/score-segment', { params })
        .then(res => {
          const data = res.data
          const option = {
            tooltip: {
              trigger: 'axis',
              axisPointer: {
                type: 'shadow'
              }
            },
            grid: {
              left: '3%',
              right: '4%',
              bottom: '3%',
              containLabel: true
            },
            xAxis: {
              type: 'category',
              data: data.map(item => item.name),
              axisLabel: {
                interval: 0,
                rotate: 0
              }
            },
            yAxis: {
              type: 'value',
              name: '人数'
            },
            series: [
              {
                name: '人数',
                type: 'bar',
                barWidth: '60%',
                data: data.map(item => item.value),
                itemStyle: {
                  color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: '#83bff6' },
                    { offset: 0.5, color: '#188df0' },
                    { offset: 1, color: '#188df0' }
                  ])
                }
              }
            ]
          }
          this.scoreBarChart.setOption(option)
        })
        .catch(err => {
          console.error('加载分数段统计失败', err)
        })
    }
  }
}
</script>

<style scoped>
.statistics-page {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.stats-card {
  text-align: center;
}

.stats-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 10px;
  font-weight: bold;
}

.stats-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stats-label {
  font-size: 14px;
  color: #999;
  margin-bottom: 10px;
}

.stats-footer {
  font-size: 12px;
  color: #666;
  padding-top: 10px;
  border-top: 1px solid #eee;
}

.chart-container {
  width: 100%;
}
</style>
