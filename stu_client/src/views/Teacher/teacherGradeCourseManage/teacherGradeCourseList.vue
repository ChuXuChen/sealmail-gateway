<template>
  <div>
    <common-table
      ref="gradeTable"
      :table-data="tableData"
      :columns="columns"
      :loading="loading"
      :show-index="true"
      :show-selection="false"
      :show-operation="true"
      :operation-width="120"
      :show-pagination="true"
      :pagination="pagination"
      :page-sizes="[7, 15, 30, 50]"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <!-- 操作列插槽 -->
      <template #operation="{ row }">
        <el-button type="primary" size="small" @click="editor(row)">编辑</el-button>
      </template>
    </common-table>

    <div class="mt-15" style="padding: 10px; background: #f5f7fa; border-radius: 8px;">
      <span style="font-weight: 500;">平均成绩：{{ avg.toFixed(1) }}</span>
    </div>
  </div>
</template>

<script>
import request from '@/utils/request'
import CommonTable from '@/components/CommonTable'

export default {
  components: { CommonTable },
  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      avg: 0,
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课程号', width: '100', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '150', align: 'center' },
        { prop: 'sid', label: '学号', width: '100', align: 'center' },
        { prop: 'sname', label: '学生名', width: '120', align: 'center' },
        { prop: 'grade', label: '成绩', width: '100', align: 'center' },
        { prop: 'term', label: '学期', width: '120', align: 'center' }
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      }
    }
  },
  props: {
    ruleForm: Object,
  },
  watch: {
    ruleForm: {
      handler(newRuleForm, oldRuleForm) {
        console.log("组件监听 form")
        console.log(newRuleForm)
        this.loadGradeList(newRuleForm)
      },
      deep: true,
      immediate: true
    }
  },
  methods: {
    select(row) {
      console.log(row)
    },
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
    editor(row) {
      this.$router.push({
        path: '/teacherEditorGradeCourse',
        query: {
          cid: row.cid,
          tid: row.tid,
          sid: row.sid,
          term: row.term
        }
      })
    },
    // 加载成绩列表
    async loadGradeList(searchParams) {
      this.loading = true
      try {
        const resp = await request.post("/SCT/findBySearch", searchParams)
        this.tmpList = resp || []
        this.pagination.total = this.tmpList.length

        // 计算平均成绩
        this.avg = 0
        if (this.tmpList.length > 0) {
          let totalGrade = 0
          this.tmpList.forEach(item => {
            totalGrade += item.grade || 0
          })
          this.avg = totalGrade / this.tmpList.length
        }

        this.handlePagination()
      } catch (error) {
        console.error('加载成绩列表失败:', error)
        this.$message.error('加载成绩列表失败')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>