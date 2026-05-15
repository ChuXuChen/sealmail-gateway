import request from '../utils/request'

/**
 * 获取学生列表
 * @param {Object} params 查询参数
 * @returns {Promise} 学生列表
 */
export function getStudentList(params) {
  return request.post('/student/findBySearch', params)
}

/**
 * 获取学生详情
 * @param {Number} id 学生ID
 * @returns {Promise} 学生详情
 */
export function getStudentDetail(id) {
  return request.get(`/student/detail/${id}`)
}

/**
 * 新增学生
 * @param {Object} data 学生信息
 * @returns {Promise} 新增结果
 */
export function addStudent(data) {
  return request.post('/student/add', data)
}

/**
 * 修改学生信息
 * @param {Object} data 学生信息
 * @returns {Promise} 修改结果
 */
export function updateStudent(data) {
  return request.post('/student/update', data)
}

/**
 * 删除学生
 * @param {Number} id 学生ID
 * @returns {Promise} 删除结果
 */
export function deleteStudent(id) {
  return request.get(`/student/deleteById/${id}`)
}

/**
 * 批量删除学生
 * @param {Array} ids 学生ID列表
 * @returns {Promise} 删除结果
 */
export function batchDeleteStudent(ids) {
  return request.post('/student/batchDelete', { ids })
}

/**
 * 导入学生
 * @param {FormData} file 导入文件
 * @returns {Promise} 导入结果
 */
export function importStudent(file) {
  return request.post('/student/import', file, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 导出学生
 * @param {Object} params 导出参数
 * @returns {Promise} 导出文件
 */
export function exportStudent(params) {
  return request.export('/student/export', params, '学生列表.xlsx')
}

/**
 * 重置学生密码
 * @param {Number} sid 学生ID
 * @returns {Promise} 重置结果
 */
export function resetPassword(sid) {
  return request.get(`/student/resetPassword/${sid}`)
}
