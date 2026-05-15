<template>
  <div>
    <el-form style="width: 60%" :model="ruleForm" :rules="rules" ref="ruleForm" label-width="100px" class="demo-ruleForm">
      <el-form-item label="学生姓名" prop="sname">
        <el-input v-model="ruleForm.sname" :value="ruleForm.sname"></el-input>
      </el-form-item>
      <el-form-item label="密码（admin不可编辑）" prop="password">
        <el-input type="password" v-model="ruleForm.password" :value="ruleForm.password" placeholder="密码不可编辑" disabled></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitForm('ruleForm')">提交</el-button>
        <el-button @click="resetForm('ruleForm')">重置</el-button>
        <el-button @click="test">test</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
<script>
import request from '@/utils/request'
export default {
  data() {
    return {
      ruleForm: {
        sid: null,
        sname: null,
        password: null
      },
      rules: {
        sname: [
          { required: true, message: '请输入名称', trigger: 'blur' },
          { min: 2, max: 5, message: '长度在 2 到 5 个字符', trigger: 'blur' }
        ],
        password: [],
      }
    };
  },
  created() {
    const that = this
    if (this.$route.query.sid === undefined) {
      this.ruleForm.sid = 1
    }
    else {
      this.ruleForm.sid = this.$route.query.sid
    }
    request.get('/student/findById/' + this.ruleForm.sid).then(function (resp) {
      that.ruleForm = resp
      that.ruleForm.password = ''
    })
  },
  methods: {
    submitForm(formName) {
      this.$refs[formName].validate((valid) => {
        if (valid) {
          // 通过前端校验
          const that = this
          console.log(this.ruleForm)
          request.post("/student/updateStudent", this.ruleForm).then(function (resp) {
            if (resp === true) {
              that.$message({
                showClose: true,
                message: '编辑成功',
                type: 'success'
              });
            }
            else {
              that.$message.error('编辑失败，请检查数据库');
            }
            that.$router.push("/queryStudent")
          })
        } else {
          return false;
        }
      });
    },
    resetForm(formName) {
      this.$refs[formName].resetFields();
    },
    test() {
      console.log(this.ruleForm)
    }
  }
}
</script>