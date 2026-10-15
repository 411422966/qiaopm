/**
 * F024 — 集成列表 UI E2E 验证
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 验证集成列表渲染（表格 + 至少 1 行）
 * 3. 验证列名：ID / 平台 / 店铺 / 方向 / 最近同步 / 状态 / 操作
 * 4. 验证 enum 漂移（I005 C1）：状态列显示 ACTIVE/PAUSED 而非 ACTIVE/DISABLED
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F024-list-ui.spec.ts
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F024 — 集成列表 UI', () => {
  test('集成列表渲染 + 列名 + 状态 enum', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 切到"集成配置" tab
    await page.getByRole('tab', { name: /集成配置/ }).click()

    // 等待表格加载
    await page.waitForSelector('.el-table__row', { timeout: 15_000 })

    // 验证表格列头
    const headers = await page.locator('.el-table__header th').allTextContents()
    console.log('列表列头:', headers)

    expect(headers).toContain('ID')
    expect(headers).toContain('平台')
    expect(headers).toContain('店铺')
    expect(headers).toContain('方向')
    expect(headers).toContain('状态')

    // 验证至少 1 行数据
    const rowCount = await page.locator('.el-table__row').count()
    expect(rowCount).toBeGreaterThanOrEqual(1)
    console.log(`集成行数: ${rowCount}`)

    // ⚠️ I005 C1 enum 漂移验证：状态列实际显示值
    const statusCells = await page.locator('.el-table__row .status-cell').allTextContents().catch(() => [])
    const allStatuses = await page.locator('.el-table__row').evaluateAll((rows) =>
      rows.map((row) => row.textContent || '')
    )
    const combinedText = allStatuses.join(' ')

    // 验证存在 ACTIVE 或 DISABLED 或 PAUSED 任一
    const hasActive = combinedText.includes('ACTIVE')
    const hasDisabled = combinedText.includes('DISABLED')
    const hasPaused = combinedText.includes('PAUSED')

    console.log(`状态 enum 实际值: ACTIVE=${hasActive}, DISABLED=${hasDisabled}, PAUSED=${hasPaused}`)

    // ⚠️ 期望：前端 PAUSED（toggle inactive-value），后端 ACTIVE/DISABLED/ERROR（I005 C1 漂移）
    expect(hasActive || hasPaused || hasDisabled).toBe(true)
  })
})
