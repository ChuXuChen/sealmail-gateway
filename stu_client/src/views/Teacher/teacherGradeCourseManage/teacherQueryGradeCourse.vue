<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <el-form :inline="true" :model="ruleForm" :rules="rules" ref="ruleForm" label-width="120px" class="demo-ruleForm">
            <el-form-item label="课程筛选">
              <el-select v-model="ruleForm.ctid" placeholder="全部课程" clearable @change="onCourseFilterChange">
                <el-option label="全部课程" :value="null"></el-option>
                <el-option
                  v-for="course in teacherCourses"
                  :key="course.ctid"
                  :label="course.cname + ' (' + course.term + ')'"
                  :value="course.ctid">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="学号" prop="sid">
              <el-input v-model.number="ruleForm.sid"></el-input>
            </el-form-item>
            <el-form-item label="学生名" prop="sname">
              <el-input v-model="ruleForm.sname"></el-input>
            </el-form-item>
            <el-form-item label="模糊查询" prop="sFuzzy">
              <el-switch v-model="ruleForm.sFuzzy"></el-switch>
            </el-form-item>
            <el-form-item label="成绩下限" prop="lowBound">
              <el-input v-model.number="ruleForm.lowBound"></el-input>
            </el-form-item>
            <el-form-item label="成绩上限" prop="highBound">
              <el-input v-model.number="ruleForm.highBound"></el-input>
            </el-form-item>
            <el-form-item label="选择学期">
              <el-select v-model="ruleForm.term" placeholder="请选择学期">
                <el-option v-for="(item, index) in termList" :key="index" :label="item" :value="item"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="resetForm('ruleForm')">重置</el-button>
              <el-button type="success" @click="exportGrade" :disabled="!ruleForm.ctid">导出当前课程成绩</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card style="margin-top: 10px">
          <teacher-grade-course-list :rule-form="ruleForm"></teacher-grade-course-list>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>
<script>
import request from '@/utils/request'
import GradeCourseList from "@/views/Admin/gradeCourseManage/gradeCourseList";
import TeacherGradeCourseList from "@/views/Teacher/teacherGradeCourseManage/teacherGradeCourseList";
export default {
  components: {TeacherGradeCourseList, GradeCourseList},
  data() {
    return {
      termList: null,
      teacherCourses: [],
      ruleForm: {
        sid: null,
        sname: null,
        sFuzzy: true,
        tid: sessionStorage.getItem('tid'),
        tname: null,
        tFuzzy: true,
        cid: null,
        cname: null,
        cFuzzy: true,
        lowBound: null,
        highBound: null,
        term: sessionStorage.getItem('currentTerm'),
        ctid: null
      },
      rules: {
        cid: [
          { type: 'number', message: '必须是数字类型' }
        ],
        tid: [
          { type: 'number', message: '必须是数字类型' }
        ],
        sid: [
          { type: 'number', message: '必须是数字类型' }
        ],
        cname: [
        ],
        lowBound: [
          { type: 'number', message: '必须是数字类型' }
        ],
        highBound: [
          { type: 'number', message: '必须是数字类型' }
        ],
      }
    };
  },
  created() {
    const that = this
    request.get('/SCT/findAllTerm').then(function (resp) {
      that.termList = resp
    })
    this.loadTeacherCourses()
  },
  methods: {
    resetForm(formName) {
      this.$refs[formName].resetFields();
    },
    loadTeacherCourses() {
      const term = sessionStorage.getItem('currentTerm') || '26-春季学期';
      const that = this;

      const searchParams = {
        tid: this.ruleForm.tid,
        term: term
      };

      request.post('/courseTeacher/findCourseTeacherInfo', searchParams)
        .then(function(resp) {
          const courses = resp;
          that.teacherCourses = courses.map(course => ({
            ctid: course.ctid,
            cid: course.cid,
            cname: course.cname,
            term: term
          }));

          console.log('教师课程列表:', that.teacherCourses);
        })
        .catch(function(error) {
          console.error('加载课程失败:', error);
          that.$message.error('加载课程失败');
        });
    },
    onCourseFilterChange(ctid) {
      console.log('课程筛选已更改:', ctid);
    },
    exportGrade() {
      if (!this.ruleForm.ctid) {
        this.$message.warning('请先选择要导出的课程');
        return;
      }

      // 构造导出参数，和查询参数一致
      const exportParams = { ...this.ruleForm };

      // 发送导出请求
      request.post('/SCT/export', exportParams, {
        responseType: 'blob'
      }).then(res => {
        // 创建下载链接
        const blob = new Blob([res], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', '学生成绩表.xlsx');
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.$message.success('导出成功');
      }).catch(err => {
        console.error('导出失败:', err);
        this.$message.error('导出失败，请稍后重试');
      });
    }
  }
}
</script>