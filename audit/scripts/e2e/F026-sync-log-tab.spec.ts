/**
 * F026 — 同步日志 tab E2E 验证（I003 + I016 已修复）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 切到"同步日志" tab
 * 3. 验证日志表渲染（含 platform 列 — I016 已修复）
 * 4. ✅ I003：前端列已对齐真实字段（platform / createdAt），3 个恒空列（integrationId/syncAt/remark）已替换或删除
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F026-sync-log-tab.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F026-sync-log-tab/e2e-result.md
 */

import { test, expect } from '@playwright/test'
import { TOKEN } from './auth-token'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://127.0.0.1:5173'

test.describe('F026 — 同步日志 tab', () => {
  test('切到同步日志 tab + 验证 platform 列', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 切到"同步日志" tab
    await page.getByRole('tab', { name: /同步日志/ }).click()

    // 等待日志表加载（可能为空）
    await page.waitForTimeout(2_000)

    // 验证日志表列头存在
    const headers = await page.locator('.el-table__header th').allTextContents()
    console.log('同步日志列头:', headers)

    // ✅ I016 已修复：列头 label 是中文「平台」
    expect(headers.some((h) => h.includes('平台'))).toBe(true)

    // ✅ I016 正向断言：后端按 platform 过滤，前端已渲染 platform 列
    expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(true)
  })

  test('同步日志 API 响应（契约对齐验证）', async ({ request }) => {
    // 直调 API 看实际响应结构，确认与前端列定义一致
    const response = await request.get('http://127.0.0.1:8080/crm-integration/sync-log/list', {
      headers: { Authorization: `Bearer ${TOKEN}` }
    })
    expect(response.status()).toBe(200)

    const body = await response.json()
    console.log('sync-log/list 响应:', JSON.stringify(body, null, 2).slice(0, 500))

    // ✅ 修复 false positive：业务码必须 200，code=500 (I002 blocking) / 401 / 403 一律 FAIL
    expect(body.code).toBe(200)
    expect(Array.isArray(body.data)).toBe(true)
    expect(body.data.length).toBeGreaterThan(0)

    // ✅ I003 已修复：响应有 platform / createdAt 字段；前端列已对齐
    const firstRow = body.data[0]
    console.log('第一条日志字段:', Object.keys(firstRow))
    expect(firstRow).toHaveProperty('platform')
    expect(firstRow).toHaveProperty('createdAt')
  })
})