/**
 * F026 — 同步日志 tab E2E 验证（I003 契约漂移）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 切到"同步日志" tab
 * 3. 验证日志表渲染（含 integrationId 列 — I003 漂移）
 * 4. ⚠️ I003：后端 SQL 实际按 platform 过滤，前端却按 integrationId 显示 → 漂移
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F026-sync-log-tab.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F026-sync-log-tab/e2e-result.md
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F026 — 同步日志 tab', () => {
  test('切到同步日志 tab + 验证 integrationId 列', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 切到"同步日志" tab
    await page.getByRole('tab', { name: /同步日志/ }).click()

    // 等待日志表加载（可能为空）
    await page.waitForTimeout(2_000)

    // 验证日志表列头存在
    const headers = await page.locator('.el-table__header th').allTextContents()
    console.log('同步日志列头:', headers)

    // ⚠️ I003 契约漂移：前端显示 integrationId 列，但 crm_sync_log 表无此字段
    expect(headers.some((h) => h.toLowerCase().includes('integrationid'))).toBe(true)

    // 应同时显示 platform 列（后端实际按 platform 过滤）
    expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(true)
  })

  test('同步日志 API 响应（I003 漂移实证）', async ({ request }) => {
    // 直调 API 看实际响应结构（后端真实过滤维度 vs 前端展示维度）
    const response = await request.get('http://localhost:8080/crm-integration/sync-log/list')
    expect(response.status()).toBe(200)

    const body = await response.json()
    console.log('sync-log/list 响应:', JSON.stringify(body, null, 2).slice(0, 500))

    // ⚠️ I003：响应中**没有** integrationId 字段（crm_sync_log 表无此列）
    // 但前端表格却显示此列 → 渲染时值为 undefined
    if (body.data && body.data.length > 0) {
      const firstRow = body.data[0]
      console.log('第一条日志字段:', Object.keys(firstRow))
      expect(firstRow).not.toHaveProperty('integrationId')
    }
  })
})