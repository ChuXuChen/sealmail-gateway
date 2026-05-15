<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <el-form :inline="true" :model="ruleForm" :rules="rules" ref="ruleForm" label-width="100px" class="demo-ruleForm">
            <el-form-item label="学号" prop="sid">
              <el-input v-model.number="ruleForm.sid"></el-input>
            </el-form-item>
            <el-form-item label="学生姓名" prop="sname">
              <el-input v-model="ruleForm.sname"></el-input>
            </el-form-item>
            <el-form-item label="模糊查询" prop="fuzzy">
              <el-switch v-model="ruleForm.fuzzy"></el-switch>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="resetForm('ruleForm')">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card>
          <student-list :ruleForm="this.ruleForm"></student-list>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>
<script>
import StudentList from "@/views/Admin/studentManage/studentList";
export default {
  components: {StudentList},
  data() {
    return {
      ruleForm: {
        sid: null,
        sname: null,
        fuzzy: true
      },
      rules: {
        sid: [
          { type: 'number', message: '必须是数字类型' }
        ],
        sname: [

        ],
      }
    };
  },
  create() {
    this.sid = null
    this.sname = null
    this.fuzzy = true
  },
  methods: {
    resetForm(formName) {
      this.$refs[formName].resetFields();
    }
  }
}
</script>