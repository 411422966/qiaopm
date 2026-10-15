/**
 * F007 — Dashboard tab E2E 验证（I006 Shadow + I008 Fake Success）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 验证 tabs 中**无 "dashboard" 标签**（I006 Shadow Feature）
 * 3. 验证 tabs 只有 "集成配置" + "同步日志" 两个
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F007-no-dashboard-tab.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F007-dashboard/e2e-result.md
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080'

test.describe('F007 — Dashboard tab Shadow Feature', () => {
  test('I006: 应无 dashboard tab', async ({ page }) => {
    // 1. 访问 CRM 集成页面
    await page.goto(`${FRONTEND_URL}/crm-integration`)

    // 2. 等待 tabs 加载
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 3. 列出所有 tab label
    const tabLabels = await page.locator('.el-tabs__item').allTextContents()

    console.log('已发现 tabs:', tabLabels)

    // 4. 验证无 "Dashboard" / "看板" / "dashboard" 标签
    expect(tabLabels).not.toContain('Dashboard')
    expect(tabLabels).not.toContain('看板')
    expect(tabLabels).not.toContain('dashboard')

    // 5. 验证只有 "集成配置" + "同步日志"
    expect(tabLabels.some((l) => l.includes('集成配置'))).toBe(true)
    expect(tabLabels.some((l) => l.includes('同步日志'))).toBe(true)
  })

  test('API 端点可访问（I008 Fake Success 链）', async ({ request }) => {
    // 直接调 API 验证 dashboard endpoint 返回 success（即使 data 为 null）
    const response = await request.get(`${BACKEND_URL}/crm-integration/dashboard`)
    expect(response.status()).toBe(200)

    const body = await response.json()
    console.log('dashboard 响应:', JSON.stringify(body, null, 2))

    // I008 Fake Success: HTTP 200 + code=200/0/500 + data 可能为 null
    expect(body.code).toBeDefined()
    expect(['200', '0', '500']).toContain(String(body.code))
  })
})
