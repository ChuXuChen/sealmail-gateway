<template>
  <div class="admin-layout">
    <el-container class="main-container">
      <!-- 顶部导航栏 -->
      <el-header class="main-header">
        <div class="header-left">
          <div class="system-title">
            <i class="el-icon-s-management"></i>
            <span>管理员后台</span>
          </div>
        </div>
        <r-header></r-header>
      </el-header>

      <el-container class="content-container">
        <!-- 侧边栏 -->
        <r-aside></r-aside>

        <!-- 主内容区域 -->
        <el-main class="main-content">
          <transition name="fade-slide" mode="out-in">
            <router-view :key="$route.fullPath"></router-view>
          </transition>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import RAside from "@/components/r-aside";
import RHeader from "@/components/r-header";
export default {
  name: "AdminLayout",
  components: {RHeader, RAside},
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  overflow: hidden;
  background: #F8FAFC;
}
.main-container {
  height: 100%;
}
.main-header {
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.95) 0%, rgba(37, 99, 235, 0.95) 100%);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0;
  height: 60px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  color: #fff;
}
.header-left {
  padding: 0 20px;
  height: 100%;
  display: flex;
  align-items: center;
}
.system-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}
.system-title i {
  margin-right: 8px;
  font-size: 20px;
}
.content-container {
  height: calc(100vh - 60px);
}
.main-content {
  padding: 0;
  background: #F8FAFC;
  overflow: hidden;
}
/* 页面切换动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>