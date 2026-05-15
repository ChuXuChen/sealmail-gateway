import Vue from 'vue'
import Element from 'element-ui'
import '../assets/styles/element-variables.scss'

Vue.use(Element, {
  size: 'medium', // 统一组件默认尺寸
  zIndex: 3000 // 弹窗zIndex初始值
})
