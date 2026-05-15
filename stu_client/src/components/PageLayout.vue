<template>
  <div class="page-layout animate-fade-in">
    <!-- 面包屑 -->
    <el-breadcrumb separator="/" class="mb-15" v-if="showBreadcrumb && breadcrumbList.length">
      <el-breadcrumb-item :to="item.path" v-for="(item, index) in breadcrumbList" :key="index">
        {{ item.name }}
      </el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 页面头部 -->
    <div class="page-header flex-between mb-20" v-if="showHeader">
      <div>
        <h2 class="page-title">{{ title }}</h2>
        <p class="page-subtitle" v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-extra">
        <slot name="extra"></slot>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="page-content">
      <slot></slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PageLayout',
  props: {
    // 页面标题
    title: {
      type: String,
      default: ''
    },
    // 副标题
    subtitle: {
      type: String,
      default: ''
    },
    // 是否显示面包屑
    showBreadcrumb: {
      type: Boolean,
      default: true
    },
    // 面包屑列表
    breadcrumbList: {
      type: Array,
      default: () => []
    },
    // 是否显示头部
    showHeader: {
      type: Boolean,
      default: true
    }
  }
}
</script>

<style scoped>
.page-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

/* 面包屑样式 */
::v-deep .el-breadcrumb {
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #F3F4F6;
}
::v-deep .el-breadcrumb__item:last-child .el-breadcrumb__inner,
::v-deep .el-breadcrumb__item:last-child .el-breadcrumb__inner a:hover {
  color: #3B82F6;
  font-weight: 500;
}
::v-deep .el-breadcrumb__inner a:hover {
  color: #2563EB;
}

.page-header {
  flex-shrink: 0;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #F3F4F6;
  position: relative;
  overflow: hidden;
}
.page-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #3B82F6 0%, #2563EB 100%);
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1F2937;
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #6B7280;
  margin: 0;
}

.header-extra {
  display: flex;
  align-items: center;
}

.page-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  /* 滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #C1C1C1 #F1F1F1;
}
.page-content::-webkit-scrollbar {
  width: 6px;
}
.page-content::-webkit-scrollbar-track {
  background: #F1F1F1;
  border-radius: 3px;
}
.page-content::-webkit-scrollbar-thumb {
  background: #C1C1C1;
  border-radius: 3px;
}
.page-content::-webkit-scrollbar-thumb:hover {
  background: #A8A8A8;
}
</style>
