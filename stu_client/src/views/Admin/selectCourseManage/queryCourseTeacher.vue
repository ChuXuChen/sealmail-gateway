<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <el-form :inline="true" :model="ruleForm" :rules="rules" ref="ruleForm" label-width="150px" class="demo-ruleForm">
            <el-form-item label="工号" prop="tid">
              <el-input v-model.number="ruleForm.tid"></el-input>
            </el-form-item>
            <el-form-item label="教师名" prop="tname">
              <el-input v-model="ruleForm.tname"></el-input>
            </el-form-item>
            <el-form-item label="教师模糊查询">
              <el-switch v-model="ruleForm.tFuzzy"></el-switch>
            </el-form-item>
            <el-form-item label="课程号" prop="cid">
              <el-input v-model.number="ruleForm.cid"></el-input>
            </el-form-item>
            <el-form-item label="课程名" prop="cname">
              <el-input v-model="ruleForm.cname"></el-input>
            </el-form-item>
            <el-form-item label="课程模糊查询">
              <el-switch v-model="ruleForm.cFuzzy"></el-switch>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="resetForm('ruleForm')">重置</el-button>
              <el-button type="success" @click="addCourseTeacher">添加开课</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card style="margin-top: 10px">
          <course-tacher-list ref="courseTeacherList" :ruleForm="ruleForm"></course-tacher-list>
        </el-card>
      </el-main>
    </el-container>

    <!-- 添加开课弹窗 -->
    <el-dialog
      title="添加开课"
      :visible.sync="addDialogVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="addForm" ref="addForm" label-width="100px" :rules="addRules">
        <el-form-item label="课程" prop="cid">
          <el-select v-model="addForm.cid" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="course in courseList" :key="course.cid"
                       :label="course.cname + ' (' + course.cid + ')'"
                       :value="course.cid"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="教师" prop="tid">
          <el-select v-model="addForm.tid" placeholder="请选择教师" style="width: 100%">
            <el-option v-for="teacher in teacherList" :key="teacher.tid"
                       :label="teacher.tname + ' (' + teacher.tid + ')'"
                       :value="teacher.tid"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="学期" prop="term">
          <el-select v-model="addForm.term" placeholder="请选择学期" style="width: 100%">
            <el-option v-for="(term, index) in termList" :key="index"
                       :label="term" :value="term"></el-option>
          </el-select>
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
import CourseTacherList from "@/views/Admin/selectCourseManage/CourseTacherList";
export default {
  components: {CourseTacherList},
  data() {
    return {
      ruleForm: {
        tid: null,
        cid: null,
        cname: null,
        tname: null,
        tFuzzy: true,
        cFuzzy: true
      },
      rules: {
        tid: [
          { type: 'number', message: '必须是数字类型' }
        ],
        cid: [
          { type: 'number', message: '必须是数字类型' }
        ],
      },
      // 添加开课相关
      addDialogVisible: false,
      addLoading: false,
      addForm: {
        cid: null,
        tid: null,
        term: null
      },
      addRules: {
        cid: [
          { required: true, message: '请选择课程', trigger: 'change' }
        ],
        tid: [
          { required: true, message: '请选择教师', trigger: 'change' }
        ],
        term: [
          { required: true, message: '请选择学期', trigger: 'change' }
        ]
      },
      courseList: [],
      teacherList: [],
      termList: []
    };
  },
  created() {
    // 获取课程列表
    request.post("/course/findBySearch", {}).then((resp) => {
      this.courseList = resp
    })

    // 获取教师列表
    request.post("/teacher/findBySearch", {}).then((resp) => {
      this.teacherList = resp
    })

    // 获取学期列表
    request.get('/SCT/findAllTerm').then((resp) => {
      this.termList = resp
    })
  },
  methods: {
    resetForm(formName) {
      this.$refs[formName].resetFields();
    },
    addCourseTeacher() {
      this.addDialogVisible = true
      this.addForm = {
        cid: null,
        tid: null,
        term: null
      }
      this.$nextTick(() => {
        this.$refs.addForm.clearValidate()
      })
    },

    // 提交添加开课
    handleSubmitAdd() {
      this.$refs.addForm.validate((valid) => {
        if (valid) {
          this.addLoading = true
          const cid = this.addForm.cid
          const tid = this.addForm.tid
          const term = this.addForm.term
          request.get("/courseTeacher/insert/" + cid + '/' + tid + '/' + term).then((resp) => {
            if (resp === true) {
              this.$message.success('添加成功')
              this.addDialogVisible = false
              // 刷新列表
              this.$refs.courseTeacherList.loadCourseTeacherList()
            } else {
              this.$message.error('添加失败：可能已存在相同的开课记录')
            }
          }).catch(() => {
            this.$message.error('添加出错，请检查网络连接')
          }).finally(() => {
            this.addLoading = false
          })
        } else {
          return false
        }
      })
    }
  }
}
</script>