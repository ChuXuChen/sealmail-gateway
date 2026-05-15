const state = {
  // 缓存的页面
  cachedViews: [],
  // 其他缓存数据
  cacheData: {}
}

const mutations = {
  ADD_CACHED_VIEW: (state, view) => {
    if (state.cachedViews.includes(view.name)) return
    if (view.meta && view.meta.keepAlive) {
      state.cachedViews.push(view.name)
    }
  },
  DEL_CACHED_VIEW: (state, view) => {
    const index = state.cachedViews.indexOf(view.name)
    index > -1 && state.cachedViews.splice(index, 1)
  },
  DEL_ALL_CACHED_VIEWS: state => {
    state.cachedViews = []
  },
  SET_CACHE_DATA: (state, { key, value }) => {
    state.cacheData[key] = value
  },
  DEL_CACHE_DATA: (state, key) => {
    delete state.cacheData[key]
  },
  CLEAR_CACHE_DATA: state => {
    state.cacheData = {}
  }
}

const actions = {
  addCachedView({ commit }, view) {
    commit('ADD_CACHED_VIEW', view)
  },
  delCachedView({ commit }, view) {
    commit('DEL_CACHED_VIEW', view)
  },
  delAllCachedViews({ commit }) {
    commit('DEL_ALL_CACHED_VIEWS')
  },
  setCacheData({ commit }, data) {
    commit('SET_CACHE_DATA', data)
  },
  delCacheData({ commit }, key) {
    commit('DEL_CACHE_DATA', key)
  },
  clearCacheData({ commit }) {
    commit('CLEAR_CACHE_DATA')
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
