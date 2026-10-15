import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request: AxiosInstance = axios.create({
  // 统一加 /api 前缀，便于 Vite dev server / Nginx 一条代理规则覆盖所有后端调用
  baseURL: '/api',
  timeout: 10000
})

/**
 * AUTH-005：401 自动 refresh 一次。
 * 用"请求头"标记 _authRetried，避免无限重试。
 *
 * 并发收敛：所有 401 共用一个 refresh Promise。refresh 失败 → 清登录态跳登录页。
 */
let _refreshPromise: Promise<string> | null = null
let _onRefreshFail: (() => void) | null = null

export function setOnRefreshFail(fn: () => void) { _onRefreshFail = fn }

async function refreshTokenOnce(oldToken: string): Promise<string> {
  if (_refreshPromise) return _refreshPromise
  _refreshPromise = (async () => {
    // 直接走原生 fetch（不经过 request，否则会被同一个 401 拦截器再卡一次）
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { Authorization: `Bearer ${oldToken}` }
    })
    if (!res.ok) throw new Error(`refresh HTTP ${res.status}`)
    const body = await res.json()
    if (!body || body.code !== 200 || !body.data?.token) {
      throw new Error('refresh: invalid payload')
    }
    const newToken = body.data.token as string
    localStorage.setItem('token', newToken)
    return newToken
  })()
  try {
    return await _refreshPromise
  } finally {
    // 给下一个 401 重新发起 refresh 的机会
    setTimeout(() => { _refreshPromise = null }, 0)
  }
}

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    } else if (!config.url?.includes('/auth/login') && !config.url?.includes('/auth/refresh')) {
      // 未登录且非登录请求：本地短路直接拒绝，避免后端 401
      return Promise.reject({
        __unauthorized: true,
        message: '未登录',
        config
      })
    }
    // 把 traceId 透传，前端可在调试时拿到
    let traceId = sessionStorage.getItem('traceId')
    if (!traceId) {
      traceId = Math.random().toString(36).slice(2)
      sessionStorage.setItem('traceId', traceId)
    }
    config.headers['X-Trace-Id'] = traceId

    // R2.0-B T-B07：如果调用方传了 Idempotency-Key 头，则透传
    const idemKey = (config as any).idempotencyKey
    if (idemKey) {
      config.headers['Idempotency-Key'] = idemKey
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res && res.traceId) {
      sessionStorage.setItem('traceId', res.traceId)
    }
    if (res.code !== 200) {
      // 调用方通过 { silent: true } 选择不弹消息（用于 dashboard 等容忍失败的接口）
      const silent = (response.config as any)?.silent
      if (!silent) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 解包 envelope：让上层直接用 res.data / res.data.data 访问业务字段
    return res
  },
  async (error) => {
    const status = error.response?.status
    const cfg: any = error.config || {}
    const silent = cfg?.silent

    // AUTH-005：401 自动 refresh 一次
    if (status === 401 && !cfg._authRetried && !cfg.url?.includes('/auth/refresh') && !cfg.url?.includes('/auth/login')) {
      const oldToken = localStorage.getItem('token')
      if (oldToken) {
        cfg._authRetried = true
        try {
          await refreshTokenOnce(oldToken)
          // 重发原请求（带新 token）
          const newToken = localStorage.getItem('token')!
          cfg.headers = cfg.headers || {}
          cfg.headers.Authorization = `Bearer ${newToken}`
          return request.request(cfg)
        } catch {
          // refresh 也失败 → 走原 401 踢回登录流程
          if (_onRefreshFail) _onRefreshFail()
        }
      }
    }

    if (status === 401) {
      // 401 强制回登录
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('rbac.session.v1')
      // 用 vue-router replace 跳转，避免 location.href 触发整页加载
      // （整页加载时浏览器会把这次未完成的请求记为 "404/blocked"，对运维排障产生误导）
      if (router.currentRoute.value.path !== '/login') {
        const next = encodeURIComponent(location.pathname + location.search)
        router.replace({ path: '/login', query: { redirect: next } }).catch(() => {})
      }
      return Promise.reject(error)
    }
    if (!silent) {
      // 403 / 500 / 网络错：仅在非静默请求下弹消息
      const msg =
        status === 403
          ? error.response?.data?.message || '权限不足'
          : error.response?.data?.message || error.message || '网络错误'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request