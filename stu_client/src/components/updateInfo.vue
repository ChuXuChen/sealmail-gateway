<template>
  <page-layout
    title="修改密码"
    subtitle="修改您的账号信息和登录密码"
    :breadcrumb-list="breadcrumbList"
  >
    <el-card class="form-card">
      <el-form :model="ruleForm" status-icon :rules="rules" ref="ruleForm" label-width="100px" class="demo-ruleForm">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="ruleForm.name"></el-input>
        </el-form-item>
        <el-form-item label="新密码" prop="pass">
          <el-input type="password" v-model="ruleForm.pass" autocomplete="off" placeholder="不修改密码则留空"></el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="checkPass">
          <el-input type="password" v-model="ruleForm.checkPass" autocomplete="off" placeholder="不修改密码则留空"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitForm('ruleForm')">提交</el-button>
          <el-button @click="resetForm('ruleForm')">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </page-layout>
</template>
<script>
import request from '@/utils/request'
import PageLayout from '@/components/PageLayout'
export default {
  components: { PageLayout },
  data() {
    const userType = sessionStorage.getItem('type')
    let homePath = '/'
    let homeName = '首页'
    if (userType === 'student' || userType === '1') {
      homePath = '/studentHome'
      homeName = '学生首页'
    } else if (userType === 'teacher' || userType === '2') {
      homePath = '/teacherHome'
      homeName = '教师首页'
    } else if (userType === 'admin' || userType === '3') {
      homePath = '/adminHome'
      homeName = '管理员首页'
    }

    var validatePass = (rule, value, callback) => {
      // 如果密码为空，直接通过（不修改密码）
      if (value === '') {
        callback();
      } else {
        if (this.ruleForm.checkPass !== '') {
          this.$refs.ruleForm.validateField('checkPass');
        }
        callback();
      }
    };
    var validatePass2 = (rule, value, callback) => {
      // 如果确认密码为空，且密码也为空，通过
      if (value === '' && this.ruleForm.pass === '') {
        callback();
      }
      // 如果密码不为空，但确认密码为空，提示
      else if (value === '' && this.ruleForm.pass !== '') {
        callback(new Error('请再次输入密码'));
      }
      // 如果两次输入不一致，提示
      else if (value !== this.ruleForm.pass) {
        callback(new Error('两次输入密码不一致!'));
      }
      // 其他情况通过
      else {
        callback();
      }
    };
    return {
      breadcrumbList: [
        { name: homeName, path: homePath },
        { name: '修改密码', path: '/updateInfo' }
      ],
      ruleForm: {
        pass: '',
        checkPass: '',
        name: sessionStorage.getItem('name')
      },
      rules: {
        pass: [
          { validator: validatePass, trigger: 'blur' }
        ],
        checkPass: [
          { validator: validatePass2, trigger: 'blur' }
        ],
        name: [
          { require: true, message: '名字不能为空', trigger: 'blur'}
        ]
      }
    };
  },
  methods: {
    submitForm(formName) {
      this.$refs[formName].validate((valid) => {
        if (valid) {
          const that = this
          sessionStorage.setItem('name', that.ruleForm.name)
          const type = sessionStorage.getItem('type')
          let form = null
          let url = null

          // 构建表单数据
          if (type === 'student') {
            form = {
              sid: sessionStorage.getItem('sid'),
              sname: that.ruleForm.name
            }
            // 如果密码不为空，添加密码字段
            if (that.ruleForm.pass && that.ruleForm.pass.trim() !== '') {
              form.password = that.ruleForm.pass
            }
            url = '/student/updateStudent'
          } else if (type === 'teacher') {
            form = {
              tid: sessionStorage.getItem('tid'),
              tname: that.ruleForm.name
            }
            if (that.ruleForm.pass && that.ruleForm.pass.trim() !== '') {
              form.password = that.ruleForm.pass
            }
            url = '/teacher/updateTeacher'
          } else if (type === 'admin' || type === '3') {
            form = {
              tid: sessionStorage.getItem('tid'),
              tname: that.ruleForm.name
            }
            if (that.ruleForm.pass && that.ruleForm.pass.trim() !== '') {
              form.password = that.ruleForm.pass
            }
            url = '/teacher/updateTeacher'
          }

          request.post(url, form).then(function (resp) {
            if (resp === true) {
              that.$message({
                showClose: true,
                message: '修改成功',
                type: 'success'
              });
            }
            else {
              that.$message.error('修改失败，请联系管理员');
            }
            const redirectType = type === '3' ? 'admin' : type
            that.$router.push("/" + redirectType + 'Home')
          })
        } else {
          console.log('error submit!!');
          return false;
        }
      });
    },
    resetForm(formName) {
      this.$refs[formName].resetFields();
    }
  }
}
</script>

<style scoped>
.form-card {
  max-width: 600px;
  margin: 0 auto;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #F3F4F6;
}
.demo-ruleForm {
  padding: 20px;
}
.el-form-item {
  margin-bottom: 24px;
}
.el-button {
  border-radius: 6px;
  padding: 10px 24px;
}
.el-button--primary {
  background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
  border: none;
}
.el-button--primary:hover {
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}
</style>