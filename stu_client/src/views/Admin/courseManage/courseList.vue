<template>
  <div class="course-list-page">
    <!-- 操作按钮区 -->
    <div style="margin-bottom: 16px;">
      <el-button type="primary" icon="el-icon-plus" @click="openAddDialog">添加课程</el-button>
    </div>

    <!-- 通用表格组件 -->
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
        <el-button type="text" size="small" @click="editor(row)">编辑</el-button>
        <el-popconfirm
          confirm-button-text='删除'
          cancel-button-text='取消'
          icon="el-icon-info"
          icon-color="red"
          title="删除不可复原"
          @confirm="deleteCourse(row)"
        >
          <el-button slot="reference" type="text" size="small" style="color: #F56C6C">删除</el-button>
        </el-popconfirm>
      </template>
    </common-table>

    <!-- 添加课程弹窗 -->
    <el-dialog
      title="添加课程"
      :visible.sync="addDialogVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="addForm" ref="addForm" label-width="100px" :rules="addRules">
        <el-form-item label="课程名" prop="cname">
          <el-input v-model="addForm.cname"></el-input>
        </el-form-item>
        <el-form-item label="学分" prop="ccredit">
          <el-input v-model.number="addForm.ccredit"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleSubmitAdd">提交</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
export default {
  methods: {
    select(row) {
      console.log(row)
    },
    // 加载课程列表
    async loadCourseList() {
      this.loading = true
      try {
        const resp = await request.post("/course/findBySearch", this.ruleForm)
        this.tmpList = resp || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载课程列表失败:', error)
        this.$message.error('加载课程列表失败')
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

    deleteCourse(row) {
      const that = this
      request.get('/course/deleteById/' + row.cid).then(function (resp) {
        console.log(resp)
        if (resp === true) {
          that.$message({
            showClose: true,
            message: '删除成功',
            type: 'success'
          });
          that.loadCourseList()
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
    offer(row) {
      const tid = sessionStorage.getItem("tid")
      const cid = row.cid
      const term = sessionStorage.getItem("currentTerm")

      const that = this
      request.get('/courseTeacher/insert/' + cid + '/' + tid + '/' + term).then(function (resp) {
        if (resp === true) {
          that.$message({
            showClose: true,
            message: '开设成功',
            type: 'success'
          });
          that.loadCourseList()
        }
        else {
          that.$message({
            showClose: true,
            message: '开设失败，请联系管理员',
            type: 'error'
          });
        }
      })


    },
    editor(row) {
      this.$router.push({
        path: '/editorCourse',
        query: {
          cid: row.cid
        }
      })
    },

    // 打开添加课程弹窗
    openAddDialog() {
      this.addDialogVisible = true
      this.addForm = {
        cname: null,
        ccredit: null
      }
      this.$nextTick(() => {
        this.$refs.addForm.clearValidate()
      })
    },

    // 提交添加课程
    handleSubmitAdd() {
      this.$refs.addForm.validate((valid) => {
        if (valid) {
          this.addLoading = true
          const that = this
          request.post("/course/save", this.addForm).then(function (resp) {
            if (resp === true) {
              that.$message.success('添加课程成功')
              that.addDialogVisible = false
              // 刷新列表
              that.loadCourseList()
            } else {
              that.$message.error('添加失败，请检查数据')
            }
          }).catch(function () {
            that.$message.error('添加失败，请稍后重试')
          }).finally(function () {
            that.addLoading = false
          })
        } else {
          return false
        }
      })
    }
  },
  created() {
    console.log(this.type)
  },
  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      type: sessionStorage.getItem("type"),
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课程号', width: '150', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '200', align: 'center' },
        { prop: 'ccredit', label: '学分', width: '120', align: 'center' }
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      },
      // 添加课程相关
      addDialogVisible: false,
      addLoading: false,
      addForm: {
        cname: null,
        ccredit: null
      },
      addRules: {
        cname: [
          { required: true, message: '请输入课程名', trigger: 'blur' },
        ],
        ccredit: [
          { required: true, message: '请输入学分', trigger: 'change' },
          { type: 'number', message: '请输入数字', trigger: 'blur' },
        ],
      }
    }
  },
  props: {
    ruleForm: Object,
    isActive: Boolean
  },
  watch: {
    ruleForm: {
      handler() {
        this.loadCourseList()
      },
      deep: true,
      immediate: true
    }
  },
}
</script>