<template>
  <div class="send-message-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>发送全体消息</span>
      </div>
      <el-form :model="messageForm" :rules="rules" ref="messageForm" label-width="100px">
        <el-form-item label="消息标题" prop="title">
          <el-input v-model="messageForm.title" placeholder="请输入消息标题"></el-input>
        </el-form-item>
        <el-form-item label="消息内容" prop="content">
          <el-input type="textarea" :rows="6" v-model="messageForm.content" placeholder="请输入消息内容"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitForm">发送消息</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
export default {
  name: "sendMessage",
  data() {
    return {
      messageForm: {
        title: '',
        content: ''
      },
      rules: {
        title: [
          { required: true, message: '请输入消息标题', trigger: 'blur' },
          { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
        ],
        content: [
          { required: true, message: '请输入消息内容', trigger: 'blur' },
          { min: 5, max: 500, message: '长度在 5 到 500 个字符', trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    submitForm() {
      this.$refs.messageForm.validate((valid) => {
        if (valid) {
          this.$axios.post('http://localhost:10086/message/admin/sendAll', {
            title: this.messageForm.title,
            content: this.messageForm.content
          }).then(res => {
            if (res.data.success) {
              this.$message.success('消息发送成功！')
              this.resetForm()
            } else {
              this.$message.error(res.data.message || '消息发送失败')
            }
          }).catch(err => {
            console.error('发送消息失败', err)
            this.$message.error('发送消息失败，请稍后重试')
          })
        } else {
          return false
        }
      })
    },
    resetForm() {
      this.$refs.messageForm.resetFields()
    }
  }
}
</script>

<style scoped>
.send-message-container {
  padding: 20px;
}

.box-card {
  max-width: 800px;
  margin: 0 auto;
}

.clearfix:before,
.clearfix:after {
  display: table;
  content: "";
}

.clearfix:after {
  clear: both
}
</style>
