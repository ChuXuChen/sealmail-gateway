<template>
  <div>
    <el-form style="width: 60%" :model="ruleForm" :rules="rules" ref="ruleForm" label-width="100px" class="demo-ruleForm">
      <el-form-item label="课程" prop="cid">
        <el-select v-model="ruleForm.cid" placeholder="请选择课程" style="width: 100%">
          <el-option v-for="course in courseList" :key="course.cid"
                     :label="course.cname + ' (' + course.cid + ')'"
                     :value="course.cid"></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="教师" prop="tid">
        <el-select v-model="ruleForm.tid" placeholder="请选择教师" style="width: 100%">
          <el-option v-for="teacher in teacherList" :key="teacher.tid"
                     :label="teacher.tname + ' (' + teacher.tid + ')'"
                     :value="teacher.tid"></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="学期" prop="term">
        <el-select v-model="ruleForm.term" placeholder="请选择学期" style="width: 100%">
          <el-option v-for="(term, index) in termList" :key="index"
                     :label="term" :value="term"></el-option>
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="submitForm('ruleForm')">提交</el-button>
        <el-button @click="resetForm('ruleForm')">重置</el-button>
        <el-button @click="cancel">取消</el-button>
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
        cid: null,
        tid: null,
        term: null
      },
      courseList: [],
      teacherList: [],
      termList: [],
      rules: {
        cid: [
          { required: true, message: '请选择课程', trigger: 'change' }
        ],
        tid: [
          { required: true, message: '请选择教师', trigger: 'change' }
        ],
        term: [
          { required: true, message: '请选择学期', trigger: 'change' }
        ]
      }
    };
  },
  created() {
    const that = this

    // 获取课程列表
    request.post("/course/findBySearch", {}).then(function (resp) {
      that.courseList = resp
    })

    // 获取教师列表
    request.post("/teacher/findBySearch", {}).then(function (resp) {
      that.teacherList = resp
    })

    // 获取学期列表
    request.get('/SCT/findAllTerm').then(function (resp) {
      that.termList = resp
    })
  },
  methods: {
    submitForm(formName) {
      this.$refs[formName].validate((valid) => {
        if (valid) {
          const that = this
          const cid = this.ruleForm.cid
          const tid = this.ruleForm.tid
          const term = this.ruleForm.term
          console.log("提交开课记录添加:", cid, tid, term)
          request.get("/courseTeacher/insert/" + cid + '/' + tid + '/' + term).then(function (resp) {
            if (resp === true) {
              that.$message({
                showClose: true,
                message: '添加成功',
                type: 'success'
              });
              that.$router.push("/queryCourseTeacher")
            } else {
              that.$message.error('添加失败：可能已存在相同的开课记录');
            }
          }).catch(function (error) {
            console.error("添加出错:", error)
            that.$message.error('添加出错，请检查网络连接');
          })
        } else {
          return false;
        }
      });
    },
    resetForm(formName) {
      this.$refs[formName].resetFields();
    },
    cancel() {
      this.$router.push("/queryCourseTeacher")
    }
  }
}
</script>