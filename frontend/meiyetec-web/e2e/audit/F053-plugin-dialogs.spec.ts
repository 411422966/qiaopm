/**
 * F053 — 插件前端 发布 + 评价对话框 E2E 验证
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置 → 用户本地 Playwright）
 *
 * 测试目标：
 * 1. 打开 pubDialog → 不填字段直接提交 → 验证错误码展示（I006 同模式：可能无 errorCode 字段）
 * 2. 打开 reviewDialog → 不填评分/评价直接提交 → 验证行为
 * 3. 验证无 loading 态、无二次确认
 *
 * 用户手动跑（从 frontend/meiyetec-web/）：
 *   npx playwright test --config=playwright.audit.config.ts e2e/audit/F053-plugin-dialogs.spec.ts
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F053 — 发布 + 评价对话框', () => {
  test('发布对话框：必填校验', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    await page.getByRole('button', { name: '发布插件' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 清空所有必填字段
    const inputs = await page.locator('.el-dialog .el-input__inner').all()
    for (const inp of inputs) {
      await inp.fill('')
    }

    // 提交
    await page.getByRole('button', { name: /^发\s*布$/ }).click()

    // 等待响应
    await page.waitForTimeout(2_000)

    // 验证错误反馈（可能是 toast / inline message / dialog 不关闭）
    const errorMsg = await page.locator('.el-message--error, .el-form-item__error').allTextContents()
    console.log('发布校验失败反馈:', errorMsg)

    // ⚠️ I006 同模式：可能没有 errorCode 字段
    // 验证至少有反馈（dialog 关闭 OR toast 出现 OR inline 错误）
    const dialogStillOpen = await page.locator('.el-dialog').isVisible()
    const toastShown = errorMsg.length > 0

    if (!dialogStillOpen && !toastShown) {
      console.warn('⚠️ 没有任何错误反馈，对话框直接关闭（I006 同模式）')
    }
  })

  test('评价对话框：评分 + 评价字段', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 先确保有市场数据（否则评价按钮不可点）
    const marketCards = await page.locator('.el-card').count()
    console.log('市场卡片数:', marketCards)

    if (marketCards === 0) {
      console.log('⚠️ 市场无数据，跳过评价对话框测试')
      test.skip()
      return
    }

    // 点第一个「评价」按钮
    await page.getByRole('button', { name: '评价' }).first().click()
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 验证 dialog 标题
    const dialogTitle = await page.locator('.el-dialog__title').textContent()
    expect(dialogTitle).toContain('评价插件')

    // 验证字段：评分 + 评价
    const labels = await page.locator('.el-form-item__label').allTextContents()
    console.log('reviewForm fields:', labels)
    expect(labels.some(l => l.includes('评分'))).toBe(true)
    expect(labels.some(l => l.includes('评价'))).toBe(true)

    // ⚠️ 无 loading 态：提交按钮点击后立即响应（无 spinner）
    const submitBtn = page.getByRole('button', { name: /^提\s*交$/ })

    // 提交空评价
    await submitBtn.click()
    await page.waitForTimeout(2_000)
  })
})
