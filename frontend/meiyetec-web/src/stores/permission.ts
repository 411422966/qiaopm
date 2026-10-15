import { defineStore } from 'pinia'
import { rbacApi, type MenuNode, type RbacSession } from '@/api/rbac'

interface PermissionState {
  session: RbacSession | null
  permissions: string[]
  menus: MenuNode[]
  loaded: boolean
  loading: boolean
  _refreshPromise: Promise<void> | null
}

const CACHE_KEY = 'rbac.session.v1'

function loadCache(): RbacSession | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function saveCache(s: RbacSession | null) {
  if (s) localStorage.setItem(CACHE_KEY, JSON.stringify(s))
  else localStorage.removeItem(CACHE_KEY)
}

// 解决 vue-tsc 严格模式下 defineStore<any> 无法推断 store state 属性问题
// 使用显式类型注解，确保 this 能访问 state 属性
type PermissionStore = {
  session: RbacSession | null
  permissions: string[]
  menus: MenuNode[]
  loaded: boolean
  loading: boolean
  _refreshPromise: Promise<void> | null
  hasPermission: (code: string) => boolean
  hasAny: (codes: string[]) => boolean
  hasAll: (codes: string[]) => boolean
  setFromLogin: (loginData: any) => void
  refresh: (force?: boolean) => Promise<void>
  clear: () => void
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => {
    const cached = loadCache()
    return {
      session: cached,
      // 从 cache 同步恢复 permissions，否则首次进入受守卫页面时 hasPermission 会返回 false，
      // 守卫会 await refresh()，导致组件 import 与 onMounted 在权限判定前抢先触发
      permissions: cached?.permissions || [],
      menus: cached?.menus || [],
      loaded: !!cached,
      loading: false,
      _refreshPromise: null
    }
  },
  getters: {
    hasPermission: (state) => (code: string) => {
      if (!code) return true
      return state.permissions.includes(code) || state.permissions.includes('*')
    },
    hasAny: (state) => (codes: string[]) => codes.some((c) => state.permissions.includes(c)),
    hasAll: (state) => (codes: string[]) => codes.every((c) => state.permissions.includes(c))
  },
  actions: {
    /**
     * P0-4 关键：登录响应直接灌入，跳过额外 /auth/me/permissions 请求。
     * 登录成功后由 login.vue 调用此方法。
     */
    setFromLogin(loginData: any) {
      const session: RbacSession = {
        userId: loginData.id,
        username: loginData.username,
        tenantId: loginData.tenantId || null,
        dataScope: loginData.dataScope || 'STORE',
        storeIds: loginData.storeIds || [],
        roles: loginData.roles || [],
        permissions: loginData.permissions || [],
        menus: loginData.menus || []
      }
      this.session = session
      this.permissions = session.permissions
      this.menus = session.menus
      this.loaded = true
      saveCache(session)
    },

    async refresh(force = false) {
      // 已有并发请求进行中：复用同一个 Promise，等它完成即可
      if (this.loading && this._refreshPromise) {
        await this._refreshPromise
        return
      }
      if (this.loaded && !force) return
      const p = this._doRefresh(force)
      this._refreshPromise = p
      try {
        await p
      } finally {
        this._refreshPromise = null
      }
    },
    async _doRefresh(force = false) {
      this.loading = true
      try {
        const res: any = await rbacApi.permissions()
        const data: RbacSession = res.data
        this.session = data
        this.permissions = data.permissions || []
        this.menus = data.menus || []
        this.loaded = true
        saveCache(data)
      } catch (e) {
        if (!this.session) {
          const cached = loadCache()
          if (cached) {
            this.session = cached
            this.permissions = cached.permissions || []
            this.menus = cached.menus || []
            this.loaded = true
          }
        }
      } finally {
        this.loading = false
      }
    },
    clear() {
      this.session = null
      this.permissions = []
      this.menus = []
      this.loaded = false
      this._refreshPromise = null
      saveCache(null)
    }
  }
}) as unknown as PermissionStore
