<template>
  <el-card class="info-card hover-lift">
    <div slot="header" class="card-header">
      <i class="el-icon-user" style="margin-right: 8px; color: #3B82F6;"></i>
      个人信息
    </div>
    <el-descriptions :column="3" border>
      <el-descriptions-item label="用户ID">
        <span class="text-semibold">{{ id }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="用户名">
        <span class="text-semibold">{{ name }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="用户类型">
        <el-tag :type="tagType" size="medium">{{ typeText }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script>
export default {
  name: "InfoCard",
  data() {
    return {
      id: sessionStorage.getItem('type') === 'teacher' ? sessionStorage.getItem('tid') : sessionStorage.getItem('sid'),
      name: sessionStorage.getItem('name'),
      type: sessionStorage.getItem('type')
    }
  },
  computed: {
    typeText() {
      const typeMap = {
        'student': '学生',
        'teacher': '教师',
        'admin': '管理员'
      }
      return typeMap[this.type] || this.type
    },
    tagType() {
      const typeMap = {
        'student': 'success',
        'teacher': 'warning',
        'admin': 'danger'
      }
      return typeMap[this.type] || 'info'
    }
  }
}
</script>

<style scoped>
.info-card {
  border-radius: 12px;
}
.card-header {
  font-weight: 600;
  font-size: 16px;
  color: #1F2937;
}
.text-semibold {
  font-weight: 600;
  color: #1F2937;
}
</style>