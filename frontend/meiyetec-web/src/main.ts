import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router'
import App from './App.vue'
import PermissionDirective from './directives/permission'
import './style.css'

const app = createApp(App)

// 容错注册图标：跳过版本升级后不存在的图标，避免 vite build 失败
const KNOWN_MISSING_ICONS = new Set(['Battery', 'Pause', 'Play', 'DiscountCoupon', 'Mail'])
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  if (KNOWN_MISSING_ICONS.has(key)) continue
  try {
    app.component(key, component)
  } catch (e) {
    console.warn(`[icon] register ${key} failed:`, e)
  }
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.use(PermissionDirective)

app.mount('#app')
