import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：只打包模板中实际用到的组件及其样式（含 v-loading 指令）
    // 代码中显式 import 的 ElMessage / ElMessageBox 不经过这里，其样式在 main.ts 中手动引入
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dirs: [], // 项目自己的组件都显式 import，不自动注册
      dts: false, // 组件类型由 tsconfig 中的 element-plus/global 提供
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发时把 /api 代理到本地后端；生产环境由 nginx 代理（同源，无 CORS）
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
