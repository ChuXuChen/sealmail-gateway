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
        ctid: null,
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

    // 获取当前编辑的记录
    if (this.$route.query.ctid) {
      const ctid = this.$route.query.ctid
      request.get('/courseTeacher/findByCtid/' + ctid).then(function (resp) {
        if (resp && resp.length > 0) {
          const courseTeacher = resp[0]
          that.ruleForm = {
            ctid: courseTeacher.ctid,
            cid: courseTeacher.cid,
            tid: courseTeacher.tid,
            term: courseTeacher.term
          }
        }
      })
    }
  },
  methods: {
    submitForm(formName) {
      this.$refs[formName].validate((valid) => {
        if (valid) {
          const that = this
          console.log("提交开课记录修改:", this.ruleForm)
          request.post("/courseTeacher/updateCourseTeacher", this.ruleForm).then(function (resp) {
            if (resp === true) {
              that.$message({
                showClose: true,
                message: '修改成功',
                type: 'success'
              });
            } else {
              that.$message.error('修改失败：可能存在重复的开课记录或数据不完整');
            }
            that.$router.push("/queryCourseTeacher")
          }).catch(function (error) {
            console.error("修改出错:", error)
            that.$message.error('修改出错，请检查网络连接');
          })
        } else {
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