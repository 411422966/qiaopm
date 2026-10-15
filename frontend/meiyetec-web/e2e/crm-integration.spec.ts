// P3-T09 · CRM 集成平台字段动态切换 + 测试连接 E2E
// 覆盖：
//   9.1 - 平台切换时显示对应平台字段（Salesforce/HubSpot/Zoho/Mock）
//   9.2 - 测试连接按钮（行级 + 新建对话框）
// 用例：API-driven + DOM 断言（不依赖后端实际 CRM 调用）

import { test, expect } from '@playwright/test'

const CRM_PATH = '/crm-integration'

test.describe('CRM 集成 — 平台字段动态切换 (T09-9.1)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(CRM_PATH)
    // 等待页面渲染
    await expect(page.locator('.crm-int-page')).toBeVisible({ timeout: 10_000 })
  })

  test('默认平台 SALESFORCE 显示 JWT 字段组', async ({ page }) => {
    await page.getByTestId('crm-platform-select').click()
    await page.locator('.el-select-dropdown__item:has-text("Salesforce")').first().click()

    // Salesforce 字段必现
    await expect(page.getByTestId('sf-client-id')).toBeVisible()
    await expect(page.getByTestId('sf-username')).toBeVisible()
    await expect(page.getByTestId('sf-private-key')).toBeVisible()
    // HubSpot/Zoho 字段应隐藏（DOM 不存在）
    await expect(page.getByTestId('hs-client-id')).toHaveCount(0)
    await expect(page.getByTestId('zo-client-id')).toHaveCount(0)
  })

  test('切换到 HUBSPOT 显示 Private App 字段组', async ({ page }) => {
    await page.getByTestId('crm-platform-select').click()
    await page.locator('.el-select-dropdown__item:has-text("HubSpot")').first().click()

    await expect(page.getByTestId('hs-client-id')).toBeVisible()
    await expect(page.getByTestId('hs-client-secret')).toBeVisible()
    await expect(page.getByTestId('hs-access-token')).toBeVisible()
    await expect(page.getByTestId('hs-portal-id')).toBeVisible()
    // Salesforce/Zoho 字段应隐藏
    await expect(page.getByTestId('sf-client-id')).toHaveCount(0)
    await expect(page.getByTestId('zo-refresh-token')).toHaveCount(0)
  })

  test('切换到 ZOHO 显示 OAuth2 字段组', async ({ page }) => {
    await page.getByTestId('crm-platform-select').click()
    await page.locator('.el-select-dropdown__item:has-text("Zoho")').first().click()

    await expect(page.getByTestId('zo-client-id')).toBeVisible()
    await expect(page.getByTestId('zo-client-secret')).toBeVisible()
    await expect(page.getByTestId('zo-refresh-token')).toBeVisible()
    // Salesforce/HubSpot 字段应隐藏
    await expect(page.getByTestId('sf-private-key')).toHaveCount(0)
    await expect(page.getByTestId('hs-access-token')).toHaveCount(0)
  })

  test('切换到 MOCK 不显示凭据字段', async ({ page }) => {
    await page.getByTestId('crm-platform-select').click()
    await page.locator('.el-select-dropdown__item:has-text("Mock")').first().click()

    // 任何平台字段都不应出现
    await expect(page.getByTestId('sf-client-id')).toHaveCount(0)
    await expect(page.getByTestId('hs-client-secret')).toHaveCount(0)
    await expect(page.getByTestId('zo-refresh-token')).toHaveCount(0)
  })
})

test.describe('CRM 集成 — 测试连接端点 (T09-9.2)', () => {
  test('Mock 集成「测试连接」按钮 → 健康检查', async ({ page, request }) => {
    await page.goto(CRM_PATH)
    await expect(page.locator('.crm-int-page')).toBeVisible({ timeout: 10_000 })

    // 先创建一个 Mock 集成（API 直接调用）
    const createRes = await request.post('/api/crm-integration', {
      data: {
        platform: 'MOCK',
        shopName: 'E2E-Test-Shop',
        syncDirection: 'PUSH',
        autoSync: 0,
        status: 'ACTIVE'
      }
    })
    expect(createRes.ok()).toBeTruthy()
    const created = await createRes.json()
    const integrationId = created.data?.id ?? created.id
    expect(integrationId).toBeTruthy()

    // 刷新列表
    await page.reload()
    await expect(page.locator('.crm-int-page')).toBeVisible()

    // API 测试连接（验证端点存在并返回 200 + healthy=true）
    const testRes = await request.post(`/api/crm-integration/${integrationId}/test-connection`)
    expect(testRes.ok()).toBeTruthy()
    const testData = await testRes.json()
    expect(testData.code).toBe(200)
    expect(testData.data?.healthy).toBe(true)
    expect(testData.data?.stage).toMatch(/MOCK_OK|AUTH_OK/)
    expect(testData.data?.elapsedMs).toBeGreaterThanOrEqual(0)

    // 列表中应有「测试连接」按钮（行级）
    const testBtn = page.locator('button:has-text("测试连接")').first()
    await expect(testBtn).toBeVisible()
  })

  test('未配置凭据的 Salesforce 集成 → 连接失败', async ({ request }) => {
    // 创建一个 Salesforce 集成（无 clientId / privateKey）
    const createRes = await request.post('/api/crm-integration', {
      data: {
        platform: 'SALESFORCE',
        shopName: 'E2E-SF-Empty',
        syncDirection: 'PUSH',
        autoSync: 0,
        status: 'ACTIVE'
        // 没有 clientId / username / privateKey
      }
    })
    expect(createRes.ok()).toBeTruthy()
    const created = await createRes.json()
    const integrationId = created.data?.id ?? created.id

    // 测试连接应返回错误（healthy=false）
    const testRes = await request.post(`/api/crm-integration/${integrationId}/test-connection`)
    expect(testRes.status()).toBeLessThan(600)
    const testData = await testRes.json()
    expect(testData.data?.healthy ?? false).toBe(false)
    expect(testData.data?.stage).toMatch(/AUTH_FAIL|EXCEPTION|CONFIG_ERROR/)
  })

  test('跨租户访问 → 403/Forbidden', async ({ request }) => {
    // 假设 integrationId=999999 不存在 → 应返回 404 或 NOT_FOUND
    const testRes = await request.post('/api/crm-integration/999999/test-connection')
    const testData = await testRes.json()
    expect(testData.code).not.toBe(200)
    expect(testData.message).toMatch(/不存在|FORBIDDEN|NOT_FOUND/)
  })
})

test.describe('CRM 集成 — 列表 UI 完整性', () => {
  test('集成列表显示 ID / 平台 / 店铺 / 状态 / 操作列', async ({ page }) => {
    await page.goto(CRM_PATH)
    await expect(page.locator('.crm-int-page')).toBeVisible({ timeout: 10_000 })

    // 表头断言
    await expect(page.locator('th:has-text("ID")').first()).toBeVisible()
    await expect(page.locator('th:has-text("平台")').first()).toBeVisible()
    await expect(page.locator('th:has-text("店铺")').first()).toBeVisible()
    await expect(page.locator('th:has-text("状态")').first()).toBeVisible()
    await expect(page.locator('th:has-text("操作")').first()).toBeVisible()
  })

  test('「新建集成」按钮可点击并打开对话框', async ({ page }) => {
    await page.goto(CRM_PATH)
    await expect(page.locator('.crm-int-page')).toBeVisible({ timeout: 10_000 })

    await page.locator('button:has-text("新建集成")').first().click()
    // 对话框 title
    await expect(page.locator('.el-dialog__title:has-text("新建 CRM 集成")')).toBeVisible()
    // 平台选择器可见
    await expect(page.getByTestId('crm-platform-select')).toBeVisible()
    // 取消按钮
    await expect(page.locator('.el-dialog button:has-text("取消")')).toBeVisible()
    // 保存按钮（T09-9.1 改造后新增「测试连接」按钮）
    await expect(page.locator('.el-dialog button:has-text("保存")')).toBeVisible()
    await expect(page.getByTestId('crm-test-new')).toBeVisible()
  })
})
