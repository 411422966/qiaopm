import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'

/**
 * SPA Fallback 规则：
 * - 以 /api 开头的请求 → 转发到后端
 * - 其余 SPA 前端路由（/crm/care/follow-task、/spa-scene/...）→ 返回 index.html
 *
 * 之前所有代理（/crm、/wallet、/spa-scene...）都做了"前缀代理"，导致
 * 浏览器刷新 http://localhost:5173/crm/care/follow-task 时，Vite 把整个页面
 * 请求也代理给后端；后端没有该 controller → 401 / 404。
 * 修复：去掉宽泛前缀代理，仅保留 /api 这一条；其它业务接口由前端 axios
 * 直接拼接 /api 前缀调用。
 */
const apiProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
  // 前端 baseURL='/api'，后端真实路径不带 /api：代理时去掉前缀
  rewrite: (path: string) => path.replace(/^\/api/, '')
}

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
      eslintrc: { enabled: false }
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    cssMinify: false
  },
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxy
    }
  }
})
