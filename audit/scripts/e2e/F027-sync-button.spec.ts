/**
 * F027 — sync 按钮 force 未传 E2E 验证（I005 C2）
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置）
 *
 * 测试目标：
 * 1. 访问 /crm-integration 页面
 * 2. 点击列表行的"同步"按钮
 * 3. 验证网络请求 URL（应不包含 ?force=1）
 * 4. ⚠️ I005 C2：force 未传 → 窗口外返回 skipped=true 但 UI 无提示
 *
 * 用户手动跑：
 *   cd frontend/meiyetec-web
 *   npx playwright test ../audit/scripts/e2e/F027-sync-button.spec.ts
 *
 * 结果贴回：
 *   audit/evidence/F027-sync-button/e2e-result.md
 */

import { test, expect } from '@playwright/test'
import { TOKEN } from './auth-token'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://127.0.0.1:5173'

test.describe('F027 — sync 按钮 force 未传', () => {
  test('点击同步按钮 → URL 不带 ?force=1', async ({ page }) => {
    // 监听网络请求
    const syncRequests: { url: string; method: string }[] = []
    page.on('request', (req) => {
      if (req.url().includes('/crm-integration/') && req.url().includes('/sync')) {
        syncRequests.push({ url: req.url(), method: req.method() })
      }
    })

    await page.goto(`${FRONTEND_URL}/crm-integration`)
    await page.waitForSelector('.el-tabs__nav', { timeout: 10_000 })

    // 切到"集成配置" tab
    await page.getByRole('tab', { name: /集成配置/ }).click()

    // 等待表格加载
    await page.waitForSelector('.el-table__row', { timeout: 15_000 })

    // 点击第一行的"同步"按钮
    const syncBtn = page.locator('.el-table__row').first().getByRole('button', { name: /同步/ })
    if ((await syncBtn.count()) === 0) {
      test.skip(true, '无集成数据，跳过')
      return
    }
    await syncBtn.click()

    // 等待请求
    await page.waitForTimeout(3_000)

    // 验证捕获到的 sync 请求
    console.log('捕获到的 sync 请求:', syncRequests)
    expect(syncRequests.length).toBeGreaterThanOrEqual(1)

    const syncReq = syncRequests[0]
    console.log(`sync URL: ${syncReq.url}`)

    // ⚠️ I005 C2 验证：URL 不应包含 ?force=1
    expect(syncReq.url).not.toContain('force=1')
    expect(syncReq.url).not.toContain('force=0')

    // 验证响应（I008 Fake Success 链 — 即使 I002 阻塞也会 200 + code=500）
    // 注：当前环境 I002 阻塞 → 响应 code=500，但 API 调用本身被发出
    expect(syncReq.method).toBe('POST')
  })

  test('API 直调验证 force 未传', async ({ request }) => {
    // 直调 API（不带 force）。按 EXT-24 §0.1：force=null 尊重窗口，窗口内 code=200，窗口外 code=200 + skipped=true。
    // I002 (窗口判断 bug) 必须先修，否则 body.code=500 → 该测试 FAIL。
    const response = await request.post('http://127.0.0.1:8080/crm-integration/2/sync', {
      headers: { Authorization: `Bearer ${TOKEN}` }
    })
    expect(response.status()).toBe(200)

    const body = await response.json()
    console.log('sync 响应（无 force）:', JSON.stringify(body, null, 2))

    // ✅ 修复 false positive：业务码必须 200（I002 修好后 skipped=true 也是 code=200；I002 阻塞 → code=500 直接 FAIL）
    expect(body.code).toBe(200)
  })

  test('API 直调 force=1 验证', async ({ request }) => {
    // 对比：传 force=1 应跨窗口执行（I002 修复后）
    const response = await request.post('http://127.0.0.1:8080/crm-integration/2/sync?force=1', {
      headers: { Authorization: `Bearer ${TOKEN}` }
    })
    expect(response.status()).toBe(200)

    const body = await response.json()
    console.log('sync 响应（force=1）:', JSON.stringify(body, null, 2))

    expect(body.code).toBe(200)
    expect(body.data?.forced).toBe(true)
  })
})