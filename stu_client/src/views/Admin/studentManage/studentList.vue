<template>
  <div class="student-list-page">
    <!-- 操作按钮区 -->
    <div style="margin-bottom: 16px;">
      <el-button type="primary" icon="el-icon-plus" @click="openAddDialog">添加学生</el-button>
    </div>

    <!-- 通用表格组件 -->
    <common-table
      ref="studentTable"
      :table-data="tableData"
      :columns="columns"
      :loading="loading"
      :show-index="true"
      :show-selection="true"
      :show-operation="true"
      :operation-width="250"
      :show-pagination="true"
      :pagination="pagination"
      :page-sizes="[7, 15, 30, 50]"
      @selection-change="handleSelectionChange"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <!-- 操作列插槽 -->
      <template #operation="{ row }">
        <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
        <el-popconfirm
          confirm-button-text="删除"
          cancel-button-text="取消"
          icon="el-icon-info"
          icon-color="red"
          title="删除不可复原，确定要删除该学生吗？"
          @confirm="handleDelete(row)"
        >
          <el-button slot="reference" type="text" size="small" style="color: #F56C6C">删除</el-button>
        </el-popconfirm>
        <el-popconfirm
          confirm-button-text="确认"
          cancel-button-text="取消"
          icon="el-icon-info"
          icon-color="#409EFF"
          title="确定要将密码重置为 '123' 吗？"
          @confirm="handleResetPassword(row)"
        >
          <el-button slot="reference" type="text" size="small">重置密码</el-button>
        </el-popconfirm>
        <el-button type="text" size="small" @click="handleSendMessage(row)">发送私信</el-button>
      </template>
    </common-table>

    <!-- 发送私信弹窗 - 使用封装的CommonDialog组件 -->
    <common-dialog
      title="发送私信给学生"
      :visible.sync="sendDialogVisible"
      width="500px"
      :confirm-loading="sendLoading"
      @confirm="handleSubmitSendMessage"
    >
      <el-form :model="sendForm" ref="sendForm" label-width="80px" :rules="sendRules">
        <el-form-item label="收件人">
          <span class="text-primary font-bold">{{ sendForm.targetName }}</span>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" placeholder="请输入消息标题"></el-input>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input type="textarea" :rows="4" v-model="sendForm.content" placeholder="请输入消息内容"></el-input>
        </el-form-item>
      </el-form>
    </common-dialog>

    <!-- 添加学生弹窗 -->
    <common-dialog
      title="添加学生"
      :visible.sync="addDialogVisible"
      width="500px"
      :confirm-loading="addLoading"
      @confirm="handleSubmitAdd"
    >
      <el-form :model="addForm" ref="addForm" label-width="100px" :rules="addRules">
        <el-form-item label="学生姓名" prop="sname">
          <el-input v-model="addForm.sname"></el-input>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="addForm.password" show-password placeholder="请输入初始密码"></el-input>
        </el-form-item>
      </el-form>
    </common-dialog>
  </div>
</template>

<script>
import { getStudentList, deleteStudent, resetPassword } from '@/api/student'

export default {
  name: 'StudentList',
  data() {
    return {
      loading: false,
      tableData: [],
      tmpList: [],
      // 表格列配置（与数据库s表字段匹配：仅sid、sname）
      columns: [
        { prop: 'sid', label: '学号', width: '250', align: 'center' },
        { prop: 'sname', label: '姓名', align: 'center' } // 自动填充剩余宽度，无空白
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      },
      // 选中的学生
      selectedStudents: [],
      // 发送私信相关
      sendDialogVisible: false,
      sendLoading: false,
      sendForm: {
        targetId: null,
        targetUserType: 1, // 学生类型
        targetName: '',
        title: '',
        content: ''
      },
      sendRules: {
        title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
        content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
      },
      // 添加学生相关
      addDialogVisible: false,
      addLoading: false,
      addForm: {
        sname: '',
        password: ''
      },
      addRules: {
        sname: [
          { required: true, message: '请输入学生姓名', trigger: 'blur' },
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
        this.loadStudentList()
      },
      deep: true,
      immediate: true
    }
  },
  methods: {
    // 加载学生列表
    async loadStudentList() {
      this.loading = true
      try {
        const params = { ...this.ruleForm }
        if (params.fuzzy !== undefined) {
          params.fuzzy = params.fuzzy ? "true" : "false"
        }
        const res = await getStudentList(params)
        this.tmpList = res || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载学生列表失败:', error)
        this.$message.error('加载学生列表失败')
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

    // 选择变化
    handleSelectionChange(selection) {
      this.selectedStudents = selection
    },

    // 编辑学生
    handleEdit(row) {
      this.$router.push({
        path: '/editorStudent',
        query: { sid: row.sid }
      })
    },

    // 删除学生
    async handleDelete(row) {
      try {
        const res = await deleteStudent(row.sid)
        if (res === true) {
          this.$message.success('删除成功')
          this.loadStudentList()
        } else {
          this.$message.error('删除失败')
        }
      } catch (error) {
        console.error('删除学生失败:', error)
        this.$message.error('删除失败，可能存在外键依赖')
      }
    },

    // 重置密码
    async handleResetPassword(row) {
      try {
        const res = await resetPassword(row.sid)
        if (res === true) {
          this.$message.success('密码重置成功，新密码为：123')
        } else {
          this.$message.error('密码重置失败')
        }
      } catch (error) {
        console.error('重置密码失败:', error)
        this.$message.error('重置密码失败')
      }
    },

    // 发送私信
    handleSendMessage(row) {
      this.sendDialogVisible = true
      this.sendForm = {
        targetId: row.sid,
        targetUserType: 1,
        targetName: row.sname,
        title: '',
        content: ''
      }
      this.$nextTick(() => {
        this.$refs.sendForm.clearValidate()
      })
    },

    // 提交发送私信
    async handleSubmitSendMessage() {
      try {
        const valid = await this.$refs.sendForm.validate()
        if (!valid) return

        this.sendLoading = true
        const senderId = this.$store.getters.userId || sessionStorage.getItem('tid')
        if (!senderId) {
          this.$message.error('无法获取用户信息，请重新登录')
          return
        }

        const params = {
          senderId: parseInt(senderId),
          senderType: 3, // 管理员类型
          targetId: this.sendForm.targetId,
          targetUserType: this.sendForm.targetUserType,
          title: this.sendForm.title,
          content: this.sendForm.content
        }

        await this.$request.post('/message/sendPrivate', params)
        this.$message.success('私信发送成功')
        this.sendDialogVisible = false
      } catch (error) {
        console.error('发送私信失败:', error)
        this.$message.error('发送失败，请稍后重试')
      } finally {
        this.sendLoading = false
      }
    },


    // 打开添加学生弹窗
    openAddDialog() {
      this.addDialogVisible = true
      this.addForm = {
        sname: '',
        password: ''
      }
      this.$nextTick(() => {
        this.$refs.addForm.clearValidate()
      })
    },

    // 提交添加学生
    async handleSubmitAdd() {
      try {
        const valid = await this.$refs.addForm.validate()
        if (!valid) return

        this.addLoading = true
        const res = await this.$request.post("/student/addStudent", this.addForm)
        if (res === true) {
          this.$message.success('添加学生成功')
          this.addDialogVisible = false
          // 刷新列表
          this.loadStudentList()
        } else {
          this.$message.error('添加失败，请检查数据')
        }
      } catch (error) {
        console.error('添加学生失败:', error)
        this.$message.error('添加失败，请稍后重试')
      } finally {
        this.addLoading = false
      }
    }
  }
}
</script>

<style scoped>
.student-list-page {
  padding: 0;
}
</style>