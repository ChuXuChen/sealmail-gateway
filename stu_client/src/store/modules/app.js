const state = {
  // 侧边栏是否折叠
  sidebar: {
    opened: true,
    withoutAnimation: false
  },
  // 设备类型：desktop/mobile
  device: 'desktop',
  // 主题色
  theme: '#1890FF',
  // 是否显示标签栏
  tagsView: true,
  // 是否固定头部
  fixedHeader: true,
  // 是否显示logo
  sidebarLogo: true
}

const mutations = {
  TOGGLE_SIDEBAR: state => {
    state.sidebar.opened = !state.sidebar.opened
    state.sidebar.withoutAnimation = false
  },
  CLOSE_SIDEBAR: (state, withoutAnimation) => {
    state.sidebar.opened = false
    state.sidebar.withoutAnimation = withoutAnimation
  },
  TOGGLE_DEVICE: (state, device) => {
    state.device = device
  },
  SET_THEME: (state, theme) => {
    state.theme = theme
  },
  SET_TAGS_VIEW: (state, tagsView) => {
    state.tagsView = tagsView
  },
  SET_FIXED_HEADER: (state, fixedHeader) => {
    state.fixedHeader = fixedHeader
  },
  SET_SIDEBAR_LOGO: (state, sidebarLogo) => {
    state.sidebarLogo = sidebarLogo
  }
}

const actions = {
  toggleSideBar({ commit }) {
    commit('TOGGLE_SIDEBAR')
  },
  closeSideBar({ commit }, { withoutAnimation }) {
    commit('CLOSE_SIDEBAR', withoutAnimation)
  },
  toggleDevice({ commit }, device) {
    commit('TOGGLE_DEVICE', device)
  },
  setTheme({ commit }, theme) {
    commit('SET_THEME', theme)
  },
  setTagsView({ commit }, tagsView) {
    commit('SET_TAGS_VIEW', tagsView)
  },
  setFixedHeader({ commit }, fixedHeader) {
    commit('SET_FIXED_HEADER', fixedHeader)
  },
  setSidebarLogo({ commit }, sidebarLogo) {
    commit('SET_SIDEBAR_LOGO', sidebarLogo)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
