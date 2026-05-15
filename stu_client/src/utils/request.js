import axios from 'axios'
import router from '../router'

// 创建axios实例
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || '/api',
  timeout: 10000 // 请求超时时间
})

// 请求计数器，用于loading控制
let requestCount = 0

// 显示loading
const showLoading = () => {
  if (requestCount === 0) {
    // 全局loading，这里可以根据需求实现
    const loading = document.createElement('div')
    loading.id = 'global-loading'
    loading.style = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.7);
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
    `
    loading.innerHTML = `
      <div style="
        padding: 20px 40px;
        background: #fff;
        border-radius: 8px;
        box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
      ">
        <i class="el-icon-loading" style="font-size: 24px; color: #1890FF; margin-right: 10px;"></i>
        <span>加载中...</span>
      </div>
    `
    document.body.appendChild(loading)
  }
  requestCount++
}

// 隐藏loading
const hideLoading = () => {
  requestCount--
  if (requestCount <= 0) {
    requestCount = 0
    const loading = document.getElementById('global-loading')
    if (loading) {
      document.body.removeChild(loading)
    }
  }
}

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 显示loading，排除不需要loading的请求
    if (!config.hideLoading) {
      showLoading()
    }

    // 添加token
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }

    // 添加用户信息到请求头，用于后端权限校验
    const userType = sessionStorage.getItem('type')
    const userId = userType === '1' || userType === 'student'
      ? sessionStorage.getItem('sid')
      : sessionStorage.getItem('tid')
    if (userType) {
      config.headers['X-User-Type'] = userType
    }
    if (userId) {
      config.headers['X-User-Id'] = userId
    }

    // 设置请求头
    config.headers['Content-Type'] = config.headers['Content-Type'] || 'application/json;charset=UTF-8'

    return config
  },
  error => {
    hideLoading()
    console.error('请求错误：', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    hideLoading()
    const res = response.data

    // 这里可以根据后端的响应码做统一处理
    // 假设后端返回格式：{ code: 200, data: {}, message: 'success' }
    if (res.code !== undefined && res.code !== 200) {
      // 处理错误
      if (res.code === 401) {
        // 未授权，跳转到登录页
        sessionStorage.clear()
        router.push('/login')
        return Promise.reject(new Error('登录已过期，请重新登录'))
      } else if (res.code === 403) {
        // 无权限
        return Promise.reject(new Error('没有权限访问该资源'))
      } else {
        // 其他错误
        return Promise.reject(new Error(res.message || '请求失败'))
      }
    } else {
      // 直接返回数据部分
      return res.data !== undefined ? res.data : res
    }
  },
  error => {
    hideLoading()
    console.error('响应错误：', error)
    let message = '网络异常，请稍后重试'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '登录已过期，请重新登录'
          sessionStorage.clear()
          router.push('/login')
          break
        case 403:
          message = '没有权限访问该资源'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = `请求错误：${error.response.status}`
      }
    } else if (error.message.includes('timeout')) {
      message = '请求超时，请稍后重试'
    }
    // 显示错误提示
    if (window.Vue) {
      window.Vue.prototype.$message.error(message)
    }
    return Promise.reject(new Error(message))
  }
)

// 封装请求方法
const request = {
  get(url, params, config = {}) {
    return service.get(url, { params, ...config })
  },
  post(url, data, config = {}) {
    return service.post(url, data, config)
  },
  put(url, data, config = {}) {
    return service.put(url, data, config)
  },
  delete(url, params, config = {}) {
    return service.delete(url, { params, ...config })
  },
  // 导出文件
  export(url, params, fileName = '导出文件.xlsx', config = {}) {
    return service.post(url, params, {
      responseType: 'blob',
      ...config
    }).then(res => {
      const blob = new Blob([res])
      const downloadUrl = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = downloadUrl
      a.download = fileName
      a.click()
      window.URL.revokeObjectURL(downloadUrl)
    })
  }
}

export default request
