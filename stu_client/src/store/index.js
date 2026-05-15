import Vue from 'vue'
import Vuex from 'vuex'
import createPersistedState from 'vuex-persistedstate'
import user from './modules/user'
import app from './modules/app'
import cache from './modules/cache'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    user,
    app,
    cache
  },
  // 持久化存储配置
  plugins: [
    createPersistedState({
      // 存储的key
      key: 'student-manage-system',
      // 需要持久化的模块
      paths: ['app', 'user'],
      // 存储方式，默认localStorage
      storage: window.localStorage
    })
  ]
})
