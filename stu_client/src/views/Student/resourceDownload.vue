<template>
  <page-layout
    :show-header="false"
    :breadcrumb-list="breadcrumbList"
  >
    <!-- 资源列表 -->
    <el-card shadow="hover">
      <div slot="header" class="card-header flex-between">
        <span><i class="el-icon-download" style="margin-right: 8px; color: #F59E0B;"></i>课程资源下载</span>
        <el-button type="text" @click="refreshResources">
          <i class="el-icon-refresh"></i> 刷新
        </el-button>
      </div>

      <!-- 搜索和筛选 -->
      <el-form :inline="true" :model="searchForm" label-width="80px" class="mb-20">
        <el-form-item label="课程筛选">
          <el-select v-model="searchForm.ctid" placeholder="全部课程" clearable @change="filterResources">
            <el-option label="全部课程" :value="null"></el-option>
            <el-option
              v-for="course in studentCourses"
              :key="course.ctid"
              :label="course.cname + ' - ' + course.teacherName + ' (' + course.term + ')'"
              :value="course.ctid">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="文件名">
          <el-input v-model="searchForm.filename" placeholder="输入文件名" clearable @input="filterResources"></el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="searchForm.description" placeholder="输入描述" clearable @input="filterResources"></el-input>
        </el-form-item>
      </el-form>

      <!-- 资源表格 -->
      <el-table :data="filteredResources" border stripe style="width: 100%" class="custom-table">
        <el-table-column prop="filename" label="文件名" min-width="200"></el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="150">
          <template slot-scope="scope">
            {{ scope.row.courseName || getCourseName(scope.row.ctid) }}
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" min-width="120">
          <template slot-scope="scope">
            {{ scope.row.teacherName || getTeacherName(scope.row.ctid) }}
          </template>
        </el-table-column>
        <el-table-column prop="term" label="学期" min-width="120">
          <template slot-scope="scope">
            {{ scope.row.term || getTerm(scope.row.ctid) }}
          </template>
        </el-table-column>
        <el-table-column prop="filesize" label="文件大小" min-width="100">
          <template slot-scope="scope">
            {{ formatFileSize(scope.row.filesize) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="上传时间" min-width="180">
          <template slot-scope="scope">
            {{ formatDate(scope.row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="200"></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="downloadResource(scope.row.rid)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <div v-if="filteredResources.length === 0" class="empty-state">
        <i class="el-icon-document"></i>
        <p>暂无资源</p>
      </div>
    </el-card>
  </page-layout>
</template>

<script>
import PageLayout from '@/components/PageLayout'
export default {
  components: { PageLayout },
  name: 'ResourceDownload',
  data() {
    return {
      // 面包屑导航
      breadcrumbList: [
        { name: '学生首页', path: '/studentHome' },
        { name: '资源下载', path: '/studentResourceDownload' }
      ],
      // 学生ID
      sid: null,

      // 学生已选课程列表（包含ctid, cname, teacherName, term）
      studentCourses: [],

      // 搜索表单
      searchForm: {
        ctid: null,
        filename: '',
        description: ''
      },

      // 所有资源数据
      allResources: [],

      // 筛选后的资源数据
      filteredResources: []
    };
  },
  created() {
    this.sid = sessionStorage.getItem('sid');
    this.loadStudentCourses();
    this.loadResources();
  },
  methods: {
    // 加载学生已选课程
    loadStudentCourses() {
      const term = sessionStorage.getItem('currentTerm') || '26-春季学期';
      const that = this;

      // 获取学生选课记录（包含ctid）
      axios.get(`http://localhost:10086/sct/findBySid/${this.sid}/${term}`)
        .then(function(resp) {
          const sctList = resp.data;
          console.log('学生选课记录:', sctList);

          // 去重处理，获取唯一的开课记录
          const courseMap = new Map();
          sctList.forEach(item => {
            const key = `${item.ctid}`;
            if (!courseMap.has(key)) {
              courseMap.set(key, {
                ctid: item.ctid,
                cid: item.cid,
                cname: item.cname,
                teacherName: item.tname,
                term: item.term || term
              });
            }
          });

          // 转换为数组
          that.studentCourses = Array.from(courseMap.values());

          // 如果没有课程，显示提示
          if (that.studentCourses.length === 0) {
            that.$message.warning('您当前学期没有选课，无法查看资源');
          }

          console.log('学生课程列表:', that.studentCourses);
        })
        .catch(function(error) {
          console.error('加载选课失败:', error);
          that.$message.error('加载选课失败');
        });
    },

    // 加载资源
    loadResources() {
      const that = this;

      axios.get(`http://localhost:10086/resource/findByStudent/${this.sid}`)
        .then(function(resp) {
          that.allResources = resp.data;
          that.filteredResources = resp.data;
          console.log('加载资源成功:', resp.data);
        })
        .catch(function(error) {
          console.error('加载资源失败:', error);
          that.$message.error('加载资源失败');
        });
    },

    // 刷新资源
    refreshResources() {
      this.loadResources();
      this.loadStudentCourses(); // 也刷新课程列表
      this.$message.success('资源列表已刷新');
    },

    // 过滤资源
    filterResources() {
      let filtered = this.allResources;

      // 按课程筛选（基于ctid）
      if (this.searchForm.ctid) {
        filtered = filtered.filter(resource => resource.ctid === this.searchForm.ctid);
      }

      // 按文件名筛选
      if (this.searchForm.filename) {
        const keyword = this.searchForm.filename.toLowerCase();
        filtered = filtered.filter(resource =>
          resource.filename.toLowerCase().includes(keyword)
        );
      }

      // 按描述筛选
      if (this.searchForm.description) {
        const keyword = this.searchForm.description.toLowerCase();
        filtered = filtered.filter(resource =>
          resource.description && resource.description.toLowerCase().includes(keyword)
        );
      }

      this.filteredResources = filtered;
    },

    // 根据ctid获取课程名称
    getCourseName(ctid) {
      const course = this.studentCourses.find(c => c.ctid === ctid);
      return course ? course.cname : '未知课程';
    },

    // 根据ctid获取教师名称
    getTeacherName(ctid) {
      const course = this.studentCourses.find(c => c.ctid === ctid);
      return course ? course.teacherName : '未知教师';
    },

    // 根据ctid获取学期
    getTerm(ctid) {
      const course = this.studentCourses.find(c => c.ctid === ctid);
      return course ? course.term : '未知学期';
    },

    // 下载资源
    downloadResource(rid) {
      const url = `http://localhost:10086/resource/download/${rid}`;
      window.open(url, '_blank');
    },

    // 工具函数：格式化文件大小
    formatFileSize(bytes) {
      if (bytes === 0 || !bytes) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },

    // 工具函数：格式化日期
    formatDate(dateString) {
      if (!dateString) return '';
      const date = new Date(dateString);
      return date.toLocaleString('zh-CN');
    }
  }
};
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 16px;
}
.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mb-20 {
  margin-bottom: 20px;
}
.custom-table {
  border-radius: 8px;
  overflow: hidden;
}
.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #909399;
}
.empty-state i {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.5;
}
.empty-state p {
  font-size: 16px;
  margin: 0;
}
</style>
