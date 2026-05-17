import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const includesAny = (id: string, segments: string[]) =>
  segments.some((segment) => id.includes(segment))

const chunkRules: Array<[string, string[]]> = [
  [
    'app-shell',
    [
      '/src/main.tsx',
      '/src/App.tsx',
      '/src/api/',
      '/src/auth/',
      '/src/components/',
      '/src/contexts/',
      '/src/router/',
      '/src/types/',
    ],
  ],
  [
    'react-vendor',
    [
      '/node_modules/react/',
      '/node_modules/react-dom/',
      '/node_modules/react-router',
      '/node_modules/scheduler/',
    ],
  ],
  [
    'antd-data',
    [
      '/node_modules/antd/es/form',
      '/node_modules/antd/es/input',
      '/node_modules/antd/es/input-number',
      '/node_modules/antd/es/select',
      '/node_modules/antd/es/switch',
      '/node_modules/antd/es/table',
      '/node_modules/antd/es/upload',
      '/node_modules/rc-field-form',
      '/node_modules/rc-input',
      '/node_modules/rc-input-number',
      '/node_modules/rc-pagination',
      '/node_modules/rc-resize-observer',
      '/node_modules/rc-select',
      '/node_modules/rc-switch',
      '/node_modules/rc-table',
      '/node_modules/rc-textarea',
      '/node_modules/rc-upload',
      '/node_modules/rc-virtual-list',
    ],
  ],
  [
    'antd-surfaces',
    [
      '/node_modules/@ant-design/icons',
      '/node_modules/@ant-design/icons-svg',
      '/node_modules/antd/es/card',
      '/node_modules/antd/es/col',
      '/node_modules/antd/es/descriptions',
      '/node_modules/antd/es/drawer',
      '/node_modules/antd/es/dropdown',
      '/node_modules/antd/es/grid',
      '/node_modules/antd/es/layout',
      '/node_modules/antd/es/menu',
      '/node_modules/antd/es/message',
      '/node_modules/antd/es/modal',
      '/node_modules/antd/es/notification',
      '/node_modules/antd/es/popconfirm',
      '/node_modules/antd/es/popover',
      '/node_modules/antd/es/progress',
      '/node_modules/antd/es/result',
      '/node_modules/antd/es/row',
      '/node_modules/antd/es/statistic',
      '/node_modules/antd/es/steps',
      '/node_modules/antd/es/tabs',
      '/node_modules/antd/es/tooltip',
      '/node_modules/rc-dialog',
      '/node_modules/rc-drawer',
      '/node_modules/rc-dropdown',
      '/node_modules/rc-menu',
      '/node_modules/rc-overflow',
      '/node_modules/rc-tabs',
      '/node_modules/rc-tooltip',
      '/node_modules/rc-trigger',
    ],
  ],
  [
    'antd-controls',
    [
      '/node_modules/antd/es/alert',
      '/node_modules/antd/es/avatar',
      '/node_modules/antd/es/button',
      '/node_modules/antd/es/empty',
      '/node_modules/antd/es/segmented',
      '/node_modules/antd/es/space',
      '/node_modules/antd/es/spin',
      '/node_modules/antd/es/tag',
      '/node_modules/antd/es/typography',
    ],
  ],
  [
    'antd-core',
    [
      '/node_modules/antd/',
      '/node_modules/@ant-design/',
      '/node_modules/@rc-component/',
      '/node_modules/@ctrl/',
      '/node_modules/dayjs/',
      '/node_modules/rc-',
    ],
  ],
  ['axios', ['/node_modules/axios/']],
]

const manualChunks = (id: string) => {
  return chunkRules.find(([, segments]) => includesAny(id, segments))?.[0]
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      // 匹配所有以 /api 开头的请求
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080', // 后端 Spring Boot 的启动端口
        changeOrigin: true, // 必须开启，确保请求头中的 host 是后端地址
        // 如果你的后端接口不带 /api 前缀，需要开启 rewrite
        // rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
