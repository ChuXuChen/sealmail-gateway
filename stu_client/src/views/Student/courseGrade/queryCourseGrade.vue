<template>
  <div>
    <el-card shadow="hover" class="mb-20">
      <el-form :inline="true" :model="queryForm" label-width="100px">
        <el-form-item label="选择学期">
          <el-select v-model="queryForm.term" placeholder="请选择学期">
            <el-option v-for="(item, index) in termList" :key="index" :label="item" :value="item"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="hover">
      <common-table
        ref="gradeTable"
        :table-data="tableData"
        :columns="columns"
        :loading="loading"
        :show-index="true"
        :show-selection="false"
        :show-operation="false"
        :show-pagination="true"
        :pagination="pagination"
        :page-sizes="[7, 15, 30, 50]"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      >
        <!-- 自定义成绩列样式 -->
        <template #grade="{ row }">
          <el-tag :type="row.grade >= 60 ? 'success' : 'danger'" size="small">
            {{ row.grade }}
          </el-tag>
        </template>
      </common-table>

      <div class="mt-15" style="padding: 10px; background: #f5f7fa; border-radius: 8px;">
        <span style="font-weight: 500;">加权平均成绩：{{ avg.toFixed(1) }}</span>
      </div>
    </el-card>
  </div>
</template>

<script>
import CommonTable from '@/components/CommonTable'

export default {
  components: { CommonTable },
  methods: {
    // 分页处理
    handlePagination() {
      const { page, size } = this.pagination
      const start = (page - 1) * size
      const end = page * size
      this.tableData = this.tmpList.slice(start, end)
    },

    // 页码变化
    handlePageChange(page) {
      this.pagination.page = page
      this.handlePagination()
    },

    // 每页条数变化
    handleSizeChange(size) {
      this.pagination.size = size
      this.pagination.page = 1
      this.handlePagination()
    },
    // 加载成绩列表
    async loadGrades(term) {
      this.loading = true
      try {
        const sid = parseInt(sessionStorage.getItem('sid'))
        const resp = await axios.get('http://localhost:10086/SCT/findBySid/' + sid + '/' + term)
        this.tmpList = resp.data || []
        this.pagination.total = this.tmpList.length

        // 计算加权平均成绩
        this.avg = 0
        let totalCredit = 0
        this.tmpList.forEach(item => {
          if (item.grade !== null && item.grade !== undefined) {
            totalCredit += item.ccredit
            this.avg += item.ccredit * item.grade
          }
        })
        if (totalCredit > 0) {
          this.avg /= totalCredit
        }

        this.handlePagination()
      } catch (error) {
        console.error('加载成绩失败:', error)
        this.$message.error('加载成绩失败')
      } finally {
        this.loading = false
      }
    },
    // 加载学期列表
    async loadTermList() {
      try {
        const resp = await axios.get('http://localhost:10086/SCT/findAllTerm')
        this.termList = resp.data || []
      } catch (error) {
        console.error('加载学期列表失败:', error)
        this.$message.error('加载学期列表失败')
      }
    }
  },
  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      avg: 0,
      termList: [],
      // 查询表单
      queryForm: {
        term: sessionStorage.getItem('currentTerm')
      },
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课号', width: '100', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '180', align: 'center' },
        { prop: 'tid', label: '教师号', width: '100', align: 'center' },
        { prop: 'tname', label: '教师名称', minWidth: '150', align: 'center' },
        { prop: 'ccredit', label: '学分', width: '100', align: 'center' },
        { prop: 'grade', label: '成绩', width: '120', align: 'center', slot: true }
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      }
    }
  },
  created() {
    this.loadTermList()
  },
  watch: {
    'queryForm.term': {
      handler(newTerm) {
        if (newTerm) {
          this.loadGrades(newTerm)
        }
      },
      immediate: true
    }
  }
}
</script>

<style scoped>
.mb-20 {
  margin-bottom: 20px;
}
.mt-15 {
  margin-top: 15px;
}
</style>