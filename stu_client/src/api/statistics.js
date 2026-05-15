import request from '../utils/request'

/**
 * 获取成绩分布统计
 * @param {Object} params 查询参数
 * @param {Number} params.ctid 开课ID，可选
 * @param {Number} params.tid 教师ID，可选
 * @param {String} params.term 学期，可选
 * @returns {Promise} 成绩分布数据
 */
export function getScoreDistribution(params) {
  return request.get('/statistics/grade/distribution', params)
}

/**
 * 获取分数段统计
 * @param {Object} params 查询参数
 * @param {Number} params.ctid 开课ID，可选
 * @param {Number} params.tid 教师ID，可选
 * @param {String} params.term 学期，可选
 * @returns {Promise} 分数段数据
 */
export function getScoreSegment(params) {
  return request.get('/statistics/grade/score-segment', params)
}

/**
 * 获取课程平均分排名
 * @param {Object} params 查询参数
 * @param {String} params.term 学期，可选
 * @param {Number} params.limit 返回数量，可选
 * @returns {Promise} 平均分排名数据
 */
export function getCourseAverageRank(params) {
  return request.get('/statistics/course/average-rank', params)
}

/**
 * 获取教师课程统计
 * @param {Number} teacherId 教师ID
 * @param {Object} params 查询参数
 * @param {String} params.term 学期，可选
 * @returns {Promise} 教师课程统计数据
 */
export function getTeacherCourseStatistics(teacherId, params) {
  return request.get(`/statistics/teacherCourse/${teacherId}`, params)
}

/**
 * 获取学生成绩统计
 * @param {Number} studentId 学生ID
 * @returns {Promise} 学生成绩统计数据
 */
export function getStudentScoreStatistics(studentId) {
  return request.get(`/statistics/studentScore/${studentId}`)
}

/**
 * 获取系统概览统计
 * @returns {Promise} 系统概览数据
 */
export function getSystemOverview() {
  return request.get('/statistics/systemOverview')
}
