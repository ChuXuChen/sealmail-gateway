<template>
  <div class="course-teacher-list-page">
    <!-- 通用表格组件 -->
    <common-table
      ref="courseTeacherTable"
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
        <el-button type="text" size="small" @click="editCourseTeacher(row)">修改</el-button>
        <el-popconfirm
          confirm-button-text='删除'
          cancel-button-text='取消'
          icon="el-icon-info"
          icon-color="red"
          title="删除不可复原"
          @confirm="deleteCourseTeacher(row)"
        >
          <el-button slot="reference" type="text" size="small" style="color: #F56C6C">删除</el-button>
        </el-popconfirm>
      </template>
    </common-table>
  </div>
</template>

<script>
import request from '@/utils/request'
export default {
  methods: {
    // 加载开课列表
    async loadCourseTeacherList() {
      this.loading = true
      try {
        const resp = await request.post("/courseTeacher/findCourseTeacherInfo", this.ruleForm)
        console.log("开课列表数据:", resp)
        if (resp && resp.length > 0) {
          console.log("第一条数据字段:", Object.keys(resp[0]))
        }
        this.tmpList = resp || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载开课列表失败:', error)
        this.$message.error('加载开课列表失败')
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

    select(row) {
      console.log(row)
      const cid = row.cid
      const tid = row.tid
      const sid = sessionStorage.getItem('sid')
      const term = sessionStorage.getItem('currentTerm')
      const sct = {
        cid: cid,
        tid: tid,
        sid: sid,
        term: term
      }
      const that = this
      axios.post('http://localhost:10086/SCT/save', sct).then(function (resp) {
        if (resp.data === true) {
          that.$message({
            showClose: true,
            message: '选课成功',
            type: 'success'
          });
        }
        else {
          that.$message({
            showClose: true,
            message: '选课出错，请联系管理员',
            type: 'error'
          });
        }
      })

    },
    deleteCourseTeacher(row) {
      const that = this
      // 添加学期参数
      row.term = sessionStorage.getItem('currentTerm')
      request.post('/courseTeacher/deleteById', row).then(function (resp) {
        if (resp === true) {
          that.$message({
            showClose: true,
            message: '删除成功',
            type: 'success'
          });
          that.loadCourseTeacherList()
        }
        else {
          that.$message({
            showClose: true,
            message: '删除失败：该课程教师关联有学生选课记录，请先让学生退课后再删除',
            type: 'error'
          });
        }
      }).catch(function () {
        that.$message({
          showClose: true,
          message: '删除失败：该课程教师关联有学生选课记录，请先让学生退课后再删除',
          type: 'error'
        });
      })
    },
    editCourseTeacher(row) {
      const that = this
      console.log("修改开课记录:", row)
      console.log("row对象字段:", Object.keys(row))
      // 确保有ctid字段
      if (row.ctid) {
        that.$router.push({
          path: '/editorCourseTeacher',
          query: {
            ctid: row.ctid
          }
        })
      } else {
        that.$message.error('无法修改：缺少开课记录ID，row字段: ' + Object.keys(row).join(', '));
      }
    },
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
        { prop: 'cname', label: '课程名称', align: 'center' },
        { prop: 'tid', label: '教师号', width: '100', align: 'center' },
        { prop: 'tname', label: '教师名称', align: 'center' }
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
      handler() {
        this.loadCourseTeacherList()
      },
      deep: true,
      immediate: true
    }
  },
}
</script>