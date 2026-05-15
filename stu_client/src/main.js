import Vue from 'vue'
import './plugins/axios'
import App from './App.vue'
import router from './router'
import store from './store'
import './plugins/element.js'
import './assets/styles/global.scss'
import './permission'
import request from './utils/request'
import * as common from './utils/common'
import * as validate from './utils/validate'

// 全局注册通用组件
import PageLayout from './components/PageLayout.vue'
import CommonDialog from './components/CommonDialog.vue'
import SearchForm from './components/SearchForm.vue'
import CommonTable from './components/CommonTable.vue'
import DataForm from './components/DataForm.vue'

Vue.component('PageLayout', PageLayout)
Vue.component('CommonDialog', CommonDialog)
Vue.component('SearchForm', SearchForm)
Vue.component('CommonTable', CommonTable)
Vue.component('DataForm', DataForm)

// 挂载到Vue原型
Vue.prototype.$request = request
Vue.prototype.$common = common
Vue.prototype.$validate = validate

// 全局挂载Vue实例，方便request.js中调用message
window.Vue = Vue

Vue.config.productionTip = false

new Vue({
  router,
  store,
  render: h => h(App),
  mounted() {
    // 页面渲染完成后移除初始加载提示，避免闪烁
    const loading = document.getElementById('app-loading')
    if (loading) {
      loading.remove()
    }
  }
}).$mount('#app')
