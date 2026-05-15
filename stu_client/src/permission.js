import router from './router'
import store from './store'
import { Message } from 'element-ui'

// 白名单，不需要登录就可以访问的页面
const whiteList = ['/login', '/404']

router.beforeEach(async(to, from, next) => {
  // 获取token，优先从sessionStorage取，避免store没初始化导致的登录判断错误
  const hasToken = sessionStorage.getItem('token') || store.getters.token

  if (hasToken) {
    if (to.path === '/login') {
      // 已经登录，跳转到首页
      next({ path: '/' })
    } else {
      // 获取用户类型，优先从sessionStorage取，避免store没初始化导致的问题
      const userType = sessionStorage.getItem('type') || store.getters.userType
      if (userType) {
        // 权限校验，判断用户是否有权限访问该页面
        // 获取所有匹配路由的roles配置，父路由和子路由都需要有权限
        const matchedRoles = to.matched.reduce((roles, route) => {
          if (route.meta && route.meta.roles) {
            return roles.concat(route.meta.roles)
          }
          return roles
        }, [])

        // 如果路由有roles配置，判断用户类型是否在允许的角色列表中
        if (matchedRoles.length > 0) {
          if (matchedRoles.includes(userType)) {
            next()
          } else {
            Message.error('没有权限访问该页面')
            // 跳转到对应用户的首页
            let homePath = '/login'
            if (userType === '1' || userType === 'student') {
              homePath = '/studentHome'
            } else if (userType === '2' || userType === 'teacher') {
              homePath = '/teacherHome'
            } else if (userType === '3' || userType === 'admin') {
              homePath = '/adminHome'
            }
            next({ path: homePath })
          }
        } else {
          // 没有配置权限的路由，直接允许访问
          next()
        }
      } else {
        try {
          // 获取用户信息
          await store.dispatch('user/GetUserInfo')
          next({ ...to, replace: true })
        } catch (error) {
          // 获取用户信息失败，跳转到登录页
          await store.dispatch('user/FedLogout')
          Message.error(error || '登录已过期，请重新登录')
          next(`/login?redirect=${to.path}`)
        }
      }
    }
  } else {
    // 没有token
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

router.afterEach(() => {
  // 路由跳转后可以做一些处理，比如关闭loading等
})

router.afterEach(() => {
  // 路由跳转后可以做一些处理，比如关闭loading等
})
