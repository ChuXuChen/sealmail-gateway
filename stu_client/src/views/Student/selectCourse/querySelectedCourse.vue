<template>
  <div>
    <el-card shadow="hover">
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
              confirm-button-text='退课'
              cancel-button-text='取消'
              icon="el-icon-info"
              title="确定退课？"
              @confirm="deleteSCT(row)"
          >
            <el-button slot="reference" type="danger" size="small">退课</el-button>
          </el-popconfirm>
        </template>
      </common-table>
    </el-card>
  </div>
</template>

<script>
import CommonTable from '@/components/CommonTable'

export default {
  components: { CommonTable },
  methods: {
    deleteSCT(row) {
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
      axios.post('http://localhost:10086/SCT/deleteBySCT', sct).then(function (resp) {
        if (resp.data === true) {
          that.$message({
            showClose: true,
            message: '退课成功',
            type: 'success'
          });
          // 重新加载课程列表
          that.loadSelectedCourses()
        }
        else {
          that.$message({
            showClose: true,
            message: '退课失败，请联系管理员',
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
    // 加载已选课程列表
    async loadSelectedCourses() {
      this.loading = true
      try {
        const sid = parseInt(sessionStorage.getItem('sid'))
        const term = sessionStorage.getItem('currentTerm')
        const resp = await axios.get('http://localhost:10086/SCT/findBySid/' + sid + '/' + term)
        this.tmpList = resp.data || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载已选课程失败:', error)
        this.$message.error('加载已选课程失败')
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
  created() {
    this.loadSelectedCourses()
  }
}
</script>