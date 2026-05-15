<template>
  <page-layout
    title="我的课程"
    subtitle="查看当前学期开设的课程"
    :breadcrumb-list="breadcrumbList"
  >
    <el-card shadow="hover" class="mb-20">
      <common-table
        ref="courseTable"
        :table-data="tableData"
        :columns="columns"
        :loading="loading"
        :show-index="true"
        :show-selection="false"
        :show-operation="true"
        :operation-width="150"
        :show-pagination="true"
        :pagination="pagination"
        :page-sizes="[7, 15, 30, 50]"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      >
        <!-- 操作列插槽 -->
        <template #operation="{ row }">
          <el-button type="primary" size="small" @click="openMessageDialog(row)">
            发送消息
          </el-button>
        </template>
      </common-table>
    </el-card>

    <!-- 发送消息弹窗 -->
    <el-dialog
      title="发送课程消息"
      :visible.sync="messageDialogVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="messageForm" ref="messageForm" label-width="100px">
        <el-form-item label="课程">
          <span>{{ messageForm.cname }}</span>
        </el-form-item>
        <el-form-item label="消息标题" prop="title" :rules="[{ required: true, message: '请输入标题', trigger: 'blur' }]">
          <el-input v-model="messageForm.title" placeholder="请输入消息标题"></el-input>
        </el-form-item>
        <el-form-item label="消息内容" prop="content" :rules="[{ required: true, message: '请输入内容', trigger: 'blur' }]">
          <el-input type="textarea" :rows="4" v-model="messageForm.content" placeholder="请输入消息内容"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="messageDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sendLoading" @click="sendMessage">发送</el-button>
      </div>
    </el-dialog>
  </page-layout>
</template>

<script>
import PageLayout from '@/components/PageLayout'
import CommonTable from '@/components/CommonTable'

export default {
  components: { PageLayout, CommonTable },
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
    openMessageDialog(row) {
      this.messageForm.ctid = row.ctid
      this.messageForm.cname = row.cname
      this.messageForm.title = ''
      this.messageForm.content = ''
      this.messageDialogVisible = true
    },
    sendMessage() {
      this.$refs.messageForm.validate((valid) => {
        if (valid) {
          this.sendLoading = true
          const that = this
          // 如果 this.tid 为 null，从 sessionStorage 获取
          const tid = this.tid || sessionStorage.getItem("tid")

          if (!tid) {
            this.$message.error("无法获取教师信息，请重新登录")
            this.sendLoading = false
            return
          }

          if (!this.messageForm.ctid) {
            this.$message.error("课程信息缺失，请重新打开对话框")
            this.sendLoading = false
            return
          }

          axios.post('http://localhost:10086/message/sendToCourse', {
            ctid: this.messageForm.ctid,
            title: this.messageForm.title,
            content: this.messageForm.content,
            tid: parseInt(tid)
          }).then(function (resp) {
            if (resp.data.success) {
              that.$message.success('消息发送成功！')
              that.messageDialogVisible = false
            } else {
              that.$message.error(resp.data.message || '发送失败')
            }
          }).catch(function (error) {
            console.error('发送消息失败', error)
            that.$message.error('发送失败，请稍后重试')
          }).finally(function () {
            that.sendLoading = false
          })
        }
      })
    },

    openMessageDialog(row) {
      this.messageForm.ctid = row.ctid
      this.messageForm.cname = row.cname
      this.messageForm.title = ''
      this.messageForm.content = ''
      this.messageDialogVisible = true
      this.$nextTick(() => {
        this.$refs.messageForm.clearValidate()
      })
    }
  },

  data() {
    return {
      // 面包屑导航
      breadcrumbList: [
        { name: '教师首页', path: '/teacherHome' },
        { name: '我的课程', path: '/myOfferCourse' }
      ],
      loading: false,
      tableData: [],
      tmpList: [],
      tid: null,
      term: null,
      sendLoading: false,
      // 表格列配置
      columns: [
        { prop: 'cid', label: '课程号', width: '120', align: 'center' },
        { prop: 'cname', label: '课程名', minWidth: '200', align: 'center' },
        { prop: 'ccredit', label: '学分', width: '100', align: 'center' }
      ],
      // 分页配置
      pagination: {
        page: 1,
        size: 7,
        total: 0
      },
      messageDialogVisible: false,
      messageForm: {
        ctid: null,
        cname: '',
        title: '',
        content: ''
      }
    }
  },
  created() {
    const type = sessionStorage.getItem("type")
    this.tid = (type === "1" || type === "student") ? sessionStorage.getItem("sid") : sessionStorage.getItem("tid")
    this.term = sessionStorage.getItem("currentTerm");
    this.loadCourseList()
  },
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

    // 加载课程列表
    async loadCourseList() {
      this.loading = true
      try {
        const resp = await axios.get('http://localhost:10086/courseTeacher/findMyCourse/' + this.tid + '/' + this.term)
        this.tmpList = resp.data || []
        this.pagination.total = this.tmpList.length
        this.handlePagination()
      } catch (error) {
        console.error('加载课程列表失败:', error)
        this.$message.error('加载课程列表失败')
      } finally {
        this.loading = false
      }
    },

    openMessageDialog(row) {
      this.messageForm.ctid = row.ctid
      this.messageForm.cname = row.cname
      this.messageForm.title = ''
      this.messageForm.content = ''
      this.messageDialogVisible = true
      this.$nextTick(() => {
        this.$refs.messageForm.clearValidate()
      })
    },

    sendMessage() {
      this.$refs.messageForm.validate((valid) => {
        if (valid) {
          this.sendLoading = true
          const that = this
          // 如果 this.tid 为 null，从 sessionStorage 获取
          const tid = this.tid || sessionStorage.getItem("tid")

          if (!tid) {
            this.$message.error("无法获取教师信息，请重新登录")
            this.sendLoading = false
            return
          }

          if (!this.messageForm.ctid) {
            this.$message.error("课程信息缺失，请重新打开对话框")
            this.sendLoading = false
            return
          }

          axios.post('http://localhost:10086/message/sendToCourse', {
            ctid: this.messageForm.ctid,
            title: this.messageForm.title,
            content: this.messageForm.content,
            tid: parseInt(tid)
          }).then(function (resp) {
            if (resp.data.success) {
              that.$message.success('消息发送成功！')
              that.messageDialogVisible = false
            } else {
              that.$message.error(resp.data.message || '发送失败')
            }
          }).catch(function (error) {
            console.error('发送消息失败', error)
            that.$message.error('发送失败，请稍后重试')
          }).finally(function () {
            that.sendLoading = false
          })
        }
      })
    }
  }
}
</script>