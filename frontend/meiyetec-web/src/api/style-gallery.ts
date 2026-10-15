import request from './request'

/**
 * 美甲款式图库（H5 消费者浏览页）—— 后端 StyleGalleryService @ /style/*
 *
 * 路由说明：
 * - A 套 /style/library/* （StyleLibraryController.*.disabled）已废弃
 * - B 套 /style/*  是当前活跃实现（POST /style + GET /style/list + /{id}/view/like/order）
 *
 * 响应包装：后端统一走 Result<T>，axios 拦截器解出 data 后调用方拿到的就是 List/Object。
 * 已自动从 /style/library/* 改为 /style/*，并修正 quickOrder 的 body 传参。
 */
export const styleGalleryApi = {
  /** 列表（按 category / tag / sort 过滤） sort: HOT | NEW | VIEW */
  list: (params?: { category?: string; tag?: string; sort?: 'HOT' | 'NEW' | 'VIEW' }) =>
    request.get<any[]>('/style/list', { params }),

  /** 详情（自带 view_count 自增） */
  get: (id: number | string) =>
    request.get<any>(`/style/${id}`),

  /** 新建款式（admin 工具，普通用户用 initDefault） */
  create: (data: any) =>
    request.post<any>('/style', data),

  /** 浏览日志（H5 端：埋点，留痕） */
  logView: (id: number | string, body?: { memberId?: number; openId?: string; source?: string }) =>
    request.post<void>(`/style/${id}/view`, body || { source: 'H5' }),

  /** 点赞（自增 like_count） */
  like: (id: number | string) =>
    request.post<void>(`/style/${id}/like`),

  /**
   * 快速下单（后端返回 orderUrl，前端跳转 /booking/create?styleId=...）。
   * body 走 JSON（不是 query）—— 修正自旧实现的 params 错误。
   */
  quickOrder: (id: number | string, body: { memberId?: number; remark?: string } = {}) =>
    request.post<{ styleId: number; styleName: string; price: number; estimatedMinutes: number; orderUrl: string }>(
      `/style/${id}/order`,
      body
    ),

  /**
   * P2-12 IND-12：完整快速下单（款式 + 预约时间 + 美甲师 + 会员 + 耗材解析）
   *
   * 请求 body：
   * {
   *   styleId: number
   *   memberId?: number
   *   technicianId?: number
   *   storeId?: number
   *   bookingDate?: "YYYY-MM-DD"
   *   startTime?: "HH:mm"
   *   remark?: string
   * }
   *
   * 返回：
   * {
   *   styleId, styleName, styleCode, basePrice, vipPrice, durationMinutes,
   *   technicianId?, technicianName?, technicianLevel?,
   *   bookingId?, bookingNo?, bookingDate?, startTime?, endTime?, bookingStatus?,
   *   bookingWarning?: string
   *   materials: [{ productId, productName, unit, stockQty, isSubstitute, substituteReason, ... }]
   *   materialWarning?: string
   *   message: string
   * }
   */
  quickOrderFull: (body: {
    styleId: number
    memberId?: number
    technicianId?: number
    storeId?: number
    bookingDate?: string
    startTime?: string
    remark?: string
  }) => request.post<Record<string, any>>('/style/quick-order', body),

  /** 一键初始化 7 条官方款式（演示数据） */
  initDefault: () =>
    request.post<{ created: number }>('/style/init-default')
}

/**
 * 类别枚举（与后端 initDefault 的样例数据对齐）
 */
export const STYLE_CATEGORIES: { code: string; label: string; emoji: string; color: string }[] = [
  { code: '',         label: '全部',   emoji: '✨', color: '#909399' },
  { code: 'FRENCH',   label: '法式',   emoji: '💅', color: '#f3a4b6' },
  { code: 'GLITTER',  label: '闪粉',   emoji: '🌟', color: '#f0c674' },
  { code: 'MATTE',    label: '哑光',   emoji: '🪨', color: '#7a8b6e' },
  { code: 'CHROME',   label: '镜面',   emoji: '🪞', color: '#9aa9c0' },
  { code: '3D',       label: '3D',    emoji: '🐱', color: '#e3a6c8' },
  { code: 'ART',      label: '艺术',   emoji: '🎨', color: '#c89dd6' }
]

/**
 * 把后端 StyleGallery.imagesJson / coverImage 解析为 URL 数组
 * —— 当前数据库 demo 数据大多为空，UI 自动用色块占位
 */
export function resolveImageList(item: any): string[] {
  if (!item) return []
  if (item.imagesJson) {
    try {
      const arr = JSON.parse(item.imagesJson)
      if (Array.isArray(arr) && arr.length) return arr
    } catch { /* fall through */ }
  }
  if (item.coverImage) return [item.coverImage]
  return []
}

/**
 * Tag 字符串（"HOT,CLASSIC"） → chip 列表
 */
export function parseTags(tagString?: string | null): { code: string; label: string; tone: string }[] {
  if (!tagString) return []
  return tagString.split(',').filter(Boolean).map(code => {
    const upper = code.toUpperCase().trim()
    if (upper === 'HOT') return { code: upper, label: '🔥 热门', tone: 'danger' }
    if (upper === 'NEW') return { code: upper, label: '🆕 新品', tone: 'success' }
    if (upper === 'RECOMMEND') return { code: upper, label: '⭐ 推荐', tone: 'warning' }
    if (upper === 'SEASONAL') return { code: upper, label: '🌸 当季', tone: 'warning' }
    if (upper === 'CLASSIC') return { code: upper, label: '经典', tone: 'info' }
    if (upper === 'SPRING') return { code: upper, label: '春季', tone: 'success' }
    return { code: upper, label: upper, tone: 'info' }
  })
}

/**
 * 难度（1~5）转可视化星星
 */
export function renderDifficulty(level?: number | null): string {
  if (!level || level < 1) return ''
  return '★'.repeat(Math.min(level, 5)) + '☆'.repeat(Math.max(0, 5 - level))
}

/**
 * 根据 category 字段挑封面占位色（无图时 H5 也能有好看的卡片）
 */
export function coverBg(category?: string | null): string {
  const map: Record<string, string> = {
    FRENCH:  'linear-gradient(135deg,#fce4ec,#fff8f9)',
    GLITTER: 'linear-gradient(135deg,#fff3c4,#fff9d8)',
    MATTE:   'linear-gradient(135deg,#a8b89c,#cfd9c5)',
    CHROME:  'linear-gradient(135deg,#d6deea,#eef2f7)',
    '3D':    'linear-gradient(135deg,#ffd6e7,#ffe9f1)',
    ART:     'linear-gradient(135deg,#e8c5e0,#f3dcef)'
  }
  return map[(category || '').toUpperCase()] || 'linear-gradient(135deg,#eef2f7,#f6f8fb)'
}

/**
 * 类别 emoji（无图时的视觉锚点）
 */
export function categoryEmoji(category?: string | null): string {
  return STYLE_CATEGORIES.find(c => c.code === (category || '').toUpperCase())?.emoji || '💖'
}

// ====================================================================
// ⭐ 扩展 1：风格维度（与类别正交 — 风格描述"调性"，类别描述"工艺"）
// ====================================================================

/**
 * 风格维度（STYLE_DIMENSIONS）。
 * 设计原则：与 category 正交。
 * - category：工艺 / 手法（FRENCH 法式、GLITTER 闪粉、3D 立体等）
 * - style：   调性 / 氛围（优雅 / 甜美 / 酷感 / 简约 / 复古 / 可爱）
 *
 * 由于后端实体无 style 字段，前端做"标签 → 风格"启发式映射：
 * - tags 中包含 "GLITTER / 闪粉" → 风格 = 甜美
 * - tags 中包含 "MATTE / 哑光"   → 风格 = 酷感
 * - tags 中包含 "CHROME / 镜面"   → 风格 = 未来感
 * - tags 中包含 "3D / 立体"      → 风格 = 可爱
 * - tags 中包含 "ART / 艺术"     → 风格 = 复古
 * - tags 中包含 "FRENCH / 法式"  → 风格 = 优雅
 * - 默认         → 风格 = 简约
 */
export const STYLE_DIMENSIONS: { code: string; label: string; emoji: string }[] = [
  { code: '',           label: '全部风格', emoji: '🎭' },
  { code: 'ELEGANT',    label: '优雅',     emoji: '🕊️' },
  { code: 'SWEET',      label: '甜美',     emoji: '🍓' },
  { code: 'COOL',       label: '酷感',     emoji: '🖤' },
  { code: 'MINIMAL',    label: '简约',     emoji: '◻️' },
  { code: 'RETRO',      label: '复古',     emoji: '🕰️' },
  { code: 'CUTE',       label: '可爱',     emoji: '🐰' },
  { code: 'FUTURE',     label: '未来感',   emoji: '🚀' }
]

/**
 * 从款式对象推断所属"风格"。
 * 命中规则：tags / category 字段含特定关键词 → 对应风格；否则 MINIMAL。
 */
export function inferStyle(item: any): string {
  if (!item) return 'MINIMAL'
  const tags   = String(item.tags || '').toUpperCase()
  const cat    = String(item.category || '').toUpperCase()
  const desc   = String(item.description || '').toLowerCase()

  const hit = (kws: string[]) =>
    kws.some(k => tags.includes(k) || cat.includes(k) || desc.includes(k))

  if (hit(['GLITTER', '闪粉', 'SPARKLE', 'RIMINI'])) return 'SWEET'
  if (hit(['MATTE', '哑光', 'VELVET']))             return 'COOL'
  if (hit(['CHROME', '镜面', 'METAL', 'HOLOGRAM']))  return 'FUTURE'
  if (hit(['3D', '立体', 'CHARACTER', '卡通', 'CUTE'])) return 'CUTE'
  if (hit(['ART', '艺术', 'VINTAGE', 'RETRO', 'Y2K']))  return 'RETRO'
  if (hit(['FRENCH', '法式', 'CLASSIC']))               return 'ELEGANT'
  return 'MINIMAL'
}

// ====================================================================
// ⭐ 扩展 2：分享链接
// ====================================================================

/**
 * 生成款式分享链接。
 * 形式：`${origin}/style-gallery?share=${id}` —
 *  - origin 取自 window.location.origin
 *  - 兼容开发/生产（无需配置 baseURL）
 *  - H5 嵌入小程序 / 短信时可直接作为入参使用
 */
export function buildShareUrl(id: number | string): string {
  if (typeof window === 'undefined') return `/style-gallery?share=${id}`
  return `${window.location.origin}/style-gallery?share=${id}`
}

/**
 * 公共二维码 API（不引入新依赖）。
 * 文档：https://goqr.me/api/doc/create-qr-code/
 */
export function buildQrCodeUrl(text: string, size = 240): string {
  const params = new URLSearchParams({
    data: text,
    size: `${size}x${size}`,
    format: 'png',
    margin: '8',
    ecc: 'M'
  })
  return `https://api.qrserver.com/v1/create-qr-code/?${params.toString()}`
}

// ====================================================================
// ⭐ 扩展 3：登录态收藏（localStorage 持久化 · per-user）
// ====================================================================

/** 从 localStorage 拿当前 user id（与 Layout.vue 一致）；拿不到返回 'guest' */
export function getCurrentUserId(): string | number {
  if (typeof window === 'undefined') return 'guest'
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return 'guest'
    const u = JSON.parse(raw)
    return u.id ?? u.username ?? 'guest'
  } catch {
    return 'guest'
  }
}

function favKey(userId: string | number): string {
  return `style-favorites:${userId}`
}

/** 读取当前用户已收藏的款式 ID 集合 */
export function getFavorites(userId?: string | number): Set<number> {
  const uid = userId ?? getCurrentUserId()
  try {
    const raw = localStorage.getItem(favKey(uid))
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    return new Set(Array.isArray(arr) ? arr : [])
  } catch {
    return new Set()
  }
}

/** 是否已收藏 */
export function isFavorited(styleId: number, userId?: string | number): boolean {
  return getFavorites(userId).has(styleId)
}

/** 切换收藏态；返回新的 boolean */
export function toggleFavorite(styleId: number, userId?: string | number): boolean {
  const uid = userId ?? getCurrentUserId()
  const set = getFavorites(uid)
  let now: boolean
  if (set.has(styleId)) {
    set.delete(styleId)
    now = false
  } else {
    set.add(styleId)
    now = true
  }
  try {
    localStorage.setItem(favKey(uid), JSON.stringify([...set]))
  } catch {
    // 配额满 / 隐私模式 — 静默忽略
  }
  return now
}

// ====================================================================
// ⭐ 扩展 4：风格分布统计（饼图数据源）
// ====================================================================

/**
 * 按 category（也可换 style）统计列表数据，返回 ECharts 饼图所需结构。
 * @param items   当前列表
 * @param groupBy 'category' | 'style'  （默认 category）
 */
export function getDistribution(items: any[], groupBy: 'category' | 'style' = 'category'):
    { name: string; value: number; code: string }[] {
  if (!Array.isArray(items) || !items.length) return []
  const counter = new Map<string, number>()
  for (const it of items) {
    const code = groupBy === 'style'
      ? inferStyle(it)
      : String(it?.category || '').toUpperCase() || 'OTHER'
    counter.set(code, (counter.get(code) || 0) + 1)
  }
  const labelMap = groupBy === 'style'
    ? Object.fromEntries(STYLE_DIMENSIONS.map(d => [d.code, d.label]))
    : Object.fromEntries(STYLE_CATEGORIES.map(c => [c.code, c.label]))
  return [...counter.entries()]
    .map(([code, value]) => ({
      code,
      name: labelMap[code] || code,
      value
    }))
    .sort((a, b) => b.value - a.value)
}
