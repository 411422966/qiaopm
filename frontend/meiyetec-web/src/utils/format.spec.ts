/**
 * 通用格式化工具的单元测试（R2.0-E T-E03）
 *
 * 覆盖：
 * - formatMoney: 千分位 / 负数 / 0 / null / NaN / 字符串
 * - computeAlertLevel: 6 种状态
 * - computeUrgency: 4 档紧急度
 * - isoDate: ISO 截断
 */
import { describe, it, expect } from 'vitest'
import { formatMoney, computeAlertLevel, computeUrgency, isoDate } from './format'

describe('formatMoney', () => {
  it('格式化整数带千分位和符号', () => {
    expect(formatMoney(1234)).toBe('¥1,234.00')
  })

  it('格式化小数', () => {
    expect(formatMoney(1234.5)).toBe('¥1,234.50')
  })

  it('格式化大数 7 位', () => {
    expect(formatMoney(1234567)).toBe('¥1,234,567.00')
  })

  it('无符号', () => {
    expect(formatMoney(99.9, false)).toBe('99.90')
  })

  it('0 处理', () => {
    expect(formatMoney(0)).toBe('¥0.00')
  })

  it('null 处理', () => {
    expect(formatMoney(null)).toBe('¥0.00')
  })

  it('undefined 处理', () => {
    expect(formatMoney(undefined)).toBe('¥0.00')
  })

  it('字符串数字处理', () => {
    expect(formatMoney('500.5')).toBe('¥500.50')
  })

  it('非法字符串处理', () => {
    expect(formatMoney('abc')).toBe('¥0.00')
  })

  it('负数处理', () => {
    expect(formatMoney(-100)).toBe('¥-100.00')
  })
})

describe('computeAlertLevel', () => {
  it('qty=0 → OUT', () => {
    expect(computeAlertLevel(0, 10, 100)).toBe('OUT')
  })

  it('qty < minStock → LOW', () => {
    expect(computeAlertLevel(5, 10, 100)).toBe('LOW')
  })

  it('qty > maxStock → OVER', () => {
    expect(computeAlertLevel(150, 10, 100)).toBe('OVER')
  })

  it('qty 在区间内 → OK', () => {
    expect(computeAlertLevel(50, 10, 100)).toBe('OK')
  })

  it('已过期日期 → EXPIRED 优先', () => {
    expect(computeAlertLevel(50, 10, 100, '2020-01-01')).toBe('EXPIRED')
  })

  it('30 天内到期 → EXPIRING', () => {
    const soon = new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString()
    expect(computeAlertLevel(50, 10, 100, soon)).toBe('EXPIRING')
  })
})

describe('computeUrgency', () => {
  it('need <= 0 → LOW（无需补货）', () => {
    expect(computeUrgency(100, 1, 10, 14, 0)).toBe('LOW')
  })

  it('stock = 0 → URGENT', () => {
    expect(computeUrgency(0, 1, 10, 14, 5)).toBe('URGENT')
  })

  it('avgDaily * 3 > stock → URGENT', () => {
    expect(computeUrgency(2, 1, 10, 14, 5)).toBe('URGENT')
  })

  it('stock < minStock → HIGH', () => {
    expect(computeUrgency(5, 0.1, 10, 14, 5)).toBe('HIGH')
  })

  it('stock < safetyTarget → MEDIUM', () => {
    expect(computeUrgency(8, 0.1, 5, 14, 5)).toBe('MEDIUM')
  })

  it('正常 → LOW', () => {
    expect(computeUrgency(20, 0.1, 10, 14, 0)).toBe('LOW')
  })
})

describe('isoDate', () => {
  it('截断到 yyyy-MM-dd', () => {
    expect(isoDate(new Date('2026-08-12T10:30:00.000Z'))).toBe('2026-08-12')
  })

  it('字符串输入', () => {
    expect(isoDate('2026-08-12T10:30:00.000Z')).toBe('2026-08-12')
  })

  it('null 处理', () => {
    expect(isoDate(null)).toBe('')
  })

  it('undefined 处理', () => {
    expect(isoDate(undefined)).toBe('')
  })

  it('非法字符串处理', () => {
    expect(isoDate('invalid')).toBe('')
  })
})