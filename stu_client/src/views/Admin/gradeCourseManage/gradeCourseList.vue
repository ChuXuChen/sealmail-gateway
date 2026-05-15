<template>
  <div class="grade-course-list-page">
    <!-- 通用表格组件 -->
    <common-table
      ref="gradeCourseTable"
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
        <el-button type="text" size="small" @click="editor(row)">编辑</el-button>
        <el-popconfirm
          confirm-button-text='删除'
          cancel-button-text='取消'
          icon="el-icon-info"
          icon-color="red"
          title="删除不可复原"
          @confirm="deleteTeacher(row)"
        >
          <el-button slot="reference" type="text" size="small" style="color: #F56C6C">删除</el-button>
        </el-popconfirm>
      </template>
    </common-table>
  </div>
</template>

<script>
export default {
  methods: {
    select(row) {
      console.log(row)
    },
    // 加载成绩列表
    async loadGradeCourseList() {
      this.loading = true
      try {
        console.log("组件监听 form")
        console.log(this.ruleForm)
        const resp = await axios.post("http://localhost:10086/SCT/findBySearch", this.ruleForm)
        console.log("查询结果:");
        console.log(resp)
        this.tmpList = resp.data || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载成绩列表失败:', error)
        this.$message.error('加载成绩列表失败')
      } finally {
        this.loading = false
      }
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

    deleteTeacher(row) {
      const that = this
      console.log(row)
      const sid = row.sid
      const cid = row.cid
      const tid = row.tid
      const term = row.term
      axios.get("http://localhost:10086/SCT/deleteById/" + sid + '/' + cid + '/' + tid + '/' + term).then(function (resp) {
        console.log(resp)
        if (resp.data === true) {
          that.$message({
            showClose: true,
            message: '删除成功',
            type: 'success'
          });
          that.loadGradeCourseList()
        }
        else {
          that.$message({
            showClose: true,
            message: '删除出错，请查询数据库连接',
            type: 'error'
          });
        }
      }).catch(function () {
        that.$message({
          showClose: true,
          message: '删除出错，存在外键依赖',
          type: 'error'
        });
      })
    },
    editor(row) {
      this.$router.push({
        path: '/editorGradeCourse',
        query: {
          cid: row.cid,
          tid: row.tid,
          sid: row.sid,
          term: row.term
        }
      })
    }
  },
  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课程号', width: '100', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '150', align: 'center' },
        { prop: 'tid', label: '工号', width: '80', align: 'center' },
        { prop: 'tname', label: '教师名', minWidth: '120', align: 'center' },
        { prop: 'sid', label: '学号', width: '100', align: 'center' },
        { prop: 'sname', label: '学生名', minWidth: '120', align: 'center' },
        { prop: 'grade', label: '成绩', width: '80', align: 'center' },
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
      handler() {
        this.loadGradeCourseList()
      },
      deep: true,
      immediate: true
    }
  },
}
</script>