<template>
  <page-layout
    title="学生首页"
    subtitle="欢迎使用智能学生管理系统"
    :breadcrumb-list="breadcrumbList"
  >
    <!-- 欢迎提示 -->
    <el-alert
      title="欢迎回来，同学！"
      type="success"
      description="您可以在这里选课、查看成绩和下载学习资源。"
      show-icon
      :closable="false"
      class="mb-24"
    />

    <!-- 快捷功能卡片 -->
    <div class="quick-actions mb-24">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/studentSelectCourse')">
            <div class="action-icon text-primary">
              <i class="el-icon-date"></i>
            </div>
            <div class="action-title">在线选课</div>
            <div class="action-desc">浏览和选择您感兴趣的课程</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/querySelectedCourse')">
            <div class="action-icon text-success">
              <i class="el-icon-notebook-2"></i>
            </div>
            <div class="action-title">我的课表</div>
            <div class="action-desc">查看您已选的课程信息</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/queryCourseGrade')">
            <div class="action-icon text-warning">
              <i class="el-icon-s-order"></i>
            </div>
            <div class="action-title">成绩查询</div>
            <div class="action-desc">查看您的课程考试成绩</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/quizList')">
            <div class="action-icon text-danger">
              <i class="el-icon-edit"></i>
            </div>
            <div class="action-title">在线测验</div>
            <div class="action-desc">参加课程在线测验和考试</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 用户信息卡片 -->
    <info-card></info-card>
  </page-layout>
</template>

<script>
import Logout from "@/components/logout";
import InfoCard from "@/components/infoCard";
export default {
  name: "StudentHome",
  components: { InfoCard, Logout },
  data() {
    return {
      breadcrumbList: [
        { name: "首页", path: "/" },
        { name: "学生首页", path: "/studentHome" }
      ]
    };
  }
};
</script>

<style scoped>
.quick-actions {
  margin-bottom: 24px;
}
.action-card {
  text-align: center;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
}
.action-icon {
  font-size: 48px;
  margin-bottom: 16px;
  transition: all 0.3s ease;
}
.action-card:hover .action-icon {
  transform: scale(1.1);
}
.action-title {
  font-size: 18px;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 8px;
}
.action-desc {
  font-size: 13px;
  color: #6B7280;
}
.mb-24 {
  margin-bottom: 24px;
}
</style>
