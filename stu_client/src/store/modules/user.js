import { login, logout, getUserInfo } from '../../api/user'

const state = {
  token: sessionStorage.getItem('token') || '',
  userInfo: {},
  // 用户类型：1-学生 2-教师 3-管理员
  userType: sessionStorage.getItem('type') || '',
  userId: sessionStorage.getItem('id') || '',
  userName: sessionStorage.getItem('name') || ''
}

const mutations = {
  SET_TOKEN: (state, token) => {
    state.token = token
    sessionStorage.setItem('token', token)
  },
  SET_USER_INFO: (state, userInfo) => {
    state.userInfo = userInfo
  },
  SET_USER_TYPE: (state, userType) => {
    state.userType = userType
    sessionStorage.setItem('type', userType)
  },
  SET_USER_ID: (state, userId) => {
    state.userId = userId
    sessionStorage.setItem('id', userId)
  },
  SET_USER_NAME: (state, userName) => {
    state.userName = userName
    sessionStorage.setItem('name', userName)
  },
  CLEAR_USER_INFO: (state) => {
    state.token = ''
    state.userInfo = {}
    state.userType = ''
    state.userId = ''
    state.userName = ''
    sessionStorage.clear()
  }
}

const actions = {
  // 登录
  Login({ commit }, userInfo) {
    return new Promise((resolve) => {
      commit('SET_TOKEN', userInfo.token)
      commit('SET_USER_TYPE', userInfo.type)
      commit('SET_USER_ID', userInfo.id)
      commit('SET_USER_NAME', userInfo.name)
      resolve(userInfo)
    })
  },

  // 获取用户信息
  GetUserInfo({ commit, state }) {
    return new Promise((resolve, reject) => {
      getUserInfo().then(res => {
        commit('SET_USER_INFO', res)
        resolve(res)
      }).catch(error => {
        reject(error)
      })
    })
  },

  // 登出
  Logout({ commit, state }) {
    return new Promise((resolve, reject) => {
      logout().then(() => {
        commit('CLEAR_USER_INFO')
        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  },

  // 前端登出，不调用接口
  FedLogout({ commit }) {
    return new Promise(resolve => {
      commit('CLEAR_USER_INFO')
      resolve()
    })
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
