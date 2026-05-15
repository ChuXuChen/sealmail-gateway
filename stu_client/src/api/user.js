import request from '../utils/request'

/**
 * 用户登录
 * @param {Object} data 登录信息
 * @param {String} data.username 用户名
 * @param {String} data.password 密码
 * @param {Number} data.type 用户类型 1-学生 2-教师 3-管理员
 * @returns {Promise} 登录结果
 */
export function login(data) {
  return request.post('/login', data)
}

/**
 * 用户登出
 * @returns {Promise} 登出结果
 */
export function logout() {
  return request.post('/logout')
}

/**
 * 获取用户信息
 * @returns {Promise} 用户信息
 */
export function getUserInfo() {
  return request.get('/user/info')
}

/**
 * 修改密码
 * @param {Object} data 密码信息
 * @param {String} data.oldPassword 旧密码
 * @param {String} data.newPassword 新密码
 * @returns {Promise} 修改结果
 */
export function updatePassword(data) {
  return request.post('/user/updatePassword', data)
}

/**
 * 修改用户信息
 * @param {Object} data 用户信息
 * @returns {Promise} 修改结果
 */
export function updateUserInfo(data) {
  return request.post('/user/updateInfo', data)
}
