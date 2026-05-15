<template>
  <div>
    <el-container>
      <el-main>
        <el-card>
          <div slot="header">
            <span>测验管理</span>
            <el-button-group style="float: right;">
              <el-button type="success" size="small" @click="showAIGradingDialog">
                <i class="el-icon-s-check"></i> AI智能评阅
              </el-button>
              <el-button type="warning" size="small" @click="showAIQuizDialog">
                <i class="el-icon-s-operation"></i> AI智能组卷
              </el-button>
              <el-button type="primary" size="small" @click="showAddQuizDialog">
                <i class="el-icon-plus"></i> 新建测验
              </el-button>
            </el-button-group>
          </div>

          <!-- 搜索和筛选 -->
          <el-form :inline="true" :model="searchForm" label-width="80px">
            <el-form-item label="课程筛选">
              <el-select v-model="searchForm.ctid" placeholder="全部课程" clearable @change="filterQuizzes">
                <el-option label="全部课程" :value="null"></el-option>
                <el-option
                  v-for="course in teacherCourses"
                  :key="course.ctid"
                  :label="course.cname + ' (' + course.term + ')'"
                  :value="course.ctid">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="测验标题">
              <el-input v-model="searchForm.quizTitle" placeholder="输入测验标题" clearable @input="filterQuizzes"></el-input>
            </el-form-item>
            <el-form-item label="发布状态">
              <el-select v-model="searchForm.publishStatus" placeholder="全部" clearable @change="filterQuizzes">
                <el-option label="全部" :value="null"></el-option>
                <el-option label="已发布" :value="1"></el-option>
                <el-option label="未发布" :value="0"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="测验状态">
              <el-select v-model="searchForm.status" placeholder="全部状态" clearable @change="filterQuizzes">
                <el-option label="全部状态" :value="null"></el-option>
                <el-option label="未开始" :value="0"></el-option>
                <el-option label="进行中" :value="1"></el-option>
                <el-option label="已结束" :value="2"></el-option>
              </el-select>
            </el-form-item>
          </el-form>

          <!-- 测验表格 -->
          <el-table :data="filteredQuizzes" border stripe style="width: 100%">
            <el-table-column prop="quizTitle" label="测验标题" width="200" show-overflow-tooltip></el-table-column>
            <el-table-column prop="courseName" label="课程" width="150"></el-table-column>
            <el-table-column prop="term" label="学期" width="120"></el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="180">
              <template slot-scope="scope">
                {{ formatDate(scope.row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="180">
              <template slot-scope="scope">
                {{ formatDate(scope.row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="时长(分钟)" width="100"></el-table-column>
            <el-table-column prop="totalScore" label="总分" width="80"></el-table-column>
            <el-table-column label="发布状态" width="100">
              <template slot-scope="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'">
                  {{ scope.row.status === 1 ? '已发布' : '未发布' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="测验状态" width="100">
              <template slot-scope="scope">
                <el-tag :type="getStatusType(scope.row)">
                  {{ getStatusText(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" size="small" @click="viewQuizDetail(scope.row)">
                  详情
                </el-button>
                <el-button type="text" size="small" @click="editQuiz(scope.row)">
                  编辑
                </el-button>
                <!-- 发布/取消发布按钮 -->
                <el-button
                  v-if="scope.row.status !== 1"
                  type="text"
                  size="small"
                  style="color: #67c23a;"
                  @click="publishQuiz(scope.row)">
                  发布
                </el-button>
                <el-button
                  v-else
                  type="text"
                  size="small"
                  style="color: #e6a23c;"
                  @click="unpublishQuiz(scope.row)">
                  取消发布
                </el-button>
                <el-popconfirm
                  title="确定删除这个测验吗？"
                  @confirm="deleteQuiz(scope.row.quizId)">
                  <el-button slot="reference" type="text" size="small" style="color: #f56c6c;">
                    删除
                  </el-button>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <!-- 空状态 -->
          <div v-if="filteredQuizzes.length === 0" style="text-align: center; padding: 40px 0; color: #909399;">
            <i class="el-icon-document" style="font-size: 48px; margin-bottom: 16px;"></i>
            <p>暂无测验</p>
          </div>
        </el-card>
      </el-main>
    </el-container>

    <!-- 新建/编辑测验对话框 -->
    <el-dialog
      title="测验信息"
      :visible.sync="quizDialogVisible"
      width="60%"
      :close-on-click-modal="false">
      <el-form :model="quizForm" :rules="quizRules" ref="quizForm" label-width="100px">
        <el-form-item label="所属课程" prop="ctid">
          <el-select v-model="quizForm.ctid" placeholder="请选择课程" style="width: 100%">
            <el-option
              v-for="course in teacherCourses"
              :key="course.ctid"
              :label="course.cname + ' (' + course.term + ')'"
              :value="course.ctid">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="测验标题" prop="quizTitle">
          <el-input v-model="quizForm.quizTitle" placeholder="请输入测验标题" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="测验描述" prop="quizDescription">
          <el-input
            v-model="quizForm.quizDescription"
            type="textarea"
            :rows="3"
            placeholder="请输入测验描述（可选）"
            style="width: 100%">
          </el-input>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="quizForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
            value-format="yyyy-MM-dd HH:mm:ss">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="quizForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            style="width: 100%"
            value-format="yyyy-MM-dd HH:mm:ss">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="测验时长(分钟)" prop="duration">
          <el-input-number
            v-model="quizForm.duration"
            :min="10"
            :max="180"
            :step="5"
            placeholder="请输入时长"
            style="width: 100%">
          </el-input-number>
        </el-form-item>
        <el-form-item label="总分" prop="totalScore">
          <el-input-number
            v-model="quizForm.totalScore"
            :min="10"
            :max="1000"
            :step="10"
            placeholder="请输入总分"
            style="width: 100%">
          </el-input-number>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="quizDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuiz">确定</el-button>
      </span>
    </el-dialog>

    <!-- 测验详情对话框 -->
    <el-dialog
      title="测验详情"
      :visible.sync="quizDetailVisible"
      width="80%"
      :close-on-click-modal="false">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="测验信息" name="info">
          <el-descriptions title="测验基本信息" :column="2">
            <el-descriptions-item label="课程">
              {{ currentQuiz.courseName }}
            </el-descriptions-item>
            <el-descriptions-item label="学期">
              {{ currentQuiz.term }}
            </el-descriptions-item>
            <el-descriptions-item label="测验标题">
              {{ currentQuiz.quizTitle }}
            </el-descriptions-item>
            <el-descriptions-item label="总分">
              {{ currentQuiz.totalScore }} 分
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">
              {{ formatDate(currentQuiz.startTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ formatDate(currentQuiz.endTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="测验时长">
              {{ currentQuiz.duration }} 分钟
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentQuiz)">
                {{ getStatusText(currentQuiz) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="测验描述" :span="2">
              {{ currentQuiz.quizDescription || '无' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="题目管理" name="questions">
          <el-button type="primary" size="small" style="margin-bottom: 15px;" @click="showAddQuestionDialog">
            <i class="el-icon-plus"></i> 添加题目
          </el-button>
          <!-- 总分显示 -->
          <div style="margin-bottom: 15px; padding: 12px; background: #f0f9ff; border-radius: 4px; border: 1px solid #b3d8ff;">
            <span style="font-weight: bold; font-size: 14px;">📊 当前题目总分：</span>
            <span style="color: #409eff; font-size: 20px; font-weight: bold;">
              {{ questionList.reduce((sum, q) => sum + (q.questionScore || 0), 0) }} 分
            </span>
            <span style="margin-left: 20px; color: #606266;">
              （测验设置总分：{{ currentQuiz.totalScore || 0 }} 分）
            </span>
          </div>
          <el-table :data="questionList" border stripe style="width: 100%">
            <el-table-column prop="questionOrder" label="题号" width="60"></el-table-column>
            <el-table-column label="题型" width="100">
              <template slot-scope="scope">
                {{ getQuestionTypeText(scope.row.questionType) }}
              </template>
            </el-table-column>
            <el-table-column prop="questionContent" label="题目内容" show-overflow-tooltip></el-table-column>
            <el-table-column prop="questionScore" label="分值" width="80"></el-table-column>
            <el-table-column label="操作" width="120">
              <template slot-scope="scope">
                <el-button type="text" size="small" @click="editQuestion(scope.row)">
                  编辑
                </el-button>
                <el-popconfirm
                  title="确定删除这个题目吗？"
                  @confirm="deleteQuestion(scope.row.questionId)">
                  <el-button slot="reference" type="text" size="small" style="color: #f56c6c;">
                    删除
                  </el-button>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学生成绩" name="scores">
          <el-table :data="scoreList" border stripe style="width: 100%">
            <el-table-column prop="studentName" label="学生姓名" width="120"></el-table-column>
            <el-table-column prop="obtainedScore" label="得分" width="80">
              <template slot-scope="scope">
                {{ scope.row.obtainedScore || 0 }}
              </template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="80"></el-table-column>
            <el-table-column label="状态" width="100">
              <template slot-scope="scope">
                <el-tag :type="scope.row.isSubmitted ? 'success' : 'warning'">
                  {{ scope.row.isSubmitted ? '已提交' : '未提交' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template slot-scope="scope">
                <el-button type="text" size="small" @click="viewStudentAnswer(scope.row)">
                  查看答案
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>


    <!-- 题目管理对话框 -->
    <el-dialog
      :title="editingQuestion ? '编辑题目' : '添加题目'"
      :visible.sync="questionDialogVisible"
      width="70%"
      :close-on-click-modal="false">
      <el-form :model="questionForm" :rules="questionRules" ref="questionForm" label-width="80px">
        <el-form-item label="题目类型" prop="questionType">
          <el-radio-group v-model="questionForm.questionType" @change="handleQuestionTypeChange">
            <el-radio :label="1">单选题</el-radio>
            <el-radio :label="2">多选题</el-radio>
            <el-radio :label="3">判断题</el-radio>
            <el-radio :label="4">填空题</el-radio>
            <el-radio :label="5">简答题</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题目内容" prop="questionContent">
          <el-input
            v-model="questionForm.questionContent"
            type="textarea"
            :rows="3"
            placeholder="请输入题目内容"
            style="width: 100%">
          </el-input>
        </el-form-item>
        <template v-if="questionForm.questionType === 1 || questionForm.questionType === 2 || questionForm.questionType === 3">
          <el-form-item label="选项" prop="options">
            <!-- 判断题固定选项 -->
            <template v-if="questionForm.questionType === 3">
              <div>
                <el-input
                  v-model="questionOptions[0].content"
                  placeholder="请输入选项内容"
                  style="width: 80%; margin-right: 10px;"
                  :readonly="true">
                  <template slot="prepend">
                    {{ getOptionLabel(0) }}
                  </template>
                </el-input>
              </div>
              <div style="margin-top: 10px;">
                <el-input
                  v-model="questionOptions[1].content"
                  placeholder="请输入选项内容"
                  style="width: 80%; margin-right: 10px;"
                  :readonly="true">
                  <template slot="prepend">
                    {{ getOptionLabel(1) }}
                  </template>
                </el-input>
              </div>
            </template>
            <!-- 单选题和多选题可编辑选项 -->
            <template v-else>
              <div v-for="(option, index) in questionOptions" :key="index">
                <el-input
                  v-model="option.content"
                  placeholder="请输入选项内容"
                  style="width: 80%; margin-right: 10px;">
                  <template slot="prepend">
                    {{ getOptionLabel(index) }}
                  </template>
                </el-input>
                <el-button
                  type="text"
                  size="small"
                  style="color: #f56c6c;"
                  v-if="questionOptions.length > 1"
                  @click="removeOption(index)">
                  删除
                </el-button>
              </div>
              <el-button type="text" size="small" @click="addOption" style="margin-top: 10px;">
                <i class="el-icon-plus"></i> 添加选项
              </el-button>
            </template>
          </el-form-item>
        </template>
        <el-form-item label="正确答案" prop="correctAnswer">
          <!-- 判断题正确答案选择 -->
          <template v-if="questionForm.questionType === 3">
            <el-select v-model="questionForm.correctAnswer" placeholder="请选择正确答案" style="width: 100%">
              <el-option label="正确" value="正确"></el-option>
              <el-option label="错误" value="错误"></el-option>
            </el-select>
          </template>
          <!-- 其他题型正确答案输入 -->
          <template v-else>
            <el-input
              v-model="questionForm.correctAnswer"
              type="textarea"
              :rows="2"
              placeholder="请输入正确答案"
              style="width: 100%">
            </el-input>
          </template>
        </el-form-item>
        <el-form-item label="题目分数" prop="questionScore">
          <el-input-number
            v-model="questionForm.questionScore"
            :min="1"
            :max="100"
            :step="1"
            placeholder="请输入题目分数"
            style="width: 100%">
          </el-input-number>
        </el-form-item>
        <el-form-item label="题目顺序" prop="questionOrder">
          <el-input-number
            v-model="questionForm.questionOrder"
            :min="0"
            :max="1000"
            :step="1"
            placeholder="请输入题目顺序"
            style="width: 100%">
          </el-input-number>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="questionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuestion">确定</el-button>
      </span>
    </el-dialog>

    <!-- AI组卷对话框 -->
    <el-dialog
      title="AI智能组卷"
      :visible.sync="aiQuizDialogVisible"
      width="80%"
      :close-on-click-modal="false">
      <el-form :model="aiQuizForm" label-width="100px">
        <el-form-item label="选择课程" prop="ctid">
          <el-select v-model="aiQuizForm.ctid" placeholder="请选择课程" style="width: 100%" @change="onCourseChange">
            <el-option
              v-for="course in teacherCourses"
              :key="course.ctid"
              :label="course.cname + ' (' + course.term + ')'"
              :value="course.ctid">
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="选择资源" prop="selectedResources">
          <el-checkbox-group v-model="aiQuizForm.selectedResources">
            <el-checkbox v-for="resource in availableResources" :key="resource.rid" :label="resource.rid">
              {{ resource.filename }}
              <span style="color: #999; margin-left: 10px;">
                ({{ formatFileSize(resource.filesize) }})
              </span>
              <span v-if="resource.description" style="color: #999; margin-left: 10px;">
                - {{ resource.description }}
              </span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="题目结构" prop="questionStructure">
          <el-input
            v-model="aiQuizForm.questionStructure"
            type="textarea"
            :rows="3"
            placeholder="请输入题目结构，例如：
            {
              '单选题': 5,
              '多选题': 3,
              '判断题': 4,
              '简答题': 2
            }"
            style="width: 100%">
          </el-input>
        </el-form-item>

        <el-form-item label="难度等级" prop="difficultyLevel">
          <el-radio-group v-model="aiQuizForm.difficultyLevel">
            <el-radio :label="1">简单</el-radio>
            <el-radio :label="2">中等</el-radio>
            <el-radio :label="3">困难</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="组卷进度">
          <el-progress v-if="aiGenerationProgress > 0" :percentage="aiGenerationProgress" status="active"></el-progress>
          <span v-if="aiGenerationStatus">{{ aiGenerationStatus }}</span>
          <span v-else>未开始组卷</span>
        </el-form-item>
      </el-form>

      <span slot="footer">
        <el-button @click="aiQuizDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="generateQuizByAI" :loading="aiGenerating">开始组卷</el-button>
      </span>
    </el-dialog>

    <!-- AI评阅对话框 -->
    <el-dialog
      title="AI智能评阅"
      :visible.sync="aiGradingDialogVisible"
      width="70%"
      :close-on-click-modal="false">
      <el-form :model="aiGradingForm" label-width="100px">
        <el-form-item label="选择测验" prop="quizId">
          <el-select v-model="aiGradingForm.quizId" placeholder="请选择测验" style="width: 100%" @change="onGradingQuizChange">
            <el-option
              v-for="quiz in pendingQuizzes"
              :key="quiz.quizId"
              :label="quiz.quizTitle + ' (' + quiz.courseName + ')'"
              :value="quiz.quizId">
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="评阅类型">
          <el-radio-group v-model="aiGradingForm.gradingType">
            <el-radio :label="1">全部主观题</el-radio>
            <el-radio :label="2">指定学生</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="aiGradingForm.gradingType === 2" label="选择学生" prop="sid">
          <el-select v-model="aiGradingForm.sid" placeholder="请选择学生" style="width: 100%">
            <el-option
              v-for="student in pendingStudents"
              :key="student.sid"
              :label="student.studentName + ' (' + student.sid + ')'"
              :value="student.sid">
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="评阅进度">
          <el-progress v-if="aiGradingProgress > 0" :percentage="aiGradingProgress" status="active"></el-progress>
          <span v-if="aiGradingStatus">{{ aiGradingStatus }}</span>
          <span v-else>未开始评阅</span>
        </el-form-item>
      </el-form>

      <span slot="footer">
        <el-button @click="aiGradingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="startAIGrading" :loading="aiGrading">开始评阅</el-button>
      </span>
    </el-dialog>

    <!-- 学生答案批阅对话框 -->
    <el-dialog
      title="学生答案批阅"
      :visible.sync="studentAnswerDialogVisible"
      width="70%"
      :close-on-click-modal="false"
      @close="closeStudentAnswerDialog">
      <el-descriptions title="学生信息" :column="2">
        <el-descriptions-item label="学生姓名">
          {{ currentStudentScore.studentName }}
        </el-descriptions-item>
        <el-descriptions-item label="学号">
          {{ currentStudentScore.sid }}
        </el-descriptions-item>
        <el-descriptions-item label="当前得分">
          {{ currentStudentScore.obtainedScore || 0 }} / {{ currentStudentScore.totalScore }}
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">
          {{ currentStudentScore.submitTime ? formatDate(currentStudentScore.submitTime) : '未提交' }}
        </el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 20px;">
        <h4>答案详情：</h4>
        <div v-for="answer in studentAnswers" :key="answer.answerId" class="answer-item">
          <el-card :class="{ 'pending': answer.gradingStatus === 0, 'graded': answer.gradingStatus === 1, 'teacher-graded': answer.gradingStatus === 2 }">
            <div class="answer-header">
              <span class="question-type">
                {{ getQuestionTypeText(answer.questionType) }}
              </span>
              <span class="question-score">({{ answer.questionScore }}分)</span>
              <el-tag :type="getGradingStatusType(answer.gradingStatus)" size="small">
                {{ getGradingStatusText(answer.gradingStatus) }}
              </el-tag>
            </div>
            <div class="question-content">
              {{ answer.questionContent }}
            </div>
            <div v-if="answer.questionType === 1 || answer.questionType === 2 || answer.questionType === 3" class="question-options">
              <div v-for="(opt, index) in parseOptions(answer.options)" :key="index" class="option">
                <el-checkbox :value="isSelectedOption(answer.studentAnswer, index)" :disabled="true">
                  {{ String.fromCharCode(65 + index) }}. {{ opt.content }}
                </el-checkbox>
              </div>
            </div>
            <div v-if="answer.questionType === 4 || answer.questionType === 5" class="student-answer">
              <div class="answer-label">学生答案：</div>
              <div class="answer-text">{{ formatStudentAnswer(answer) }}</div>
            </div>
            <div class="correct-answer">
              <div class="answer-label">正确答案：</div>
              <div class="answer-text">{{ formatCorrectAnswer(answer) }}</div>
            </div>
            <div v-if="answer.aiScore !== null" class="ai-analysis">
              <el-divider content-position="left">AI评分分析</el-divider>
              <div class="analysis-text">{{ answer.aiAnalysis }}</div>
              <div class="analysis-score">
                <span style="font-weight: bold;">AI评分：</span> {{ answer.aiScore }}分
              </div>
            </div>
            <div v-if="answer.questionType === 4 || answer.questionType === 5" class="teacher-grading">
              <el-divider content-position="left">教师评分</el-divider>
              <el-form :inline="true" size="small">
                <el-form-item label="得分">
                  <el-input-number
                    v-model="answer.teacherScore"
                    :min="0"
                    :max="answer.questionScore"
                    :step="0.5"
                    style="width: 100px">
                  </el-input-number>
                </el-form-item>
                <el-form-item label="评语">
                  <el-input
                    v-model="answer.teacherComment"
                    placeholder="请输入评语"
                    style="width: 300px">
                  </el-input>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" size="small" @click="saveTeacherScore(answer.answerId, answer.teacherScore || 0, answer.teacherComment || '')">
                    保存评分
                  </el-button>
                  <el-button type="info" size="small" @click="startAIGradingForAnswer(answer.answerId)" :loading="gradingLoading[answer.answerId]">
                    AI评分
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
            <div v-if="answer.teacherComment" class="teacher-comment">
              <div class="comment-label">教师评语：</div>
              <div class="comment-text">{{ answer.teacherComment }}</div>
            </div>
          </el-card>
        </div>
      </div>

      <span slot="footer">
        <el-button @click="closeStudentAnswerDialog">关闭</el-button>
        <el-button type="info" @click="batchAIGradingForStudent">批量AI评分</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'QuizManage',
  data() {
    return {
      // 教师ID
      tid: null,

      // 教师开设的课程列表
      teacherCourses: [],

      // 所有测验数据
      allQuizzes: [],

      // 筛选后的测验数据
      filteredQuizzes: [],

      // 搜索表单
      searchForm: {
        ctid: null,
        quizTitle: '',
        status: null,
        publishStatus: null
      },

      // AI组卷相关
      aiQuizDialogVisible: false,
      aiQuizForm: {
        ctid: null,
        selectedResources: [],
        questionStructure: '',
        difficultyLevel: 2
      },
      availableResources: [],
      aiGenerating: false,
      aiGenerationStatus: '',
      aiGenerationProgress: 0,

      // AI评阅相关
      aiGradingDialogVisible: false,
      aiGradingForm: {
        quizId: null,
        gradingType: 1,
        sid: null
      },
      pendingQuizzes: [],
      pendingStudents: [],
      aiGrading: false,
      aiGradingStatus: '',
      aiGradingProgress: 0,
      gradingLoading: {},

      // 测验对话框状态
      quizDialogVisible: false,
      isEditingQuiz: false,
      quizForm: {
        quizId: null,
        ctid: null,
        quizTitle: '',
        quizDescription: '',
        startTime: '',
        endTime: '',
        duration: 60,
        totalScore: 100
      },

      // 测验表单验证规则
      quizRules: {
        ctid: [{ required: true, message: '请选择课程', trigger: 'change' }],
        quizTitle: [{ required: true, message: '请输入测验标题', trigger: 'blur' }],
        startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
        endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
        duration: [{ required: true, message: '请输入测验时长', trigger: 'blur' }],
        totalScore: [{ required: true, message: '请输入总分', trigger: 'blur' }]
      },

      // 测验详情
      quizDetailVisible: false,
      currentQuiz: {},
      activeTab: 'info',
      questionList: [],
      scoreList: [],

      // 题目管理
      questionDialogVisible: false,
      isEditingQuestion: false,
      questionForm: {
        questionId: null,
        quizId: null,
        questionType: 1,
        questionContent: '',
        options: '',
        correctAnswer: '',
        questionScore: 10,
        questionOrder: 0
      },
      questionOptions: [
        { content: '' },
        { content: '' }
      ],

      // 题目表单验证规则
      questionRules: {
        questionType: [{ required: true, message: '请选择题目类型', trigger: 'change' }],
        questionContent: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
        questionScore: [{ required: true, message: '请输入题目分数', trigger: 'blur' }],
        questionOrder: [{ required: true, message: '请输入题目顺序', trigger: 'blur' }]
      },

      // 学生答案批阅
      studentAnswerDialogVisible: false,
      currentStudentScore: {},
      studentAnswers: [],
      gradingActiveNames: ['1']
    };
  },
  created() {
    this.tid = sessionStorage.getItem('tid');
    this.loadTeacherCourses();
    this.loadQuizzes();
  },
  methods: {
    // 加载教师开设的课程
    loadTeacherCourses() {
      const term = sessionStorage.getItem('currentTerm') || '26-春季学期';
      const that = this;

      const searchParams = {
        tid: this.tid,
        term: term
      };

      axios.post('http://localhost:10086/courseTeacher/findCourseTeacherInfo', searchParams)
        .then(function(resp) {
          const courses = resp.data;
          that.teacherCourses = courses.map(course => ({
            ctid: course.ctid,
            cid: course.cid,
            cname: course.cname,
            term: term
          }));
        })
        .catch(function(error) {
          console.error('加载课程失败:', error);
          that.$message.error('加载课程失败');
        });
    },

    // 加载测验数据
    loadQuizzes() {
      const that = this;
      return new Promise((resolve, reject) => {
        axios.get(`http://localhost:10086/quiz/findByTeacher/${this.tid}`)
          .then(function(resp) {
            console.log('从后端获取的测验数据:', resp.data);
            // 打印第一条数据的详细信息
            if (resp.data && resp.data.length > 0) {
              console.log('第一条测验数据的详细信息:', JSON.stringify(resp.data[0], null, 2));
              console.log('测验标题:', resp.data[0].quizTitle);
              console.log('课程名称:', resp.data[0].courseName);
              console.log('开始时间:', resp.data[0].startTime);
              console.log('结束时间:', resp.data[0].endTime);
              console.log('总分:', resp.data[0].totalScore);
              console.log('状态字段:', resp.data[0].status);
            }
            that.allQuizzes = resp.data;
            that.filteredQuizzes = resp.data;
            resolve(resp.data);
          })
          .catch(function(error) {
            console.error('加载测验失败:', error);
            that.$message.error('加载测验失败');
            reject(error);
          });
      });
    },

    // 过滤测验
    filterQuizzes() {
      let filtered = this.allQuizzes;

      // 按课程筛选
      if (this.searchForm.ctid) {
        filtered = filtered.filter(quiz => quiz.ctid === this.searchForm.ctid);
      }

      // 按标题筛选
      if (this.searchForm.quizTitle) {
        const keyword = this.searchForm.quizTitle.toLowerCase();
        filtered = filtered.filter(quiz =>
          quiz.quizTitle.toLowerCase().includes(keyword)
        );
      }

      // 按发布状态筛选
      if (this.searchForm.publishStatus !== null) {
        filtered = filtered.filter(quiz => {
          return quiz.status === this.searchForm.publishStatus;
        });
      }

      // 按测验状态筛选（时间状态）
      if (this.searchForm.status !== null) {
        filtered = filtered.filter(quiz => {
          const now = new Date();
          const startTime = new Date(quiz.startTime);
          const endTime = new Date(quiz.endTime);

          if (now < startTime) {
            return this.searchForm.status === 0;
          } else if (now >= startTime && now <= endTime) {
            return this.searchForm.status === 1;
          } else {
            return this.searchForm.status === 2;
          }
        });
      }

      this.filteredQuizzes = filtered;
    },

    // 获取状态类型
    getStatusType(quiz) {
      if (!quiz.startTime || !quiz.endTime) return 'info';
      const now = new Date();
      const startTime = this.parseDate(quiz.startTime);
      const endTime = this.parseDate(quiz.endTime);

      if (now < startTime) return 'warning';
      if (now <= endTime) return 'success';
      return 'info';
    },

    // 获取状态文本
    getStatusText(quiz) {
      if (!quiz.startTime || !quiz.endTime) return '未知';
      const now = new Date();
      const startTime = this.parseDate(quiz.startTime);
      const endTime = this.parseDate(quiz.endTime);

      if (now < startTime) return '未开始';
      if (now <= endTime) return '进行中';
      return '已结束';
    },

    // 解析日期
    parseDate(dateString) {
      if (!dateString) return new Date();

      // 处理各种日期格式
      let parsedDate;

      try {
        if (typeof dateString === 'string') {
          // 处理各种日期字符串格式
          if (dateString.includes('CST') || dateString.includes('GMT')) {
            // 处理 "Sun Mar 01 00:00:00 CST 2026" 格式
            parsedDate = new Date(dateString);
          } else if (dateString.includes('T')) {
            // 处理 "2026-03-01T00:00:00" 格式
            parsedDate = new Date(dateString);
          } else if (dateString.includes(' ')) {
            // 处理 "2026-03-01 00:00:00" 格式
            parsedDate = new Date(dateString);
          } else {
            parsedDate = new Date(dateString);
          }
        } else if (typeof dateString === 'object') {
          // 处理日期对象
          parsedDate = new Date(dateString);
        } else {
          parsedDate = new Date();
        }

        // 验证日期是否有效
        if (isNaN(parsedDate.getTime())) {
          console.error('无效的日期格式:', dateString);
          return new Date();
        }

        return parsedDate;
      } catch (e) {
        console.error('日期解析失败:', dateString, e);
        return new Date();
      }
    },

    // 格式化日期
    formatDate(dateString) {
      if (!dateString) return '';

      try {
        const date = this.parseDate(dateString);
        // 格式化日期为 YYYY-MM-DD HH:mm:ss
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');

        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
      } catch (e) {
        console.error('日期格式化失败:', dateString);
        return '';
      }
    },

    // 显示新建测验对话框
    showAddQuizDialog() {
      this.isEditingQuiz = false;
      this.quizForm = {
        quizId: null,
        ctid: null,
        quizTitle: '',
        quizDescription: '',
        startTime: '',
        endTime: '',
        duration: 60,
        totalScore: 100
      };
      this.quizDialogVisible = true;
    },

    // 保存测验
    saveQuiz() {
      const that = this;
      this.$refs.quizForm.validate((valid) => {
        if (valid) {
          // 检查时间是否合法
          if (new Date(that.quizForm.startTime) >= new Date(that.quizForm.endTime)) {
            that.$message.error('结束时间必须晚于开始时间');
            return;
          }

          console.log('发送给后端的测验数据:', that.quizForm);
          const request = this.isEditingQuiz
            ? axios.post('http://localhost:10086/quiz/update', that.quizForm)
            : axios.post('http://localhost:10086/quiz/save', that.quizForm);

          request.then(function(resp) {
            if (resp.data.success) {
              that.$message.success(that.isEditingQuiz ? '测验更新成功' : '测验创建成功');
              that.quizDialogVisible = false;
              that.loadQuizzes();
            } else {
              that.$message.error(resp.data.message || (that.isEditingQuiz ? '更新失败' : '创建失败'));
            }
          }).catch(function(error) {
            console.error('保存测验失败:', error);
            that.$message.error('保存失败');
          });
        }
      });
    },

    // 编辑测验
    editQuiz(quiz) {
      console.log('编辑测验数据:', quiz);
      const that = this;
      // 先从后端获取最新的完整数据
      axios.get(`http://localhost:10086/quiz/findById/${quiz.quizId}`)
        .then(function(resp) {
          const fullQuiz = resp.data;
          console.log('从后端获取的完整测验数据:', fullQuiz);
          that.isEditingQuiz = true;
          that.quizForm = {
            quizId: fullQuiz.quizId,
            ctid: fullQuiz.ctid,
            quizTitle: fullQuiz.quizTitle,
            quizDescription: fullQuiz.quizDescription,
            startTime: that.formatDateForInput(fullQuiz.startTime),
            endTime: that.formatDateForInput(fullQuiz.endTime),
            duration: fullQuiz.duration,
            totalScore: fullQuiz.totalScore
          };
          that.quizDialogVisible = true;
        })
        .catch(function(error) {
          console.error('获取测验详情失败:', error);
          that.$message.error('获取测验详情失败');
        });
    },

    // 格式化日期为输入框使用的格式 (YYYY-MM-DD HH:mm:ss)
    formatDateForInput(dateString) {
      if (!dateString) return '';
      const date = this.parseDate(dateString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    },

    // 发布测验
    publishQuiz(quiz) {
      const that = this;
      this.$confirm('确定要发布该测验吗？发布后学生将可以看到并参加测验。', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        axios.post(`http://localhost:10086/quiz/publish/${quiz.quizId}`)
          .then(function(resp) {
            if (resp.data.success) {
              that.$message.success('测验发布成功');
              that.loadQuizzes();
            } else {
              that.$message.error(resp.data.message || '发布失败');
            }
          })
          .catch(function(error) {
            console.error('发布测验失败:', error);
            that.$message.error('发布失败');
          });
      }).catch(() => {
        // 取消发布
      });
    },

    // 取消发布测验
    unpublishQuiz(quiz) {
      const that = this;
      this.$confirm('确定要取消发布该测验吗？取消后学生将无法看到该测验。', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        axios.post(`http://localhost:10086/quiz/unpublish/${quiz.quizId}`)
          .then(function(resp) {
            if (resp.data.success) {
              that.$message.success('测验已取消发布');
              that.loadQuizzes();
            } else {
              that.$message.error(resp.data.message || '取消发布失败');
            }
          })
          .catch(function(error) {
            console.error('取消发布测验失败:', error);
            that.$message.error('取消发布失败');
          });
      }).catch(() => {
        // 取消操作
      });
    },

    // 删除测验
    deleteQuiz(quizId) {
      const that = this;
      axios.get(`http://localhost:10086/quiz/delete/${quizId}`)
        .then(function(resp) {
          if (resp.data.success) {
            that.$message.success('测验删除成功');
            that.loadQuizzes();
          } else {
            that.$message.error(resp.data.message || '删除失败');
          }
        })
        .catch(function(error) {
          console.error('删除测验失败:', error);
          that.$message.error('删除失败');
        });
    },

    // 查看测验详情
    viewQuizDetail(quiz) {
      console.log('查看详情的测验数据:', quiz);
      // 先通过 ID 获取完整的测验数据
      const that = this;
      axios.get(`http://localhost:10086/quiz/findById/${quiz.quizId}`)
        .then(function(resp) {
          that.currentQuiz = resp.data;
          that.quizDetailVisible = true;
          that.loadQuestionList(resp.data.quizId);
          that.loadScoreList(resp.data.quizId);
        })
        .catch(function(error) {
          console.error('获取测验详情失败:', error);
          that.$message.error('获取测验详情失败');
        });
    },

    // 加载题目列表
    loadQuestionList(quizId) {
      const that = this;
      if (quizId === null || quizId === undefined || quizId === 'null') {
        that.questionList = [];
        return;
      }
      axios.get(`http://localhost:10086/quizQuestion/findByQuizId/${quizId}`)
        .then(function(resp) {
          that.questionList = resp.data;
        })
        .catch(function(error) {
          console.error('加载题目失败:', error);
          that.$message.error('加载题目失败');
        });
    },

    // 自动更新测验总分
    async updateQuizTotalScore(quizId) {
      try {
        const resp = await axios.get(`http://localhost:10086/quizQuestion/findByQuizId/${quizId}`);
        const questions = resp.data || [];
        const totalScore = questions.reduce((sum, q) => sum + (q.questionScore || 0), 0);
        // 更新测验总分
        await axios.post('http://localhost:10086/quiz/update', {
          quizId: quizId,
          totalScore: totalScore
        });
        // 刷新测验列表
        await this.loadQuizzes();
        // 更新当前测验的总分
        if (this.currentQuiz && this.currentQuiz.quizId === quizId) {
          this.currentQuiz.totalScore = totalScore;
        }
        return totalScore;
      } catch (error) {
        console.error('更新测验总分失败:', error);
      }
    },

    // 加载成绩列表
    loadScoreList(quizId) {
      const that = this;
      if (quizId === null || quizId === undefined || quizId === 'null') {
        that.scoreList = [];
        return;
      }
      axios.get(`http://localhost:10086/quizScore/findAllStudentsWithScores/${quizId}`)
        .then(function(resp) {
          that.scoreList = resp.data;
        })
        .catch(function(error) {
          console.error('加载成绩失败:', error);
          that.$message.error('加载成绩失败');
        });
    },

    // 获取题目类型文本
    getQuestionTypeText(type) {
      const types = ['', '单选题', '多选题', '判断题', '填空题', '简答题'];
      return types[type] || '未知题型';
    },

    // 获取评分状态类型
    getGradingStatusType(status) {
      const types = {
        0: 'warning',  // 待批改
        1: 'info',    // AI评分完成
        2: 'success'  // 教师评分完成
      };
      return types[status] || 'info';
    },

    // 获取评分状态文本
    getGradingStatusText(status) {
      const texts = {
        0: '待批改',
        1: 'AI评分完成',
        2: '教师评分完成'
      };
      return texts[status] || '未知状态';
    },

    // 获取选项标签
    getOptionLabel(index) {
      return String.fromCharCode(65 + index); // A, B, C, D...
    },

    // 解析题目选项
    parseQuestionOptions(answer) {
      try {
        if (!answer.options) return [];
        const options = JSON.parse(answer.options);
        return options.map(opt => opt.content);
      } catch (e) {
        return [];
      }
    },

    // 格式化学生答案
    formatStudentAnswer(answer) {
      return this.formatAnswerText(answer.studentAnswer, answer.questionType);
    },

    // 格式化正确答案
    formatCorrectAnswer(answer) {
      // 如果有教师评分，优先显示教师评分的分数
      if (answer.teacherScore !== null && answer.teacherScore !== undefined) {
        return answer.correctAnswer || '未设置';
      }
      return answer.correctAnswer || '未设置';
    },

    // 格式化答案文本
    formatAnswerText(answerStr, questionType) {
      if (!answerStr) return '未作答';
      try {
        let answer = answerStr;
        try {
          answer = JSON.parse(answerStr);
        } catch (e) {
          // 不是JSON，使用原始值
        }

        // 处理数组（多选题）
        if (Array.isArray(answer)) {
          return answer.join(', ');
        }

        // 转换为字符串
        const answerStrVal = String(answer);

        // 判断题特殊处理：将A/B转换为"正确"/"错误"
        if (questionType === 3) {
          if (answerStrVal === 'A' || answerStrVal === '1' || answerStrVal.toLowerCase() === 'true') {
            return '正确';
          } else if (answerStrVal === 'B' || answerStrVal === '0' || answerStrVal.toLowerCase() === 'false') {
            return '错误';
          }
        }

        return answerStrVal;
      } catch (error) {
        return answerStr;
      }
    },

    // 解析选项
    parseOptions(optionsStr) {
      if (!optionsStr) return [];
      try {
        const parsed = JSON.parse(optionsStr);

        // 格式1: 数组格式 [{"option":"A","content":"xxx"}]
        if (Array.isArray(parsed)) {
          return parsed.map((opt, index) => ({
            option: opt.option || opt.label || String.fromCharCode(65 + index),
            content: opt.content || opt.value || opt.text || ''
          }));
        }

        // 格式2: 对象格式 {"A":"内容","B":"内容"}
        if (typeof parsed === 'object' && parsed !== null) {
          return Object.keys(parsed).map(key => ({
            option: key,
            content: parsed[key]
          }));
        }

        return [];
      } catch (error) {
        console.error('解析选项失败:', error, optionsStr);
        return [];
      }
    },

    // 检查选项是否被选中
    isSelectedOption(studentAnswer, index) {
      if (!studentAnswer) return false;
      const optionChar = String.fromCharCode(65 + index); // A, B, C, D...
      // 处理数组格式的答案（多选题）
      try {
        const parsed = JSON.parse(studentAnswer);
        if (Array.isArray(parsed)) {
          return parsed.includes(optionChar);
        }
      } catch (e) {
        // 不是JSON数组，按字符串处理
      }
      // 处理字符串格式的答案
      return String(studentAnswer).includes(optionChar);
    },

    // 处理题目类型变化
    handleQuestionTypeChange(newType) {
      // 如果是判断题，固定设置选项为'正确'和'错误'
      if (newType === 3) {
        this.questionOptions = [
          { content: '正确' },
          { content: '错误' }
        ];
        // 如果正确答案不是'正确'或'错误'，清空正确答案
        if (this.questionForm.correctAnswer !== '正确' && this.questionForm.correctAnswer !== '错误') {
          this.questionForm.correctAnswer = '';
        }
      } else {
        // 其他题型重置为空选项
        this.questionOptions = [
          { content: '' },
          { content: '' }
        ];
      }
    },

    // 显示题目管理对话框
    showAddQuestionDialog() {
      console.log('当前测验数据:', this.currentQuiz);
      this.isEditingQuestion = false;
      // 确保 quizId 不为 null 或 undefined
      if (!this.currentQuiz || !this.currentQuiz.quizId) {
        this.$message.error('请先选择要添加题目的测验');
        return;
      }

      this.questionForm = {
        questionId: null,
        quizId: this.currentQuiz.quizId,
        questionType: 1,
        questionContent: '',
        options: '',
        correctAnswer: '',
        questionScore: 10,
        questionOrder: 0
      };
      this.questionOptions = [
        { content: '' },
        { content: '' }
      ];
      this.questionDialogVisible = true;
    },

    // 添加选项
    addOption() {
      if (this.questionOptions.length < 10) {
        this.questionOptions.push({ content: '' });
      } else {
        this.$message.warning('选项数量已达到上限');
      }
    },

    // 删除选项
    removeOption(index) {
      if (this.questionOptions.length > 1) {
        this.questionOptions.splice(index, 1);
      } else {
        this.$message.warning('至少保留一个选项');
      }
    },

    // 保存题目
    saveQuestion() {
      const that = this;
      this.$refs.questionForm.validate((valid) => {
        if (valid) {
          // 处理选项数据
          if (that.questionForm.questionType === 1 || that.questionForm.questionType === 2 || that.questionForm.questionType === 3) {
            // 判断题固定选项，不需要验证选项内容
            let validOptions;
            if (that.questionForm.questionType === 3) {
              validOptions = [
                { content: '正确' },
                { content: '错误' }
              ];
            } else {
              validOptions = that.questionOptions.filter(opt => opt.content.trim() !== '');
              if (validOptions.length < 2) {
                that.$message.error('请至少输入两个有效的选项');
                return;
              }
            }
            that.questionForm.options = JSON.stringify(validOptions);
          } else {
            that.questionForm.options = '';
          }

          const request = this.isEditingQuestion
            ? axios.post('http://localhost:10086/quizQuestion/update', that.questionForm)
            : axios.post('http://localhost:10086/quizQuestion/save', that.questionForm);

          request.then(function(resp) {
            if (resp.data.success) {
              that.$message.success(that.isEditingQuestion ? '题目更新成功' : '题目添加成功');
              that.questionDialogVisible = false;
              that.loadQuestionList(that.currentQuiz.quizId);
              // 自动更新测验总分
              that.updateQuizTotalScore(that.currentQuiz.quizId);
            } else {
              that.$message.error(resp.data.message || (that.isEditingQuestion ? '更新失败' : '添加失败'));
            }
          }).catch(function(error) {
            console.error('保存题目失败:', error);
            that.$message.error('保存失败');
          });
        }
      });
    },

    // 编辑题目
    editQuestion(question) {
      this.isEditingQuestion = true;
      this.questionForm = { ...question };
      if (question.options) {
        // 判断题直接使用固定选项
        if (question.questionType === 3) {
          this.questionOptions = [
            { content: '正确' },
            { content: '错误' }
          ];
        } else {
          this.questionOptions = JSON.parse(question.options);
          while (this.questionOptions.length < 2) {
            this.questionOptions.push({ content: '' });
          }
        }
      } else {
        // 如果没有选项
        if (question.questionType === 3) {
          this.questionOptions = [
            { content: '正确' },
            { content: '错误' }
          ];
        } else {
          this.questionOptions = [
            { content: '' },
            { content: '' }
          ];
        }
      }
      this.questionDialogVisible = true;
    },

    // 删除题目
    deleteQuestion(questionId) {
      const that = this;
      axios.get(`http://localhost:10086/quizQuestion/delete/${questionId}`)
        .then(function(resp) {
          if (resp.data.success) {
            that.$message.success('题目删除成功');
            that.loadQuestionList(that.currentQuiz.quizId);
            // 自动更新测验总分
            that.updateQuizTotalScore(that.currentQuiz.quizId);
          } else {
            that.$message.error(resp.data.message || '删除失败');
          }
        })
        .catch(function(error) {
          console.error('删除题目失败:', error);
          that.$message.error('删除失败');
        });
    },

    // 查看学生答案
    viewStudentAnswer(score) {
      // 确保之前的对话框状态已经清理
      if (this.studentAnswerDialogVisible) {
        this.studentAnswerDialogVisible = false;
      }
      // 延迟显示对话框以避免状态冲突
      setTimeout(() => {
        // 显示学生答案详情对话框
        this.studentAnswerDialogVisible = true;
        this.currentStudentScore = score;
        this.loadStudentAnswers(score.sid);
      }, 300);
    },

    // 加载学生答案
    loadStudentAnswers(sid) {
      const that = this;
      axios.get(`http://localhost:10086/quizAnswer/findByQuizAndStudent/${this.currentQuiz.quizId}/${sid}`)
        .then(function(resp) {
          that.studentAnswers = resp.data;
          console.log('加载学生答案:', resp.data);
        })
        .catch(function(error) {
          console.error('加载学生答案失败:', error);
          that.$message.error('加载学生答案失败');
        });
    },
    // 关闭学生答案对话框
    closeStudentAnswerDialog() {
      // 清理数据
      this.studentAnswers = [];
      this.currentStudentScore = {};
      // 重置对话框状态
      this.studentAnswerDialogVisible = false;
      // 确保所有状态都已清理
      this.$nextTick(() => {
        this.studentAnswers = [];
        this.currentStudentScore = {};
      });
    },

    // 保存教师评分
    saveTeacherScore(answerId, score, comment) {
      const that = this;
      const answer = this.studentAnswers.find(item => item.answerId === answerId);
      if (answer) {
        answer.teacherScore = score;
        answer.teacherComment = comment;
        answer.gradingStatus = 2; // 教师评分完成
        answer.isCorrect = score >= answer.questionScore * 0.6; // 60%分以上为正确
        answer.score = score;

        axios.post('http://localhost:10086/quizAnswer/update', answer)
          .then(function(resp) {
            if (resp.data.success) {
              that.$message.success('评分保存成功');
              // 刷新成绩列表
              that.loadScoreList(that.currentQuiz.quizId);
            } else {
              that.$message.error(resp.data.message || '保存失败');
            }
          })
          .catch(function(error) {
            console.error('保存评分失败:', error);
            that.$message.error('保存失败');
          });
      }
    },

    // 显示AI组卷对话框
    showAIQuizDialog() {
      this.aiQuizForm = {
        ctid: null,
        selectedResources: [],
        questionStructure: '',
        difficultyLevel: 2
      };
      this.aiGenerating = false;
      this.aiGenerationStatus = '';
      this.aiGenerationProgress = 0;
      this.availableResources = [];
      this.aiQuizDialogVisible = true;
    },

    // 监听课程变化，加载对应资源
    onCourseChange(ctid) {
      const that = this;
      if (ctid) {
        axios.get(`http://localhost:10086/resource/findByCtid/${ctid}`)
          .then(function(resp) {
            that.availableResources = resp.data;
          })
          .catch(function(error) {
            console.error('加载资源失败:', error);
            that.availableResources = [];
          });
      } else {
        this.availableResources = [];
      }
    },

    // 调用AI生成测验
    generateQuizByAI() {
      const that = this;
      if (!this.aiQuizForm.ctid) {
        this.$message.error('请选择课程');
        return;
      }
      if (this.aiQuizForm.selectedResources.length === 0) {
        this.$message.error('请至少选择一个资源');
        return;
      }

      this.aiGenerating = true;
      this.aiGenerationStatus = '正在调用AI生成测验...';
      this.aiGenerationProgress = 10;

      axios.post('http://localhost:10086/quiz/generateByAI', this.aiQuizForm)
        .then(function(resp) {
          if (resp.data.success) {
            const taskId = resp.data.taskId;
            that.$message.success('AI组卷任务已提交，正在生成...');
            that.aiGenerationStatus = '正在生成测验...';
            that.pollGenerationStatus(taskId);
          } else {
            that.$message.error(resp.data.message || 'AI组卷失败');
            that.aiGenerating = false;
          }
        })
        .catch(function(error) {
          console.error('AI组卷失败:', error);
          that.$message.error('AI组卷失败: ' + (error.response?.data?.message || error.message));
          that.aiGenerating = false;
        });
    },

    // 轮询查询AI组卷状态
    pollGenerationStatus(taskId) {
      const that = this;
      const pollInterval = setInterval(() => {
        axios.get(`http://localhost:10086/quiz/generationStatus/${taskId}`)
          .then(function(resp) {
            if (resp.data.success) {
              const data = resp.data;
              that.aiGenerationProgress = data.progress;

              if (data.status === 'completed') {
                clearInterval(pollInterval);
                that.aiGenerationStatus = '组卷完成！';
                that.aiGenerationProgress = 100;
                that.aiGenerating = false;
                that.$message.success('AI组卷完成！');
                that.aiQuizDialogVisible = false;
                that.loadQuizzes(); // 刷新测验列表
              } else if (data.status === 'failed') {
                clearInterval(pollInterval);
                that.aiGenerationStatus = '组卷失败: ' + data.error;
                that.aiGenerating = false;
                that.$message.error('AI组卷失败: ' + data.error);
              } else if (data.status === 'processing') {
                that.aiGenerationStatus = '正在处理...';
              }
            } else {
              clearInterval(pollInterval);
              that.aiGenerating = false;
              that.$message.error(resp.data.message || '查询组卷状态失败');
            }
          })
          .catch(function(error) {
            clearInterval(pollInterval);
            that.aiGenerating = false;
            console.error('查询组卷状态失败:', error);
            that.$message.error('查询组卷状态失败');
          });
      }, 2000); // 每2秒查询一次
    },

    // 显示AI评阅对话框
    showAIGradingDialog() {
      this.aiGradingForm = {
        quizId: null,
        gradingType: 1,
        sid: null
      };
      this.aiGrading = false;
      this.aiGradingStatus = '';
      this.aiGradingProgress = 0;
      this.pendingQuizzes = [];
      this.pendingStudents = [];

      const that = this;
      // 重新加载测验数据以确保数据最新
      this.loadQuizzes()
        .then(() => {
          // 加载所有测验
          this.pendingQuizzes = this.allQuizzes;
          console.log('AI评阅可用测验列表:', this.pendingQuizzes.length, this.pendingQuizzes);
        })
        .catch(error => {
          console.error('加载测验数据失败:', error);
        });

      this.aiGradingDialogVisible = true;
    },

    // 监听测验变化，加载学生列表
    onGradingQuizChange(quizId) {
      const that = this;
      if (quizId) {
        axios.get(`http://localhost:10086/quizScore/findAllStudentsWithScores/${quizId}`)
          .then(function(resp) {
            that.pendingStudents = resp.data;
          })
          .catch(function(error) {
            console.error('加载学生列表失败:', error);
            that.pendingStudents = [];
          });
      } else {
        this.pendingStudents = [];
      }
    },

    // 开始AI评阅
    startAIGrading() {
      const that = this;
      if (!this.aiGradingForm.quizId) {
        this.$message.error('请选择测验');
        return;
      }

      this.aiGrading = true;
      this.aiGradingStatus = '正在调用AI评阅...';
      this.aiGradingProgress = 10;

      axios.post(`http://localhost:10086/quizAnswer/batchGradeByAI/${this.aiGradingForm.quizId}`)
        .then(function(resp) {
          if (resp.data.success) {
            that.$message.success(resp.data.message);
            that.aiGradingProgress = 100;
            that.aiGradingStatus = '评阅完成！';
            setTimeout(() => {
              that.aiGrading = false;
              that.aiGradingDialogVisible = false;
            }, 1000);
          } else {
            that.$message.error(resp.data.message || 'AI评阅失败');
            that.aiGrading = false;
          }
        })
        .catch(function(error) {
          console.error('AI评阅失败:', error);
          that.$message.error('AI评阅失败: ' + (error.response?.data?.message || error.message));
          that.aiGrading = false;
        });
    },

    // 单个答案AI评分
    startAIGradingForAnswer(answerId) {
      const that = this;
      this.$set(this.gradingLoading, answerId, true);

      const answer = this.studentAnswers.find(a => a.answerId === answerId);
      if (!answer) {
        this.$message.error('答案不存在');
        this.$set(this.gradingLoading, answerId, false);
        return;
      }

      axios.post('http://localhost:10086/quizAnswer/gradeByAI', {
        answerId: answerId,
        questionContent: answer.questionContent,
        correctAnswer: answer.correctAnswer,
        studentAnswer: answer.studentAnswer,
        questionScore: answer.questionScore
      })
      .then(function(resp) {
        if (resp.data.success) {
          that.$message.success('AI评分完成');
          answer.aiScore = resp.data.aiScore;
          answer.aiAnalysis = resp.data.aiAnalysis;
          answer.gradingStatus = 1; // AI评分完成
          // 将AI评分作为初始教师评分
          answer.teacherScore = answer.aiScore;
          answer.score = answer.aiScore;
        } else {
          that.$message.error(resp.data.message || 'AI评分失败');
        }
      })
      .catch(function(error) {
        console.error('AI评分失败:', error);
        that.$message.error('AI评分失败');
      })
      .finally(function() {
        that.$set(that.gradingLoading, answerId, false);
      });
    },

    // 学生所有答案批量AI评分
    batchAIGradingForStudent() {
      const that = this;
      this.$confirm('确定要对该学生的所有主观题进行AI评分吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        let count = 0;
        let total = 0;

        // 统计需要评分的主观题
        this.studentAnswers.forEach(answer => {
          if (answer.questionType === 5) {
            total++;
          }
        });

        if (total === 0) {
          this.$message.info('没有需要评分的主观题');
          return;
        }

        // 逐个评分
        this.studentAnswers.forEach(answer => {
          if (answer.questionType === 5 && answer.gradingStatus !== 1) {
            this.startAIGradingForAnswer(answer.answerId);
            count++;
          }
        });

        this.$message.success(`已提交 ${count} 道题的AI评分`);
      }).catch(() => {
        // 用户取消
      });
    },

    // 工具函数：格式化文件大小
    formatFileSize(bytes) {
      if (bytes === 0 || !bytes) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
  }
};
</script>

<style scoped>
/* AI组卷和评阅样式 */
.answer-item {
  margin-bottom: 20px;
}

.answer-item .el-card {
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.answer-item .el-card.pending {
  border-left: 4px solid #e6a23c;
}

.answer-item .el-card.graded {
  border-left: 4px solid #67c23a;
}

.answer-item .el-card.teacher-graded {
  border-left: 4px solid #409eff;
}

.answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #ebedf0;
}

.question-type {
  font-weight: bold;
  color: #303133;
  margin-right: 10px;
}

.question-score {
  color: #606266;
  margin-right: 10px;
}

.question-content {
  font-size: 16px;
  color: #303133;
  margin-bottom: 15px;
  line-height: 1.6;
}

.question-options {
  margin-bottom: 15px;
}

.question-options .option {
  margin-bottom: 8px;
}

.student-answer,
.correct-answer,
.ai-analysis {
  margin-bottom: 15px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.answer-label {
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.answer-text {
  color: #606266;
  line-height: 1.6;
}

.analysis-text {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}

.analysis-score {
  color: #409eff;
  font-weight: bold;
}

.teacher-grading {
  margin-top: 15px;
}

.teacher-grading .el-form {
  margin-top: 10px;
}

.teacher-comment {
  margin-top: 15px;
  padding: 12px;
  background-color: #e6f7ff;
  border-radius: 4px;
}

.comment-label {
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.comment-text {
  color: #606266;
  line-height: 1.6;
}

/* AI功能界面样式 */
.ai-quiz-form .el-form-item {
  margin-bottom: 20px;
}

.ai-quiz-form .el-form-item__label {
  font-weight: bold;
  color: #303133;
}

.resource-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #f5f7fa;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .answer-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .question-type,
  .question-score {
    margin-bottom: 5px;
  }

  .teacher-grading .el-form {
    margin-top: 15px;
  }
}
</style>
