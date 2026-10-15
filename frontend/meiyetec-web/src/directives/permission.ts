import type { App, Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * v-permission="'member:add'"
 * v-permission="['member:add', 'member:edit']"
 *
 * 命中规则：登录用户拥有任一权限码即继续展示；缺失则移除 DOM 元素。
 * 配合 pinia 异步加载：permission store 在 App.vue 启动时会拉取一次，
 * stale 情况可在 mutation 后调用 usePermissionStore().refresh(true)。
 */
const Permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding: DirectiveBinding<string | string[]>) {
    apply(el, binding)
  },
  updated(el, binding: DirectiveBinding<string | string[]>) {
    apply(el, binding)
  }
}

function apply(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const value = binding.value
  if (!value) return
  const codes = Array.isArray(value) ? value : [value]
  const store = usePermissionStore()
  const ok = codes.some((c) => store.hasPermission(c))
  if (!ok) {
    el.parentNode?.removeChild(el)
  }
}

export default {
  install(app: App) {
    app.directive('permission', Permission)
  }
}
