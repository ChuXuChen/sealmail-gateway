<template>
  <page-layout
    title="数据统计"
    subtitle="全校成绩统计分析"
    :breadcrumb-list="breadcrumbList"
  >
    <!-- 筛选区域 - 使用封装的SearchForm组件 -->
    <search-form
      :form="filterForm"
      :fields="searchFields"
      :inline="true"
      label-width="80px"
      @search="loadData"
      @reset="handleReset"
      class="filter-card"
    ></search-form>

    <!-- 成绩分布 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="12">
        <el-card title="全校成绩分布" shadow="hover" class="chart-card">
          <div ref="gradePieChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card title="分数段统计" shadow="hover" class="chart-card">
          <div ref="scoreBarChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 课程平均分排行 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card title="课程平均分排行" shadow="hover" class="chart-card">
          <div ref="courseRankChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </page-layout>
</template>

<script>
import * as echarts from 'echarts'
import { getScoreDistribution, getScoreSegment, getCourseAverageRank } from '@/api/statistics'

export default {
  name: 'AdminStatistics',
  data() {
    return {
      // 面包屑配置
      breadcrumbList: [
        { name: '首页', path: '/admin' },
        { name: '数据统计', path: '' }
      ],
      // 搜索表单配置
      searchFields: [
        {
          prop: 'term',
          label: '学期',
          type: 'select',
          options: [],
          placeholder: '请选择学期',
          width: '180px'
        },
        {
          prop: 'ctid',
          label: '课程',
          type: 'select',
          options: [],
          placeholder: '全部课程',
          clearable: true,
          width: '280px'
        }
      ],
      filterForm: {
        term: sessionStorage.getItem('currentTerm') || '',
        ctid: null
      },
      gradePieChart: null,
      scoreBarChart: null,
      courseRankChart: null
    }
  },
  created() {
    this.loadTerms()
  },
  watch: {
    'filterForm.term'(newVal) {
      if (newVal) {
        this.loadCourseList()
        this.loadData()
      }
    },
    'filterForm.ctid'() {
      this.loadData()
    }
  },
  mounted() {
    this.initCharts()
    this.loadData()
  },
  beforeDestroy() {
    if (this.gradePieChart) this.gradePieChart.dispose()
    if (this.scoreBarChart) this.scoreBarChart.dispose()
    if (this.courseRankChart) this.courseRankChart.dispose()
  },
  methods: {
    // 初始化图表
    initCharts() {
      this.gradePieChart = echarts.init(this.$refs.gradePieChart)
      this.scoreBarChart = echarts.init(this.$refs.scoreBarChart)
      this.courseRankChart = echarts.init(this.$refs.courseRankChart)

      // 响应式
      window.addEventListener('resize', () => {
        this.gradePieChart.resize()
        this.scoreBarChart.resize()
        this.courseRankChart.resize()
      })
    },

    // 加载学期列表
    async loadTerms() {
      try {
        const res = await this.$request.get('/SCT/findAllTerm')
        const termList = res || []
        // 更新搜索表单的学期选项
        this.searchFields[0].options = termList.map(term => ({
          label: term,
          value: term
        }))
        if (termList.length > 0 && !this.filterForm.term) {
          this.filterForm.term = termList[0]
        }
        if (termList.length > 0 && this.filterForm.term) {
          this.loadCourseList()
        }
      } catch (error) {
        console.error('加载学期失败', error)
        this.$message.error('加载学期列表失败')
      }
    },
    // 加载当前学期的所有开课列表
    async loadCourseList() {
      if (!this.filterForm.term) return
      try {
        const params = { term: this.filterForm.term }
        const res = await this.$request.post('/courseTeacher/findCourseTeacherInfo', params)
        const courseList = res || []
        // 更新搜索表单的课程选项
        this.searchFields[1].options = courseList.map(course => ({
          label: `${course.cname}(${course.tname})`,
          value: course.ctid
        }))
      } catch (error) {
        console.error('加载课程列表失败', error)
        this.$message.error('加载课程列表失败')
      }
    },

    // 加载所有统计数据
    loadData() {
      this.loadGradeDistribution()
      this.loadScoreSegment()
      this.loadCourseRank()
    },

    // 加载成绩分布
    async loadGradeDistribution() {
      try {
        const params = {}
        if (this.filterForm.term) params.term = this.filterForm.term
        if (this.filterForm.ctid) params.ctid = this.filterForm.ctid

        const data = await getScoreDistribution(params)
        const option = {
          tooltip: {
            trigger: 'item',
            formatter: '{a} <br/>{b}: {c}人 ({d}%)'
          },
          legend: {
            orient: 'vertical',
            left: 'left'
          },
          color: ['#67C23A', '#409EFF', '#E6A23C', '#909399', '#F56C6C'],
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
      } catch (error) {
        console.error('加载成绩分布失败', error)
        this.$message.error('加载成绩分布失败')
      }
    },

    // 加载分数段统计
    async loadScoreSegment() {
      try {
        const params = {}
        if (this.filterForm.term) params.term = this.filterForm.term
        if (this.filterForm.ctid) params.ctid = this.filterForm.ctid

        const data = await getScoreSegment(params)
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
      } catch (error) {
        console.error('加载分数段统计失败', error)
        this.$message.error('加载分数段统计失败')
      }
    },

    // 加载课程平均分排行
    async loadCourseRank() {
      try {
        const params = { limit: 10 }
        if (this.filterForm.term) params.term = this.filterForm.term

        const data = await getCourseAverageRank(params)
        const option = {
          tooltip: {
            trigger: 'axis',
            axisPointer: {
              type: 'shadow'
            },
            formatter: '{b}<br/>平均分：{c}分'
          },
          grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true
          },
          xAxis: {
            type: 'value',
            name: '平均分',
            boundaryGap: [0, 0.01]
          },
          yAxis: {
            type: 'category',
            data: data.map(item => item.courseName).reverse(),
            axisLabel: {
              interval: 0,
              width: 150,
              overflow: 'truncate'
            }
          },
          series: [
            {
              name: '平均分',
              type: 'bar',
              data: data.map(item => item.averageScore).reverse(),
              itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                  { offset: 0, color: '#67C23A' },
                  { offset: 1, color: '#90EE90' }
                ])
              },
              label: {
                show: true,
                position: 'right',
                formatter: '{c}分'
              }
            }
          ]
        }
        this.courseRankChart.setOption(option)
      } catch (error) {
        console.error('加载课程排行失败', error)
        this.$message.error('加载课程排行失败')
      }
    },

    // 重置筛选
    handleReset() {
      this.filterForm.ctid = null
      this.loadData()
    }
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.chart-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 16px 0 rgba(0, 0, 0, 0.1);
}

.chart-container {
  width: 100%;
  height: 400px;
}
</style>
