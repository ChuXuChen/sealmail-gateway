// 按需引入echarts组件
import * as echarts from 'echarts/core'
// 引入图表类型
import { PieChart, BarChart } from 'echarts/charts'
// 引入提示框、图例、标题、数据集等组件
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent,
  TransformComponent
} from 'echarts/components'
// 引入标签自动布局、全局过渡动画等特性
import { LabelLayout, UniversalTransition } from 'echarts/features'
// 引入Canvas渲染器，这是必须的
import { CanvasRenderer } from 'echarts/renderers'

// 注册需要的组件
echarts.use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent,
  TransformComponent,
  PieChart,
  BarChart,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer
])

export default echarts
