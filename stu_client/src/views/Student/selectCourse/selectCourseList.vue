<template>
  <div>
    <common-table
      ref="courseTable"
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
        <el-popconfirm
            confirm-button-text='选择'
            cancel-button-text='取消'
            icon="el-icon-info"
            title="确定选择该教师开设的课程？"
            @confirm="select(row)"
        >
          <el-button slot="reference" type="primary" size="small">选择</el-button>
        </el-popconfirm>
      </template>
    </common-table>
  </div>
</template>

<script>
import CommonTable from '@/components/CommonTable'

export default {
  components: { CommonTable },
  methods: {
    select(row) {
      console.log(row)
      const cid = row.cid
      const tid = row.tid
      const sid = parseInt(sessionStorage.getItem('sid'))
      const term = sessionStorage.getItem('currentTerm')
      const sct = {
        cid: cid,
        tid: tid,
        sid: sid,
        term: term
      }
      const that = this
      axios.post('http://localhost:10086/SCT/save', sct).then(function (resp) {
        if (resp.data === '选课成功') {
          that.$message({
            showClose: true,
            message: '选课成功',
            type: 'success'
          });
          // 重新加载课程列表
          that.loadCourseList(that.ruleForm)
        }
        else {
          that.$message({
            showClose: true,
            message: resp.data,
            type: 'error'
          });
        }
      })

    },
    deleteCourseTeacher(row) {
      const that = this
      axios.post('http://localhost:10086/courseTeacher/deleteById', row).then(function (resp) {
        if (resp.data === true) {
          that.$message({
            showClose: true,
            message: '删除成功',
            type: 'success'
          });
          window.location.reload()
        }
        else {
          that.$message({
            showClose: true,
            message: '删除出错，请查询数据库连接',
            type: 'error'
          });
        }
      })
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
    // 加载课程列表
    async loadCourseList(searchParams) {
      this.loading = true
      try {
        const resp = await axios.post("http://localhost:10086/courseTeacher/findCourseTeacherInfo", searchParams)
        this.tmpList = resp.data || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载课程列表失败:', error)
        this.$message.error('加载课程列表失败')
      } finally {
        this.loading = false
      }
    }
  },

  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      type: sessionStorage.getItem('type'),
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课号', width: '100', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '180', align: 'center' },
        { prop: 'tid', label: '教师号', width: '100', align: 'center' },
        { prop: 'tname', label: '教师名称', minWidth: '150', align: 'center' },
        { prop: 'ccredit', label: '学分', width: '100', align: 'center' }
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
    ruleForm: Object
  },
  watch: {
    ruleForm: {
      handler(newRuleForm, oldRuleForm) {
        this.loadCourseList(newRuleForm)
      },
      deep: true,
      immediate: true
    }
  }
}
</script>