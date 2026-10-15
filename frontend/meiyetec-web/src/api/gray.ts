/**
 * P3.4 灰度上线：前端 Feature Flag API + 状态管理
 */
import request from './request'

// ====================================================================
// API
// ====================================================================

export const grayApi = {
  /** 列出所有 Feature Flag */
  listFlags: () => request.get('/gray/flag/list'),

  /** 评估某个 flag 对当前用户是否启用 */
  evaluate: (key: string, userId: number) =>
    request.get(`/gray/flag/${key}/evaluate`, { params: { userId } }),

  /** 切换 Flag（启用/禁用/调整灰度比例） */
  toggle: (key: string, params: { enabled?: number; rolloutPercent?: number }) =>
    request.post(`/gray/flag/${key}/toggle`, params),

  /** 创建 Flag */
  createFlag: (data: any) => request.post('/gray/flag', data),

  /** AB Test：用户分桶 */
  assignVariant: (expKey: string, userId: number) =>
    request.get(`/gray/experiment/${expKey}/assign`, { params: { userId } }),

  /** AB Test：实验统计 */
  expStats: (key: string) => request.get('/gray/experiment/stats', { params: { key } }),
}

// ====================================================================
// Feature Flag 缓存（内存 + localStorage）
// ====================================================================

const CACHE_KEY = 'gray_feature_flags'

interface FeatureCache {
  [key: string]: {
    enabled: boolean
    rolloutPercent: number
    evaluatedAt: number
  }
}

export function getCachedFlag(key: string): boolean | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const cache: FeatureCache = JSON.parse(raw)
    const entry = cache[key]
    if (!entry) return null
    // 缓存 5 分钟
    if (Date.now() - entry.evaluatedAt > 5 * 60 * 1000) return null
    return entry.enabled
  } catch {
    return null
  }
}

export function setCachedFlag(key: string, enabled: boolean, rolloutPercent: number) {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    const cache: FeatureCache = raw ? JSON.parse(raw) : {}
    cache[key] = { enabled, rolloutPercent, evaluatedAt: Date.now() }
    localStorage.setItem(CACHE_KEY, JSON.stringify(cache))
  } catch {}
}

export function clearCachedFlag(key?: string) {
  try {
    if (key) {
      const raw = localStorage.getItem(CACHE_KEY)
      if (!raw) return
      const cache: FeatureCache = JSON.parse(raw)
      delete cache[key]
      localStorage.setItem(CACHE_KEY, JSON.stringify(cache))
    } else {
      localStorage.removeItem(CACHE_KEY)
    }
  } catch {}
}

// ====================================================================
// 灰度比例一致性 Hash
// ====================================================================

function stableHash(str: string): number {
  let hash = 5381
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) + hash) + str.charCodeAt(i)
    hash = hash & hash
  }
  return Math.abs(hash)
}

/**
 * 判断某用户是否在灰度百分比内（一致性 Hash，同一用户始终命中）
 */
export function isInRollout(key: string, userId: number, rolloutPercent: number): boolean {
  if (rolloutPercent <= 0) return false
  if (rolloutPercent >= 100) return true
  return (stableHash(`${key}:${userId}`) % 100) < rolloutPercent
}
