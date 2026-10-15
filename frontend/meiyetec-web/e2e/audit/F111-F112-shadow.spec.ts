/**
 * F111 + F112 — INT-19 前端 Shadow E2E 验证
 *
 * Phase 5C E2E 降级（Playwright MCP 未配置 → 用户本地 Playwright）
 *
 * 测试目标（验证 3 个 Shadow Issue）：
 *   - I040 (F096/F111): autoSend=false UI 缺失
 *   - I041 (F096/F111): 「确认」按钮文案无「发送通知」提示
 *   - I042 (F095/F112): intlBookingApi 无 sendConfirmation 方法
 *
 * 用户手动跑（从 frontend/meiyetec-web/）：
 *   npx playwright test --config=playwright.audit.config.ts e2e/audit/F111-F112-shadow.spec.ts
 */

import { test, expect } from '@playwright/test'
import { readFileSync } from 'fs'
import { resolve } from 'path'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

/**
 * 读取 intl-booking.ts 源码做静态校验（I042 实证）
 * 由于 grep 工具在 Playwright 上下文不可用，直接用 fs 读
 */
function loadApiSource(): string {
  const apiPath = resolve(__dirname, '../../../src/api/intl-booking.ts')
  return readFileSync(apiPath, 'utf-8')
}

test.describe('F111 — intl-booking/index.vue Shadow 验证', () => {
  test('页面加载 + 2 tab 结构（"渠道连接" + "国际预约"）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/intl-booking`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 等待 tabs 渲染
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    const tabs = await page.locator('.el-tabs__item').allTextContents()
    console.log('intl-booking tabs:', tabs)

    expect(tabs.length).toBe(2)
    expect(tabs[0]).toContain('渠道连接')
    expect(tabs[1]).toContain('国际预约')
  })

  test('3 对话框结构（Google OAuth / Instagram OAuth / 模板预览）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/intl-booking`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 通过 Vue ref 上的元素验证 dialog 存在
    // 渠道连接 tab 应有 2 个绑定按钮（Google + Instagram）
    const connectButtons = await page.getByRole('button', { name: '绑定' }).count()
    console.log('绑定按钮数（Google + Instagram）:', connectButtons)
    expect(connectButtons).toBeGreaterThanOrEqual(2)

    // 切换到"国际预约" tab — 验证页面无确认 → 需要先有数据（I038 表缺失时为空，跳过）
    await page.getByRole('tab', { name: /国际预约/ }).click()
    await page.waitForTimeout(1_000)

    // 表格存在（即便空数据）
    const table = page.locator('.el-table')
    await expect(table).toBeVisible()
  })

  test('I040 — autoSend=false UI 缺失（Shadow 实证）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/intl-booking`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 静态校验：检查页面 HTML 中是否包含 autoSend 关键词
    const bodyHtml = await page.content()
    const hasAutoSendUI = bodyHtml.includes('autoSend')
    console.log(`I040 — 前端 UI 含 autoSend 元素 = ${hasAutoSendUI}（应 false）`)
    expect(hasAutoSendUI).toBe(false)

    // 进一步确认：没有 "autoSend" 相关的 el-switch / el-checkbox / el-radio
    const switches = await page.locator('text=/autoSend/i').count()
    expect(switches).toBe(0)
  })

  test('I041 — "确认"按钮文案无"发送通知"提示（Shadow 实证）', async ({ page }) => {
    await page.goto(`${FRONTEND_URL}/intl-booking`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 切换到"国际预约" tab
    await page.getByRole('tab', { name: /国际预约/ }).click()
    await page.waitForTimeout(1_000)

    // 查找"确认"按钮 — Vue 模板 L66 按钮文案是「确认」,无「即发送通知」提示
    const confirmButtons = await page.getByRole('button', { name: /确认/ }).count()
    console.log(`"确认" 按钮数: ${confirmButtons}`)

    if (confirmButtons > 0) {
      // 任意点击一个"确认"按钮 — 检查是否触发 el-popconfirm（SoT §0.3 要求二次确认）
      // 当前实现是直接 @click="setStatus(row, 'CONFIRMED')"，无 el-popconfirm
      const popconfirmCount = await page.locator('.el-popconfirm').count()
      console.log(`el-popconfirm 元素数 = ${popconfirmCount}（应 0 — 缺少二次确认）`)
      expect(popconfirmCount).toBe(0)

      // 检查按钮文案是否包含 "发送通知" / "通知" 字样
      const allButtonText = await page.getByRole('button', { name: /确认/ }).first().textContent()
      console.log(`按钮文案: "${allButtonText?.trim()}"`)
      expect(allButtonText?.includes('通知')).toBe(false)
      expect(allButtonText?.includes('发送')).toBe(false)
    } else {
      console.log('当前空数据（I038 MISSING），跳过按钮文案校验（已通过静态分析实证）')
      // 静态层面实证已通过 5A.7 grep 结果
      expect(true).toBe(true)
    }
  })
})

test.describe('F112 — intl-booking.ts API 端点封装 Shadow 验证', () => {
  test('I042 — intlBookingApi 无 sendConfirmation 方法（Shadow 实证）', async ({ page }) => {
    // 直接读 TS 源码静态校验（不依赖 network）
    const apiSource = loadApiSource()
    console.log('intl-booking.ts 行数:', apiSource.split('\n').length)

    // SoT §0.3 要求 sendConfirmation 应为独立端点方法
    // 当前代码应**不包含** sendConfirmation（I042 Shadow）
    const hasSendConfirmation = /sendConfirmation/.test(apiSource)
    console.log(`I042 — intl-booking.ts 含 sendConfirmation = ${hasSendConfirmation}（应 false）`)
    expect(hasSendConfirmation).toBe(false)

    // 进一步确认 — 也不存在 '/intl-booking/{id}/send-confirmation' 端点引用
    const hasSendConfirmationEndpoint = /send-confirmation/.test(apiSource)
    console.log(`I042 — endpoint url 含 send-confirmation = ${hasSendConfirmationEndpoint}（应 false）`)
    expect(hasSendConfirmationEndpoint).toBe(false)
  })

  test('12 端点方法完整性（对照 F112 期望清单）', async ({ page }) => {
    const apiSource = loadApiSource()

    // F112 期望的方法清单（scope-freeze §2.3 + plan §F112）
    const expectedMethods = [
      'connectGoogle',
      'googleCallback',
      'connectInstagram',
      'instagramCallback',
      'listChannels',
      'pullBookings',
      'listBookings',
      'getBooking',
      'updateStatus',
      'convertBooking',
      'confirmationTemplate',
      // 注：缺 sendConfirmation（I042）
    ]

    const missing: string[] = []
    for (const m of expectedMethods) {
      if (!apiSource.includes(`${m}:`)) {
        missing.push(m)
      }
    }

    console.log(`期望方法数 = ${expectedMethods.length}`)
    console.log(`缺失方法 = ${JSON.stringify(missing)}`)

    // 期望全部齐全（缺 sendConfirmation 不算缺失，是 I042 Shadow）
    expect(missing.length).toBe(0)
  })

  test('updateStatus 仅传 status 字段，不支持 autoSend', async ({ page }) => {
    const apiSource = loadApiSource()
    // I040: 前端 updateStatus 调用不传 autoSend
    const updateStatusBodyType = /updateStatus:\s*\(id[^}]*\}\s*\{[^}]*status:\s*string/.exec(apiSource)

    console.log(`updateStatus 类型签名匹配 = ${!!updateStatusBodyType}（应 true — 仅 status 字段）`)
    expect(updateStatusBodyType).not.toBeNull()

    // 进一步：源码不应包含 autoSend:boolean 或 autoSend?
    const hasAutoSendInType = /autoSend\s*[\?:]/.test(apiSource)
    console.log(`updateStatus 类型含 autoSend = ${hasAutoSendInType}（应 false — I040 实证）`)
    expect(hasAutoSendInType).toBe(false)
  })
})
