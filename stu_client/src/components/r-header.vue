<template>
  <div class="header-container">
    <div class="header-right">
      <!-- 学期信息 -->
      <div class="header-item">
        <i class="el-icon-date"></i>
        <span class="item-text">{{ currentTerm }}</span>
      </div>

      <el-divider direction="vertical" class="header-divider"></el-divider>

      <!-- 消息中心 -->
      <div class="header-item message-item" @click="openMessageList()">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="message-badge">
          <i class="el-icon-message"></i>
          <span class="item-text">消息</span>
        </el-badge>
      </div>

      <el-divider direction="vertical" class="header-divider"></el-divider>

      <!-- 用户信息 -->
      <div class="header-item user-item">
        <div class="user-avatar">
          <i class="el-icon-user"></i>
        </div>
        <span class="user-name">{{ name }}</span>
        <el-dropdown trigger="hover">
          <i class="el-icon-arrow-down" style="margin-left: 5px; font-size: 12px;"></i>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item @click.native="$router.push('/updateInfo')">
              <i class="el-icon-key" style="margin-right: 8px;"></i>
              修改密码
            </el-dropdown-item>
            <el-dropdown-item divided @click.native="out()">
              <i class="el-icon-switch-button" style="margin-right: 8px;"></i>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div>

      <!-- 消息列表弹窗 -->
      <el-dialog
        title="消息中心"
        :visible.sync="messageDialogVisible"
        width="700px"
        append-to-body
        :close-on-click-modal="false">
        <div style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center">
          <el-tabs v-model="activeTab" @tab-click="handleTabClick">
            <el-tab-pane label="全部消息" name="all"></el-tab-pane>
            <el-tab-pane label="系统通知" name="system"></el-tab-pane>
            <el-tab-pane label="私信" name="private"></el-tab-pane>
          </el-tabs>
          <el-button type="primary" size="small" icon="el-icon-edit" @click="openSendDialog">发送私信</el-button>
        </div>
        <div v-if="filteredMessageList.length === 0" style="text-align: center; padding: 40px; color: #999">
          暂无消息
        </div>
        <div v-else>
          <div v-for="msg in filteredMessageList" :key="msg.id" class="message-item" :class="{ 'unread': msg.isRead === 0 }" @click="readMessage(msg)">
            <div class="message-title">
              <span>{{ msg.message.title }}</span>
              <span class="message-time">{{ formatDate(msg.message.createTime) }}</span>
            </div>
            <div v-if="msg.message.senderName" class="message-sender">
              发件人：{{ msg.message.senderName }}
            </div>
            <div class="message-content">{{ msg.message.content }}</div>
          </div>
          <div style="text-align: right; margin-top: 20px">
            <el-button type="text" @click="markAllRead">一键已读</el-button>
          </div>
        </div>
      </el-dialog>

      <!-- 发送私信弹窗 -->
      <el-dialog
        title="发送私信"
        :visible.sync="sendDialogVisible"
        width="500px"
        append-to-body
        :close-on-click-modal="false">
        <el-form :model="sendForm" ref="sendForm" label-width="80px">
          <el-form-item label="收件人" prop="targetId" :rules="[{ required: true, message: '请选择收件人', trigger: 'blur' }]">
            <el-select
              v-model="selectValue"
              filterable
              remote
              reserve-keyword
              placeholder="请输入姓名或ID搜索用户"
              :remote-method="querySearchAsync"
              :loading="searchLoading"
              style="width: 100%"
              @change="handleSelectUser">
              <el-option
                v-for="item in searchUserList"
                :key="`${item.type}-${item.id}`"
                :label="`${item.name} (${item.type === 1 ? '学生' : item.type === 3 ? '管理员' : '教师'})`"
                :value="`${item.type}-${item.id}`">
              </el-option>
            </el-select>
            <div v-if="sendForm.targetId" style="margin-top: 8px; color: #666; font-size: 13px">
              已选择：{{ selectedUser.name }} ({{ selectedUser.type === 1 ? '学生' : selectedUser.type === 3 ? '管理员' : '教师' }})
            </div>
          </el-form-item>
          <el-form-item label="标题" prop="title" :rules="[{ required: true, message: '请输入标题', trigger: 'blur' }]">
            <el-input v-model="sendForm.title" placeholder="请输入消息标题"></el-input>
          </el-form-item>
          <el-form-item label="内容" prop="content" :rules="[{ required: true, message: '请输入内容', trigger: 'blur' }]">
            <el-input type="textarea" :rows="4" v-model="sendForm.content" placeholder="请输入消息内容"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer">
          <el-button @click="sendDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="sendPrivateMessage">发送</el-button>
        </div>
      </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
export default {
  name: "r-header",
  data() {
    return {
      name: null,
      currentTerm: null,
      unreadCount: 0,
      messageDialogVisible: false,
      messageList: [],
      pollTimer: null,
      activeTab: 'all',
      sendDialogVisible: false,
      searchKeyword: '',
      searchLoading: false,
      searchUserList: [],
      selectValue: null, // 下拉框选中的组合值（type-id）
      sendForm: {
        targetId: null,
        targetUserType: null,
        title: '',
        content: ''
      },
      selectedUser: {}
    }
  },
  computed: {
    filteredMessageList() {
      if (this.activeTab === 'all') {
        return this.messageList
      } else if (this.activeTab === 'system') {
        return this.messageList.filter(msg => msg.message.type !== 5)
      } else if (this.activeTab === 'private') {
        return this.messageList.filter(msg => msg.message.type === 5)
      }
      return this.messageList
    }
  },
  created() {
    this.name = sessionStorage.getItem("name")
    this.currentTerm = sessionStorage.getItem("currentTerm")
    // 立即获取一次未读数量
    this.getUnreadCount()
    // 每30秒轮询一次未读数量
    this.pollTimer = setInterval(() => {
      this.getUnreadCount()
    }, 30000)
  },
  beforeDestroy() {
    // 清除轮询定时器
    if (this.pollTimer) {
      clearInterval(this.pollTimer)
    }
  },
  methods: {
    out() {
      sessionStorage.clear();
      this.$router.push('/')
    },
    // 获取未读消息数量
    getUnreadCount() {
      const type = sessionStorage.getItem("type")
      let userId, userType
      if (type === "1" || type === "student") {
        userId = sessionStorage.getItem("sid")
        userType = 1 // 学生（后端类型定义：1=学生，2=教师，3=管理员）
      } else if (type === "2" || type === "teacher") {
        userId = sessionStorage.getItem("tid")
        userType = 2 // 教师
      } else if (type === "3" || type === "admin") {
        userId = sessionStorage.getItem("tid")
        userType = 3 // 管理员
      }
      if (!userId) return
      request.get(`/message/unread/count?userId=${userId}&userType=${userType}`)
        .then(res => {
          if (res.success) {
            this.unreadCount = res.count
          }
        })
        .catch(err => {
          console.error("获取未读消息数量失败", err)
        })
    },
    // 打开消息列表
    openMessageList() {
      this.messageDialogVisible = true
      this.getMessageList()
    },
    // 获取消息列表
    getMessageList() {
      const type = sessionStorage.getItem("type")
      let userId, userType
      if (type === "1" || type === "student") {
        userId = sessionStorage.getItem("sid")
        userType = 1 // 学生（后端类型定义：1=学生，2=教师，3=管理员）
      } else if (type === "2" || type === "teacher") {
        userId = sessionStorage.getItem("tid")
        userType = 2 // 教师
      } else if (type === "3" || type === "admin") {
        userId = sessionStorage.getItem("tid")
        userType = 3 // 管理员
      }
      console.log("请求消息列表参数：", { userId, userType, type })
      if (!userId) return
      request.get(`/message/list?userId=${userId}&userType=${userType}`)
        .then(res => {
          console.log("接口返回完整内容：", res)
          let list = []
          // request拦截器已经自动返回data字段的值，所以res直接是消息数组
          if (Array.isArray(res)) {
            list = res
          } else if (res.success && Array.isArray(res.data)) {
            list = res.data
          }
          console.log("处理后的消息列表：", list, "长度：", list.length)
          this.$set(this, 'messageList', list)
        })
        .catch(err => {
          console.error("获取消息列表失败", err)
        })
    },
    // 阅读消息
    readMessage(msg) {
      if (msg.isRead === 1) return
      request.post(`/message/markRead`, { id: msg.id })
        .then(res => {
          if (res.success || res === true) {
            msg.isRead = 1
            this.unreadCount = Math.max(0, this.unreadCount - 1)
          }
        })
    },
    // 一键已读
    markAllRead() {
      const type = sessionStorage.getItem("type")
      let userId, userType
      if (type === "1" || type === "student") {
        userId = sessionStorage.getItem("sid")
        userType = 1 // 学生（后端类型定义：1=学生，2=教师，3=管理员）
      } else if (type === "2" || type === "teacher") {
        userId = sessionStorage.getItem("tid")
        userType = 2 // 教师
      } else if (type === "3" || type === "admin") {
        userId = sessionStorage.getItem("tid")
        userType = 3 // 管理员
      }
      if (!userId) return
      request.post(`/message/markAllRead`, { userId: userId, userType: userType })
        .then(res => {
          if (res.success || res === true) {
            this.messageList.forEach(msg => {
              msg.isRead = 1
            })
            this.unreadCount = 0
          }
        })
    },
    // 格式化日期
    formatDate(dateStr) {
      if (!dateStr) return ""
      const date = new Date(dateStr)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, "0")
      const day = String(date.getDate()).padStart(2, "0")
      const hour = String(date.getHours()).padStart(2, "0")
      const minute = String(date.getMinutes()).padStart(2, "0")
      return `${year}-${month}-${day} ${hour}:${minute}`
    },
    // Tab切换
    handleTabClick() {
      // 切换Tab后无需额外操作，计算属性会自动过滤
    },
    // 打开发送私信弹窗
    openSendDialog() {
      this.sendDialogVisible = true
      this.searchKeyword = ''
      this.selectValue = null
      this.sendForm = {
        targetId: null,
        targetUserType: null,
        title: '',
        content: ''
      }
      this.selectedUser = {}
      this.$nextTick(() => {
        this.$refs.sendForm.clearValidate()
      })
    },
    // 搜索用户（适配el-select远程搜索）
    querySearchAsync(queryString) {
      if (!queryString) {
        this.searchUserList = []
        return
      }
      this.searchLoading = true
      request.get(`/message/searchUser?keyword=${queryString}`)
        .then(res => {
          console.log("搜索用户返回：", res)
          let list = []
          // request拦截器已经自动返回data字段的值，所以res直接是用户数组
          if (Array.isArray(res)) {
            list = res
          } else if (res.success && Array.isArray(res.data)) {
            list = res.data
          }
          console.log("处理后的用户列表：", list)
          this.searchUserList = list
        })
        .catch(err => {
          console.error("搜索用户失败", err)
          this.searchUserList = []
        })
        .finally(() => {
          this.searchLoading = false
        })
    },
    // 选择用户
    handleSelectUser(selectValue) {
      if (!selectValue) return
      // 解析组合值：格式为type-id
      const [typeStr, idStr] = selectValue.split('-')
      const type = parseInt(typeStr)
      const id = parseInt(idStr)
      // 根据type和id查找用户，避免id重复导致匹配错误
      const user = this.searchUserList.find(item => item.id === id && item.type === type)
      if (user) {
        this.selectedUser = user
        this.sendForm.targetId = id
        this.sendForm.targetUserType = type
      }
    },
    // 发送私信
    sendPrivateMessage() {
      this.$refs.sendForm.validate((valid) => {
        if (valid) {
          const type = sessionStorage.getItem("type")
          let senderId, senderType
          if (type === "1" || type === "student") {
            senderId = sessionStorage.getItem("sid")
            senderType = 3 // 学生发送者类型：3=学生（后端发送者类型定义：0=系统，1=管理员，2=教师，3=学生）
          } else if (type === "2" || type === "teacher") {
            senderId = sessionStorage.getItem("tid")
            senderType = 2 // 教师
          } else if (type === "3" || type === "admin") {
            senderId = sessionStorage.getItem("tid")
            senderType = 1 // 管理员
          }
          if (!senderId) {
            this.$message.error("无法获取用户信息，请重新登录")
            return
          }
          const params = {
            senderId: parseInt(senderId),
            senderType: senderType,
            targetId: this.sendForm.targetId,
            targetUserType: this.sendForm.targetUserType,
            title: this.sendForm.title,
            content: this.sendForm.content
          }
          console.log("发送私信参数：", params)
          request.post(`/message/sendPrivate`, params)
            .then(res => {
              if (res.success || res === true) {
                this.$message.success("私信发送成功")
                this.sendDialogVisible = false
                // 刷新消息列表
                this.getMessageList()
              } else {
                this.$message.error(res.message || "发送失败")
              }
            })
            .catch(err => {
              console.error("发送私信失败", err)
              this.$message.error("发送失败，请稍后重试")
            })
        }
      })
    }
  }
}
</script>

<style scoped>
.header-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 20px;
}
.header-right {
  display: flex;
  align-items: center;
  height: 100%;
}
.header-item {
  display: flex;
  align-items: center;
  padding: 0 15px;
  height: 100%;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #FFFFFF;
}
.header-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #E5E7EB;
}
.header-item i {
  font-size: 18px;
  margin-right: 6px;
}
.item-text {
  font-size: 14px;
}
.header-divider {
  height: 30px;
  margin: 0 5px;
  background: #E5E7EB;
}
/* 消息项 */
.message-item {
  position: relative;
}
.message-badge {
  position: relative;
  display: flex;
  align-items: center;
}
/* 调整红点位置 */
.message-badge ::v-deep .el-badge__content {
  position: absolute;
  top: -8px;
  right: -12px;
  transform: scale(0.8);
}
/* 用户信息 */
.user-item {
  padding-left: 20px;
  padding-right: 10px;
}
.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 10px;
}
.user-avatar i {
  font-size: 16px;
  margin: 0;
}
.user-name {
  font-size: 14px;
  font-weight: 500;
  margin-right: 5px;
  color: #FFFFFF;
}
/* 消息列表样式 */
.message-item {
  padding: 15px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: all 0.3s ease;
}
.message-item.unread {
  background-color: #F8FAFC;
  border-left: 3px solid #3B82F6;
}
.message-item:hover {
  background-color: #F1F5F9;
}
.message-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #1F2937;
}
.message-time {
  font-size: 12px;
  color: #9CA3AF;
  font-weight: normal;
}
.message-content {
  font-size: 13px;
  color: #6B7280;
  line-height: 1.5;
}
.message-sender {
  font-size: 12px;
  color: #3B82F6;
  margin-bottom: 8px;
  font-weight: 500;
}
/* 去掉下拉框动画，避免闪现 */
::v-deep .el-select-dropdown {
  transition: none !important;
}
/* 对话框样式优化 */
::v-deep .el-dialog__header {
  padding: 16px 20px;
  background: linear-gradient(135deg, #F8FAFC 0%, #EEF2F7 100%);
}
::v-deep .el-dialog__title {
  font-weight: 600;
}
</style>