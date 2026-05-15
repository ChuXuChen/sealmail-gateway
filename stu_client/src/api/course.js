import request from '../utils/request'

/**
 * 获取课程列表
 * @param {Object} params 查询参数
 * @returns {Promise} 课程列表
 */
export function getCourseList(params) {
  return request.get('/course/list', params)
}

/**
 * 获取课程详情
 * @param {Number} id 课程ID
 * @returns {Promise} 课程详情
 */
export function getCourseDetail(id) {
  return request.get(`/course/detail/${id}`)
}

/**
 * 新增课程
 * @param {Object} data 课程信息
 * @returns {Promise} 新增结果
 */
export function addCourse(data) {
  return request.post('/course/add', data)
}

/**
 * 修改课程信息
 * @param {Object} data 课程信息
 * @returns {Promise} 修改结果
 */
export function updateCourse(data) {
  return request.post('/course/update', data)
}

/**
 * 删除课程
 * @param {Number} id 课程ID
 * @returns {Promise} 删除结果
 */
export function deleteCourse(id) {
  return request.delete(`/course/delete/${id}`)
}

/**
 * 获取教师的课程列表
 * @param {Number} teacherId 教师ID
 * @param {Object} params 查询参数
 * @returns {Promise} 课程列表
 */
export function getTeacherCourseList(teacherId, params) {
  return request.get(`/course/teacher/${teacherId}`, params)
}

/**
 * 获取学生的课程列表
 * @param {Number} studentId 学生ID
 * @param {Object} params 查询参数
 * @returns {Promise} 课程列表
 */
export function getStudentCourseList(studentId, params) {
  return request.get(`/course/student/${studentId}`, params)
}

/**
 * 学生选课
 * @param {Object} data 选课信息
 * @param {Number} data.studentId 学生ID
 * @param {Number} data.courseId 课程ID
 * @returns {Promise} 选课结果
 */
export function selectCourse(data) {
  return request.post('/course/select', data)
}

/**
 * 学生退课
 * @param {Number} studentId 学生ID
 * @param {Number} courseId 课程ID
 * @returns {Promise} 退课结果
 */
export function dropCourse(studentId, courseId) {
  return request.delete(`/course/drop/${studentId}/${courseId}`)
}
