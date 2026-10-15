/**
 * F028 — objectMappings UI 缺失 E2E 验证（I005 C4 + I002）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 打开"新建集成"对话框
 * 3. 验证表单**无 objectMappings 输入**（I005 C4）
 * 4. ⚠️ I005 C4 + I002：表单无此字段 → Salesforce 无法配置自定义字段映射
 * 5. 保存后 DB 中 object_mappings 列为 NULL（I002 缺列）
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F028-object-mappings-missing.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F028-object-mappings/e2e-result.md
 */

import { test, expect } from '@playwright/test'
import { TOKEN } from './auth-token'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://127.0.0.1:5173'
const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080'

test.describe('F028 — objectMappings UI 缺失', () => {
  test('新建表单无 objectMappings 字段（I005 C4）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 打开新建对话框
    await page.getByRole('button', { name: /新建集成/ }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5_000 })

    // 列出所有表单 label + placeholder
    const formItems = await page.locator('.el-form-item__label').allTextContents()
    const placeholders = await page.locator('.el-dialog input[placeholder], .el-dialog textarea[placeholder]').evaluateAll((els) =>
      els.map((el) => (el as HTMLInputElement).placeholder || '')
    )

    console.log('表单字段:', formItems)
    console.log('placeholder:', placeholders)

    // ⚠️ I005 C4 验证：表单无 objectMappings 字段
    expect(formItems.some((l) => l.toLowerCase().includes('objectmapping'))).toBe(false)
    expect(formItems.some((l) => l.toLowerCase().includes('字段映射'))).toBe(false)

    // 同时验证 clientId / clientSecret / privateKey / username / loginUrl 均缺失
    const missingFields = ['clientId', 'clientSecret', 'privateKey', 'username', 'loginUrl', 'objectMappings']
    for (const f of missingFields) {
      const found = formItems.some((l) => l.toLowerCase().includes(f.toLowerCase()))
      expect(found).toBe(false)
      console.log(`✅ 表单缺 "${f}"`)
    }
  })

  test('后端 API + DB 验证 object_mappings 持久化为 NULL', async ({ request }) => {
    // 通过 list API 看实际数据是否含 objectMappings 字段（即便 I002 silent 写库）
    const response = await request.get(`${BACKEND_URL}/crm-integration/list`, {
      headers: { Authorization: `Bearer ${TOKEN}` }
    })
    // ✅ 修复 false positive：401 / 500 / data 为空 都会直接 FAIL，不再绕过断言
    expect(response.status()).toBe(200)
    const body = await response.json()
    console.log('list 响应 code:', body.code)
    expect(body.code).toBe(200)
    expect(Array.isArray(body.data)).toBe(true)
    expect(body.data.length).toBeGreaterThan(0)
    const firstRow = body.data[0]
    console.log('第一条 integration 字段:', Object.keys(firstRow))
    // I002 验证：字段存在但为 null（silent 写库） 或 完全缺失
    expect(firstRow.objectMappings === null || firstRow.objectMappings === undefined).toBe(true)
  })
})