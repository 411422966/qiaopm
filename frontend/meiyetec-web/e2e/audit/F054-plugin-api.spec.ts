/**
 * F054 — 插件前端 api/plugin.ts 封装 E2E 验证
 *
 * Phase 5B E2E 降级（Playwright MCP 未配置 → 用户本地 Playwright + 静态分析）
 *
 * 测试目标：
 * 1. 静态验证 plugin.ts 仅 marketplace 8 方法（无 runtime 方法 → I024 Shadow 实证）
 * 2. 运行时验证：通过 UI 操作触发各 API 调用，验证响应
 * 3. 验证 pluginApi 8 方法的路由映射
 *
 * 用户手动跑（从 frontend/meiyetec-web/）：
 *   npx playwright test --config=playwright.audit.config.ts e2e/audit/F054-plugin-api.spec.ts
 */

import { test, expect } from '@playwright/test'
import * as fs from 'fs'
import * as path from 'path'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173'

test.describe('F054 — pluginApi 封装', () => {
  test('静态验证 plugin.ts 方法集', async () => {
    // 读取 plugin.ts 源码
    const pluginTsPath = path.resolve(__dirname, '../../src/api/plugin.ts')
    const content = fs.readFileSync(pluginTsPath, 'utf-8')

    // 提取方法名
    const methodMatch = content.match(/(?:publish|listMarket|updatePlugin|install|uninstall|listInstalls|review|listReviews)\s*:/g)
    const methods = methodMatch ? methodMatch.map(m => m.replace(':', '').trim()) : []
    console.log('pluginApi 方法集:', methods)

    // 验证 8 个 marketplace 方法
    const expectedMethods = ['publish', 'listMarket', 'updatePlugin', 'install', 'uninstall', 'listInstalls', 'review', 'listReviews']
    for (const m of expectedMethods) {
      expect(methods).toContain(m)
    }

    // ⚠️ I024 Shadow 验证：plugin.ts 不应包含 runtime 相关方法
    const runtimeKeywords = ['runtime', '/plugin/runtime', 'loadPlugin', 'enablePlugin', 'disablePlugin', 'unloadPlugin']
    for (const kw of runtimeKeywords) {
      const hasRuntime = content.toLowerCase().includes(kw.toLowerCase())
      console.log(`I024 Shadow 验证: ${kw} = ${hasRuntime}（应 false）`)
      expect(hasRuntime).toBe(false)
    }
  })

  test('运行时：通过 UI 调用 listMarket API', async ({ page }) => {
    const apiCalls: string[] = []

    // 拦截 /plugin/market/list 请求
    page.on('request', (req) => {
      if (req.url().includes('/plugin/market/list')) {
        apiCalls.push(req.url())
      }
    })

    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    // 验证调用了 listMarket
    expect(apiCalls.length).toBeGreaterThanOrEqual(1)
    console.log('API 调用:', apiCalls)
  })

  test('运行时：通过 UI 调用 listInstalls API', async ({ page }) => {
    const apiCalls: string[] = []

    page.on('request', (req) => {
      if (req.url().includes('/plugin/install/list')) {
        apiCalls.push(req.url())
      }
    })

    await page.goto(`${FRONTEND_URL}/plugin`)
    await page.waitForLoadState('networkidle', { timeout: 15_000 })

    await page.getByRole('tab', { name: /已安装/ }).click()
    await page.waitForTimeout(2_000)

    expect(apiCalls.length).toBeGreaterThanOrEqual(1)
    console.log('listInstalls 调用:', apiCalls)
  })
})
