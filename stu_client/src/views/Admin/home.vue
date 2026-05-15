<template>
  <page-layout
    title="系统概览"
    subtitle="欢迎使用智能学生管理系统"
    :breadcrumb-list="breadcrumbList"
    v-loading="loading"
    element-loading-text="页面加载中..."
    element-loading-background="rgba(245, 247, 250, 0.9)"
  >
    <!-- 欢迎提示 -->
    <el-alert
      title="欢迎回来，管理员！"
      type="success"
      description="这里是系统数据概览，您可以快速查看系统运行情况。"
      show-icon
      :closable="false"
      class="mb-24"
    />

    <!-- 统计卡片 -->
    <div class="stats-card-wrapper">
      <el-row :gutter="20">
        <el-col :span="6" v-for="(item, index) in statsData" :key="index">
          <div class="stats-card" :class="`card-${index + 1}`">
            <div class="card-icon">
              <i :class="item.icon"></i>
            </div>
            <div class="card-content">
              <p class="card-label">{{ item.label }}</p>
              <p class="card-value">{{ item.value }}</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 图表区域 -->
    <div class="chart-wrapper mt-30">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card shadow="hover" class="chart-card hover-lift">
            <div slot="header" class="card-header-flex">
              <span>
                <i class="el-icon-pie-chart" style="margin-right: 8px; color: #667eea;"></i>
                成绩分布统计
              </span>
              <el-select v-model="chartTerm" size="small" placeholder="选择学期" @change="loadChartData">
                <el-option v-for="term in termOptions" :key="term" :label="term" :value="term"></el-option>
              </el-select>
            </div>
            <div id="scoreDistributionChart" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="chart-card hover-lift">
            <div slot="header" class="card-header-flex">
              <span>
                <i class="el-icon-s-data" style="margin-right: 8px; color: #f093fb;"></i>
                课程平均分排名
              </span>
              <el-button type="text" icon="el-icon-refresh" @click="refreshChart">刷新</el-button>
            </div>
            <div id="courseRankChart" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </page-layout>
</template>
<script>
import echarts from '@/plugins/echarts'
import { getSystemOverview, getScoreDistribution, getCourseAverageRank } from '@/api/statistics'

export default {
  name: "AdminHome",
  data() {
    return {
      breadcrumbList: [
        { name: '首页', path: '/' },
        { name: '系统概览', path: '/adminHome' }
      ],
      loading: false,
      statsData: [
        { icon: 'el-icon-user', label: '学生总数', value: 0 },
        { icon: 'el-icon-s-custom', label: '教师总数', value: 0 },
        { icon: 'el-icon-s-management', label: '课程总数', value: 0 },
        { icon: 'el-icon-s-order', label: '选课人次', value: 0 }
      ],
      chartTerm: '',
      termOptions: [],
      scoreChart: null,
      rankChart: null
    }
  },
  mounted() {
    // 先加载基础数据，快速显示页面
    this.loadBasicData()
    // 延迟加载图表，不阻塞首屏渲染
    setTimeout(() => {
      this.initChartsAndData()
    }, 200)
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    // 清理事件监听和图表实例，避免内存泄漏
    window.removeEventListener('resize', this.handleResize)
    if (this.scoreChart) this.scoreChart.dispose()
    if (this.rankChart) this.rankChart.dispose()
  },
  methods: {
    // 加载基础数据，快速显示页面
    async loadBasicData() {
      this.loading = true
      try {
        await Promise.all([
          this.loadTermOptions(),
          this.loadStatsData()
        ])
      } catch (error) {
        this.$message.error('页面数据加载失败')
        console.error(error)
      } finally {
        this.loading = false
      }
    },
    // 延迟加载图表相关内容
    async initChartsAndData() {
      try {
        // 初始化图表
        this.initCharts()
        // 加载图表数据
        await this.loadChartData()
      } catch (error) {
        console.error('图表加载失败', error)
      }
    },
    async loadTermOptions() {
      // 和全局逻辑保持一致，从sessionStorage获取当前学期
      const currentTerm = sessionStorage.getItem('currentTerm')
      this.termOptions = currentTerm ? [currentTerm] : []
      this.chartTerm = currentTerm
    },
    async loadStatsData() {
      // 加载统计卡片数据
      const res = await getSystemOverview()
      this.statsData[0].value = res.studentCount
      this.statsData[1].value = res.teacherCount
      this.statsData[2].value = res.courseCount
      this.statsData[3].value = res.totalSelection
    },
    async loadChartData() {
      if (!this.chartTerm) return
      // 加载图表数据
      const [scoreRes, rankRes] = await Promise.all([
        getScoreDistribution({ term: this.chartTerm }),
        getCourseAverageRank({ term: this.chartTerm })
      ])
      // 更新图表
      this.updateScoreChart(scoreRes)
      this.updateRankChart(rankRes)
    },
    initCharts() {
      // 初始化成绩分布饼图
      this.scoreChart = echarts.init(document.getElementById('scoreDistributionChart'))
      // 初始化课程排名柱状图
      this.rankChart = echarts.init(document.getElementById('courseRankChart'))
    },
    updateScoreChart(data) {
      this.scoreChart.setOption({
        tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'left' },
        series: [
          {
            name: '成绩分布',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            label: { show: false, position: 'center' },
            emphasis: {
              label: { show: true, fontSize: '16', fontWeight: 'bold' }
            },
            labelLine: { show: false },
            data: data || [
              { value: 320, name: '优秀(90-100)' },
              { value: 580, name: '良好(70-89)' },
              { value: 260, name: '及格(60-69)' },
              { value: 80, name: '不及格(<60)' }
            ]
          }
        ]
      })
    },
    updateRankChart(data) {
      this.rankChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'value',
          boundaryGap: [0, 0.01]
        },
        yAxis: {
          type: 'category',
          data: data ? data.map(item => item.courseName) : ['高等数学', '大学英语', '计算机基础', '专业导论', '数据结构']
        },
        series: [
          {
            name: '平均分',
            type: 'bar',
            data: data ? data.map(item => item.averageScore) : [86.5, 78.2, 92.1, 83.7, 79.5],
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#409EFF' },
                { offset: 1, color: '#67C23A' }
              ])
            }
          }
        ]
      })
    },
    refreshChart() {
      this.$message.success('图表已刷新')
      this.loadChartData()
    },
    handleResize() {
      this.scoreChart && this.scoreChart.resize()
      this.rankChart && this.rankChart.resize()
    }
  }
}
</script>

<style scoped>
.stats-card-wrapper {
  margin-bottom: 24px;
}
.stats-card {
  display: flex;
  align-items: center;
  padding: 24px;
  border-radius: 12px;
  color: #fff;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.stats-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(45deg, rgba(255, 255, 255, 0.1) 0%, transparent 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}
.stats-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: 0 15px 30px rgba(0, 0, 0, 0.2);
}
.stats-card:hover::before {
  opacity: 1;
}
.card-1 {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: 1px solid rgba(102, 126, 234, 0.3);
}
.card-2 {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: 1px solid rgba(240, 147, 251, 0.3);
}
.card-3 {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  border: 1px solid rgba(79, 172, 254, 0.3);
}
.card-4 {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  border: 1px solid rgba(67, 233, 123, 0.3);
}
.card-icon {
  font-size: 56px;
  opacity: 0.3;
  margin-right: 24px;
  transition: all 0.3s ease;
}
.stats-card:hover .card-icon {
  opacity: 0.5;
  transform: scale(1.1) rotate(5deg);
}
.card-content {
  flex: 1;
  position: relative;
  z-index: 1;
}
.card-label {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.card-value {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.chart-wrapper {
  width: 100%;
}
.chart-card {
  height: 100%;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 12px;
  overflow: hidden;
}
.chart-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
}
.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
  color: #1F2937;
}
.chart-container {
  width: 100%;
  height: 380px;
  padding: 10px 0;
}
.mt-30 {
  margin-top: 30px;
}
.hover-lift:hover {
  transform: translateY(-3px);
}

/* 数据加载动画 */
@keyframes countUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.card-value {
  animation: countUp 0.8s ease-out forwards;
}
</style>
