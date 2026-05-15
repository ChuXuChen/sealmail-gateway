/**
 * 通用工具函数
 */

/**
 * 日期格式化
 * @param {Date|String|Number} date 日期
 * @param {String} format 格式化字符串，默认yyyy-MM-dd HH:mm:ss
 * @returns {String} 格式化后的日期字符串
 */
export function formatDate(date, format = 'yyyy-MM-dd HH:mm:ss') {
  if (!date) return ''
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  const second = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('yyyy', year)
    .replace('MM', month)
    .replace('dd', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second)
}

/**
 * 获取当前登录用户信息
 * @returns {Object} 用户信息
 */
export function getCurrentUser() {
  return {
    id: sessionStorage.getItem('id'),
    name: sessionStorage.getItem('name'),
    type: sessionStorage.getItem('type'),
    token: sessionStorage.getItem('token')
  }
}

/**
 * 深拷贝
 * @param {*} obj 要拷贝的对象
 * @returns {*} 拷贝后的对象
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime())
  if (obj instanceof Array) return obj.map(item => deepClone(item))
  if (typeof obj === 'object') {
    const clonedObj = {}
    for (const key in obj) {
      if (obj.hasOwnProperty(key)) {
        clonedObj[key] = deepClone(obj[key])
      }
    }
    return clonedObj
  }
}

/**
 * 防抖函数
 * @param {Function} func 要执行的函数
 * @param {Number} delay 延迟时间，默认300ms
 * @returns {Function} 防抖后的函数
 */
export function debounce(func, delay = 300) {
  let timeoutId
  return function (...args) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => func.apply(this, args), delay)
  }
}

/**
 * 节流函数
 * @param {Function} func 要执行的函数
 * @param {Number} delay 延迟时间，默认300ms
 * @returns {Function} 节流后的函数
 */
export function throttle(func, delay = 300) {
  let lastTime = 0
  return function (...args) {
    const now = Date.now()
    if (now - lastTime >= delay) {
      lastTime = now
      func.apply(this, args)
    }
  }
}

/**
 * 下载文件
 * @param {Blob} blob 文件流
 * @param {String} fileName 文件名
 */
export function downloadFile(blob, fileName) {
  const downloadUrl = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = downloadUrl
  a.download = fileName
  a.click()
  window.URL.revokeObjectURL(downloadUrl)
}

/**
 * 复制文本到剪贴板
 * @param {String} text 要复制的文本
 * @returns {Promise} 复制结果
 */
export function copyText(text) {
  return new Promise((resolve, reject) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        resolve(true)
      }).catch(err => {
        reject(err)
      })
    } else {
      // 降级方案
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      try {
        document.execCommand('copy')
        resolve(true)
      } catch (err) {
        reject(err)
      }
      document.body.removeChild(textarea)
    }
  })
}

/**
 * 生成随机ID
 * @param {Number} length ID长度，默认8位
 * @returns {String} 随机ID
 */
export function generateRandomId(length = 8) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

/**
 * 数组去重
 * @param {Array} arr 要去重的数组
 * @param {String} key 根据对象的key去重，可选
 * @returns {Array} 去重后的数组
 */
export function uniqueArray(arr, key) {
  if (!key) {
    return [...new Set(arr)]
  }
  const map = new Map()
  return arr.filter(item => !map.has(item[key]) && map.set(item[key], 1))
}
