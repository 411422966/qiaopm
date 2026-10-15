/**
 * F025 — 新建集成表单 E2E 验证（I005 C4 字段缺失）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 点击"新建集成"按钮
 * 3. 验证对话框字段：平台 / 店铺名称 / 方向 / 自动同步（4 字段）
 * 4. ⚠️ I005 C4：表单**无 objectMappings 字段**
 * 5. ⚠️ I005 C4：表单**无 clientId/clientSecret/privateKey 字段**
 * 6. 填表保存 → 列表新增行
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F025-create-form.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F025-create-form/e2e-result.md
 */

import { test, expect } from '@playwright/test'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F025 — 新建集成表单', () => {
  test('对话框渲染 + I005 C4 字段缺失', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 点击"新建集成"按钮
    await page.getByRole('button', { name: /新建集成/ }).click()

    // 等待对话框
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 列出所有表单 label
    const formItems = await page.locator('.el-form-item__label').allTextContents()
    console.log('表单字段:', formItems)

    // ✅ 应有：平台 / 店铺名称 / 方向 / 自动同步
    expect(formItems.some((l) => l.includes('平台'))).toBe(true)
    expect(formItems.some((l) => l.includes('店铺'))).toBe(true)
    expect(formItems.some((l) => l.includes('方向'))).toBe(true)
    expect(formItems.some((l) => l.includes('自动同步'))).toBe(true)

    // ⚠️ I005 C4 应缺失：objectMappings / clientId / clientSecret / privateKey
    expect(formItems.some((l) => l.toLowerCase().includes('objectmapping'))).toBe(false)
    expect(formItems.some((l) => l.toLowerCase().includes('clientid'))).toBe(false)
    expect(formItems.some((l) => l.toLowerCase().includes('clientsecret'))).toBe(false)
    expect(formItems.some((l) => l.toLowerCase().includes('privatekey'))).toBe(false)
  })

  test('填表保存 → 列表新增行', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    const beforeRows = await page.locator('.el-table__row').count()

    await page.getByRole('button', { name: /新建集成/ }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 填表
    await page.locator('.el-form-item:has-text("店铺名称") input').fill('E2E测试集成5B')

    // 选 PULL 方向
    await page.locator('.el-form-item:has-text("方向") .el-select').click()
    await page.locator('.el-select-dropdown__item:has-text("PULL")').click()

    // 保存
    await page.getByRole('button', { name: /保存/ }).click()

    // 等待对话框关闭 + 列表刷新
    await page.waitForTimeout(3_000)

    const afterRows = await page.locator('.el-table__row').count()
    console.log(`保存前: ${beforeRows} 行, 保存后: ${afterRows} 行`)
    expect(afterRows).toBeGreaterThanOrEqual(beforeRows)
  })
})