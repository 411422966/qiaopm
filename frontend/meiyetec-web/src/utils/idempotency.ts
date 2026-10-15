/**
 * R2.0-B T-B07：幂等键 + 防重复提交工具
 *
 * <p>前端三种防重策略：</p>
 * <ol>
 *   <li>幂等键：每次发起"写"操作前生成 UUID，作为 Idempotency-Key 头传给后端；</li>
 *   <li>按钮锁定：500ms 内按钮 disabled，避免用户连续点击；</li>
 *   <li>页面级 Loading：路由切换 + 防回退 + 警示。</li>
 * </ol>
 */

export interface IdempotentP {
  /** 幂等键；不传则内部生成 */
  key?: string
  /** 锁定时长（毫秒），默认 500 */
  lockMs?: number
}

/**
 * 生成幂等键（UUID 去横杠）
 */
export function newIdempotencyKey(): string {
  // 用 crypto.randomUUID 优先，回退到 Math.random 拼接
  if (typeof crypto !== 'undefined' && (crypto as any).randomUUID) {
    return (crypto as any).randomUUID().replace(/-/g, '')
  }
  // RFC4122 v4 fallback
  const hex = '0123456789abcdef'
  let s = ''
  for (let i = 0; i < 32; i++) {
    s += hex[Math.floor(Math.random() * 16)]
    if (i === 7 || i === 11 || i === 15 || i === 19) s += '-'
  }
  return s.replace(/-/g, '')
}

const BUTTON_LOCK_CACHE = new Set<string>()

/**
 * 简易按钮级去重：相同 key 在 lockMs 内的请求会被标记为 lock-breached。
 * 用于 SubmitButton.vue 内按钮点击事件。
 */
export function tryLock(key: string, lockMs = 500): boolean {
  if (BUTTON_LOCK_CACHE.has(key)) return false
  BUTTON_LOCK_CACHE.add(key)
  setTimeout(() => BUTTON_LOCK_CACHE.delete(key), lockMs)
  return true
}

/**
 * 组合两个工具：返回 Idempotent-Promise
 */
export function idempotentRequest<T>(opts: {
  name: string         // 业务名，用于锁定
  key?: string          // 幂等键
  lockMs?: number
  request: (p: { key: string }) => Promise<T>
}): Promise<T> {
  const idemKey = opts.key || newIdempotencyKey()
  if (!tryLock(opts.name, opts.lockMs ?? 500)) {
    return Promise.reject(new Error('DUPLICATE_REQUEST'))
  }
  return opts.request({ key: idemKey })
}
