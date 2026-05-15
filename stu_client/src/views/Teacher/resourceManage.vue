<template>
  <page-layout
    :show-header="false"
    :breadcrumb-list="breadcrumbList"
  >
    <!-- 文件上传表单 -->
    <el-card class="mb-24" shadow="hover">
      <div slot="header" class="card-header">
        <span><i class="el-icon-upload" style="margin-right: 8px; color: #3B82F6;"></i>上传新资源</span>
      </div>
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="选择课程" prop="ctid" required>
          <el-select v-model="uploadForm.ctid" placeholder="请选择课程" style="width: 100%;">
              <el-option
                v-for="course in teacherCourses"
                :key="course.ctid"
                :label="course.cname + ' (' + course.term + ')'"
                :value="course.ctid">
              </el-option>
            </el-select>
        </el-form-item>

        <el-form-item label="选择文件" prop="file" required>
          <div style="display: flex; align-items: center; width: 100%;">
            <input
              type="file"
              ref="fileInput"
              style="display: none"
              @change="handleFileSelect"
              accept="*"
            >
            <el-button
              size="small"
              type="primary"
              @click="$refs.fileInput.click()"
              style="margin-right: 10px;"
            >
              选择文件
            </el-button>
            <span v-if="selectedFile">
              {{ selectedFile.name }} ({{ formatFileSize(selectedFile.size) }})
            </span>
            <span v-else style="color: #999;">
              未选择文件
            </span>
          </div>
          <div style="font-size: 12px; color: #999; margin-top: 5px;">
            只能上传不超过50MB的文件
          </div>
        </el-form-item>

        <el-form-item label="文件描述" prop="description">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文件描述（可选）"
            style="width: 100%;">
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :disabled="!uploadForm.ctid || !selectedFile" @click="submitUpload">
            确认上传
          </el-button>
          <el-button @click="resetUploadForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 资源列表 -->
    <el-card shadow="hover">
      <div slot="header" class="card-header flex-between">
        <span><i class="el-icon-document" style="margin-right: 8px; color: #10B981;"></i>资源列表</span>
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
                v-for="course in teacherCourses"
                :key="course.ctid"
                :label="course.cname + ' (' + course.term + ')'"
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
        <el-table-column prop="courseName" label="课程" min-width="150"></el-table-column>
        <el-table-column prop="term" label="学期" min-width="120"></el-table-column>
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
        <el-table-column label="操作" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="downloadResource(scope.row.rid)">
              下载
            </el-button>
            <el-popconfirm
              title="确定删除这个资源吗？"
              @confirm="deleteResource(scope.row.rid)">
              <el-button slot="reference" type="text" size="small" style="color: #f56c6c;">
                删除
              </el-button>
            </el-popconfirm>
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
  name: 'ResourceManage',
  data() {
    return {
      // 面包屑导航
      breadcrumbList: [
        { name: '教师首页', path: '/teacherHome' },
        { name: '资源管理', path: '/teacherResourceManage' }
      ],
      // 教师ID
      tid: null,

      // 教师开设的课程列表
      teacherCourses: [],

      // 上传表单
      uploadForm: {
        ctid: null,
        description: ''
      },

      // 搜索表单
      searchForm: {
        ctid: null,
        filename: '',
        description: ''
      },

      // 所有资源数据
      allResources: [],

      // 筛选后的资源数据
      filteredResources: [],

      // 上传相关
      uploadAction: 'http://localhost:10086/resource/upload',
      selectedFile: null
    };
  },
  created() {
    this.tid = sessionStorage.getItem('tid');
    this.loadTeacherCourses();
    this.loadResources();
  },
  methods: {
    // 加载教师开设的课程
    loadTeacherCourses() {
      const term = sessionStorage.getItem('currentTerm') || '26-春季学期';
      const that = this;

      // 使用findCourseTeacherInfo接口获取课程信息（包含ctid）
      const searchParams = {
        tid: this.tid,
        term: term
      };

      axios.post('http://localhost:10086/courseTeacher/findCourseTeacherInfo', searchParams)
        .then(function(resp) {
          // resp.data包含ctid, cid, cname, tid, tname, ccredit等字段
          const courses = resp.data;
          that.teacherCourses = courses.map(course => ({
            ctid: course.ctid,
            cid: course.cid,
            cname: course.cname,
            term: term
          }));

          console.log('教师课程列表:', that.teacherCourses);

          // 如果没有课程，禁用上传
          if (that.teacherCourses.length === 0) {
            that.$message.warning('您当前学期没有开设课程，无法上传资源');
          }
        })
        .catch(function(error) {
          console.error('加载课程失败:', error);
          that.$message.error('加载课程失败');
        });
    },

    // 加载资源
    loadResources() {
      const that = this;

      axios.get(`http://localhost:10086/resource/findByTeacher/${this.tid}`)
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

    // 处理文件选择
    handleFileSelect(event) {
      const file = event.target.files[0];
      if (!file) {
        this.selectedFile = null;
        return;
      }
      // 验证文件大小
      const isLt50M = file.size / 1024 / 1024 < 50;
      if (!isLt50M) {
        this.$message.error('上传文件大小不能超过 50MB!');
        event.target.value = ''; // 清空输入
        this.selectedFile = null;
        return;
      }
      this.selectedFile = file;
      console.log('文件已选择:', file.name, file.size);
    },

    // 刷新资源
    refreshResources() {
      this.loadResources();
      this.$message.success('资源列表已刷新');
    },

    // 提交上传
    submitUpload() {
      console.log('uploadAction:', this.uploadAction);
      console.log('selectedFile:', this.selectedFile);

      if (!this.uploadForm.ctid) {
        this.$message.error('请选择课程');
        return;
      }

      if (!this.selectedFile) {
        this.$message.error('请选择文件');
        return;
      }

      // 使用FormData提交
      const formData = new FormData();
      formData.append('ctid', this.uploadForm.ctid);
      formData.append('description', this.uploadForm.description || '');
      formData.append('file', this.selectedFile);

      const that = this;
      const uploadUrl = 'http://localhost:10086/resource/upload';
      console.log('Uploading to:', uploadUrl);
      axios.post(uploadUrl, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      .then(function(resp) {
        if (resp.data.success) {
          that.$message.success('资源上传成功');
          that.resetUploadForm();
          that.loadResources(); // 刷新资源列表
        } else {
          that.$message.error(resp.data.message || '上传失败');
        }
      })
      .catch(function(error) {
        console.error('上传失败:', error);
        that.$message.error('上传失败: ' + (error.response?.data?.message || error.message));
      });
    },

    // 重置上传表单
    resetUploadForm() {
      this.uploadForm = {
        ctid: null,
        description: ''
      };
      this.selectedFile = null;
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = '';
      }
    },

    // 过滤资源
    filterResources() {
      let filtered = this.allResources;

      // 按课程筛选
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

    // 下载资源
    downloadResource(rid) {
      const url = `http://localhost:10086/resource/download/${rid}`;
      window.open(url, '_blank');
    },

    // 删除资源
    deleteResource(rid) {
      const that = this;
      axios.get(`http://localhost:10086/resource/delete/${rid}`)
        .then(function(resp) {
          if (resp.data.success) {
            that.$message.success('资源删除成功');
            that.loadResources(); // 刷新列表
          } else {
            that.$message.error(resp.data.message || '删除失败');
          }
        })
        .catch(function(error) {
          console.error('删除失败:', error);
          that.$message.error('删除失败');
        });
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
.mb-24 {
  margin-bottom: 24px;
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
