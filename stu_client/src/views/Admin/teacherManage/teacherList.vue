<template>
  <div class="teacher-list-page">
    <!-- 操作按钮区 -->
    <div style="margin-bottom: 16px;">
      <el-button type="primary" icon="el-icon-plus" @click="openAddDialog">添加教师</el-button>
    </div>

    <!-- 通用表格组件 -->
    <common-table
      ref="teacherTable"
      :table-data="tableData"
      :columns="columns"
      :loading="loading"
      :show-index="true"
      :show-selection="false"
      :show-operation="true"
      :operation-width="200"
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
        <el-popconfirm
          v-if="row.tname !== 'admin'"
          confirm-button-text='确认'
          cancel-button-text='取消'
          icon="el-icon-info"
          icon-color="#409EFF"
          title="确定要将密码重置为 '123' 吗？"
          @confirm="resetPassword(row)"
        >
          <el-button slot="reference" type="text" size="small">重置密码</el-button>
        </el-popconfirm>
      </template>
    </common-table>

    <!-- 添加教师弹窗 -->
    <el-dialog
      title="添加教师"
      :visible.sync="addDialogVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="addForm" ref="addForm" label-width="100px" :rules="addRules">
        <el-form-item label="教师姓名" prop="tname">
          <el-input v-model="addForm.tname"></el-input>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="addForm.password" show-password placeholder="请输入初始密码"></el-input>
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
    // 加载教师列表
    async loadTeacherList() {
      this.loading = true
      try {
        const resp = await request.post("/teacher/findBySearch", this.ruleForm)
        this.tmpList = resp || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载教师列表失败:', error)
        this.$message.error('加载教师列表失败')
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
      if (row.tname === 'admin') {
        this.$message({
          showClose: true,
          message: 'admin 不可删除',
          type: 'error'
        });
        return
      }
      const that = this
      request.get('/teacher/deleteById/' + row.tid).then(function (resp) {
        if (resp === true) {
          that.$message({
            showClose: true,
            message: '删除成功',
            type: 'success'
          });
          that.loadTeacherList()
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
      if (row.tname === 'admin') {
        this.$message({
          showClose: true,
          message: 'admin 不可编辑',
          type: 'error'
        });
        return
      }
      this.$router.push({
        path: '/editorTeacher',
        query: {
          tid: row.tid
        }
      })
    },
    resetPassword(row) {
      const that = this
      request.get('/teacher/resetPassword/' + row.tid).then(function (resp) {
        if (resp === true) {
          that.$message({
            showClose: true,
            message: '密码重置成功，新密码为：123',
            type: 'success'
          });
        } else {
          that.$message({
            showClose: true,
            message: '密码重置失败',
            type: 'error'
          });
        }
      }).catch(function (error) {
        that.$message({
          showClose: true,
          message: '密码重置出错：' + error.message,
          type: 'error'
        });
      })
    },

    // 打开添加教师弹窗
    openAddDialog() {
      this.addDialogVisible = true
      this.addForm = {
        tname: '',
        password: ''
      }
      this.$nextTick(() => {
        this.$refs.addForm.clearValidate()
      })
    },

    // 提交添加教师
    handleSubmitAdd() {
      this.$refs.addForm.validate((valid) => {
        if (valid) {
          if (this.addForm.tname === 'admin') {
            this.$message.error('admin 不可添加')
            return
          }

          this.addLoading = true
          const that = this
          request.post("/teacher/addTeacher", this.addForm).then(function (resp) {
            if (resp === true) {
              that.$message.success('添加教师成功')
              that.addDialogVisible = false
              // 刷新列表
              that.loadTeacherList()
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

  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      // 表格列配置
      columns: [
        { prop: 'tid', label: '工号', width: '150', align: 'center' },
        { prop: 'tname', label: '姓名', align: 'center' }
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      },
      // 添加教师相关
      addDialogVisible: false,
      addLoading: false,
      addForm: {
        tname: '',
        password: ''
      },
      addRules: {
        tname: [
          { required: true, message: '请输入教师姓名', trigger: 'blur' },
          { min: 2, max: 5, message: '长度在 2 到 5 个字符', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入初始密码', trigger: 'change' }
        ]
      }
    }
  },
  props: {
    ruleForm: Object
  },
  watch: {
    ruleForm: {
      handler() {
        this.loadTeacherList()
      },
      deep: true,
      immediate: true
    }
  },
}
</script>