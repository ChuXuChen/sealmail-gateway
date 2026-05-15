import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
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
