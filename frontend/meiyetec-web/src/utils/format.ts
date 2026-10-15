/**
 * 前端通用格式化工具（R2.0-E T-E03 Vitest 覆盖）
 */

/** 金额格式化：保留 2 位小数 + 千分位 + ¥ */
export function formatMoney(value: number | string | null | undefined, withSymbol = true): string {
  if (value === null || value === undefined || value === '') return withSymbol ? '¥0.00' : '0.00'
  const num = Number(value)
  if (Number.isNaN(num)) return withSymbol ? '¥0.00' : '0.00'
  const fixed = num.toFixed(2)
  const [int, dec] = fixed.split('.')
  const intWithComma = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return withSymbol ? `¥${intWithComma}.${dec}` : `${intWithComma}.${dec}`
}

/** 库存预警等级计算 */
export type AlertLevel = 'OUT' | 'LOW' | 'OVER' | 'EXPIRING' | 'EXPIRED' | 'OK'

export function computeAlertLevel(
  qty: number,
  minStock: number,
  maxStock: number,
  expiryDate?: string | null
): AlertLevel {
  if (expiryDate) {
    const d = new Date(expiryDate)
    const now = new Date()
    if (d.getTime() < now.getTime()) return 'EXPIRED'
    const daysToExpire = (d.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)
    if (daysToExpire <= 30) return 'EXPIRING'
  }
  if (qty <= 0) return 'OUT'
  if (qty < minStock) return 'LOW'
  if (qty > maxStock) return 'OVER'
  return 'OK'
}

/** 紧急度计算（R2.0-D T-D04 补货建议） */
export type Urgency = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW'

export function computeUrgency(stock: number, avgDaily: number, minStock: number, safetyTarget: number, need: number): Urgency {
  if (need <= 0) return 'LOW'
  if (stock <= 0 || (avgDaily > 0 && stock < avgDaily * 3)) return 'URGENT'
  if (stock < minStock) return 'HIGH'
  if (stock < safetyTarget) return 'MEDIUM'
  return 'LOW'
}

/** ISO 日期截断到 yyyy-MM-dd */
export function isoDate(d: Date | string | null | undefined): string {
  if (!d) return ''
  const date = typeof d === 'string' ? new Date(d) : d
  if (Number.isNaN(date.getTime())) return ''
  return date.toISOString().slice(0, 10)
}