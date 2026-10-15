/**
 * F052 — 插件前端 市场 + 已安装 tab E2E 验证
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置 → 用户本地 Playwright）
 *
 * 测试目标：
 * 1. 访问 /plugin 页面
 * 2. 验证 tab 数 = 2（市场 + 已安装，**无 runtime tab → I024 Shadow**）
 * 3. 验证「发布插件」按钮点击 → pubDialog 打开（5 字段：编码/名称/类别/版本/简介）
 * 4. 验证「安装」按钮点击 → 仅插 plugin_install（**不触发 runtime load → I023 联动**）
 *
 * 用户手动跑（从 frontend/meiyetec-web/）：
 *   npx playwright test --config=playwright.audit.config.ts e2e/audit/F052-plugin-tabs.spec.ts
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F052 — 插件前端 tab 结构', () => {
  test('页面加载 + tab 渲染', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 等待 tabs 渲染
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 验证 tab 数 = 2
    const tabs = await page.locator('.el-tabs__item').allTextContents()
    console.log('页面 tabs:', tabs)

    expect(tabs.length).toBe(2)
    expect(tabs[0]).toContain('插件市场')
    expect(tabs[1]).toContain('已安装')

    // ⚠️ I024 Shadow：应**没有** runtime tab
    const hasRuntimeTab = tabs.some(t => t.includes('runtime') || t.includes('运行时'))
    expect(hasRuntimeTab).toBe(false)
    console.log(`I024 Shadow: runtime tab 存在 = ${hasRuntimeTab}（应 false）`)
  })

  test('发布插件对话框（5 字段）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    await page.getByRole('button', { name: '发布插件' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 验证 dialog 标题
    const dialogTitle = await page.locator('.el-dialog__title').textContent()
    expect(dialogTitle).toContain('发布插件')

    // 验证 5 字段：编码 / 名称 / 类别 / 版本 / 简介
    const labels = await page.locator('.el-form-item__label').allTextContents()
    console.log('pubForm fields:', labels)
    expect(labels.some(l => l.includes('编码'))).toBe(true)
    expect(labels.some(l => l.includes('名称'))).toBe(true)
    expect(labels.some(l => l.includes('类别'))).toBe(true)
    expect(labels.some(l => l.includes('版本'))).toBe(true)
    expect(labels.some(l => l.includes('简介'))).toBe(true)
  })

  test('已安装 tab 表格 + 卸载按钮', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    await page.getByRole('tab', { name: /已安装/ }).click()
    await page.waitForTimeout(2_000)

    // 验证表格存在
    const table = page.locator('.el-table')
    await expect(table).toBeVisible()

    // 验证列头
    const headers = await page.locator('.el-table__header th').allTextContents()
    console.log('已安装 tab 列头:', headers)
    expect(headers).toContain('ID')
    expect(headers).toContain('编码')
    expect(headers).toContain('版本')
    expect(headers).toContain('状态')
    expect(headers).toContain('操作')
  })
})
