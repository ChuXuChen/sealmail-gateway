<template>
  <page-layout
    title="教师首页"
    subtitle="欢迎使用智能学生管理系统"
    :breadcrumb-list="breadcrumbList"
  >
    <!-- 欢迎提示 -->
    <el-alert
      title="欢迎回来，老师！"
      type="info"
      description="您可以在这里管理您的课程、学生和成绩信息。"
      show-icon
      :closable="false"
      class="mb-24"
    />

    <!-- 快捷功能卡片 -->
    <div class="quick-actions mb-24">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/myOfferCourse')">
            <div class="action-icon text-primary">
              <i class="el-icon-s-management"></i>
            </div>
            <div class="action-title">我的课程</div>
            <div class="action-desc">查看和管理您教授的课程</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/teacherQueryGradeCourseManage')">
            <div class="action-icon text-success">
              <i class="el-icon-s-order"></i>
            </div>
            <div class="action-title">成绩管理</div>
            <div class="action-desc">录入和管理学生课程成绩</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/teacherResourceManage')">
            <div class="action-icon text-warning">
              <i class="el-icon-folder"></i>
            </div>
            <div class="action-title">资源管理</div>
            <div class="action-desc">上传和管理课程学习资源</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card hover-lift" @click.native="$router.push('/quizManage')">
            <div class="action-icon text-danger">
              <i class="el-icon-edit"></i>
            </div>
            <div class="action-title">测验管理</div>
            <div class="action-desc">创建和管理课程在线测验</div>
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
  name: "TeacherHome",
  components: { InfoCard, Logout },
  data() {
    return {
      breadcrumbList: [
        { name: "首页", path: "/" },
        { name: "教师首页", path: "/teacherHome" }
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
