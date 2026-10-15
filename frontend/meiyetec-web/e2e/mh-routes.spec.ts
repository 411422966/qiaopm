// P3.x · T07-8.2 — 补齐缺失的 MH（医美）模块路由 E2E
// 覆盖：
//   1. /mh/visual 路由可达 + DOM 加载（医美视觉档案）
//   2. /mh/consent 路由可达 + DOM 加载（医美合规）
//   3. /complication 路由可达（并发症 SOP，依赖 mh:consent 权限）

import { test, expect } from '@playwright/test'

async function waitPageReady(page: any) {
  await page.waitForLoadState('domcontentloaded')
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
}

test.describe('MH 模块路由可达性（T07-8.2）', () => {
  test('/mh/visual 路由可达 + 视觉档案页面渲染', async ({ page }) => {
    await page.goto('/mh/visual')
    await waitPageReady(page)

    // 路由断言：停留在 /mh/visual
    await expect(page).toHaveURL(/\/mh\/visual$/)

    // 页面渲染：标题 + 卡片 / 表头 / 表单 至少一个出现
    const body = page.locator('h1, h2, h3, .el-card__header, .page-title, .el-table, .el-empty, form').first()
    await expect(body).toBeVisible({ timeout: 8000 })
  })

  test('/mh/consent 路由可达 + 合规页面渲染', async ({ page }) => {
    await page.goto('/mh/consent')
    await waitPageReady(page)

    await expect(page).toHaveURL(/\/mh\/consent$/)

    const body = page.locator('h1, h2, h3, .el-card__header, .page-title, .el-table, .el-empty, form').first()
    await expect(body).toBeVisible({ timeout: 8000 })
  })

  test('/complication 路由可达（依赖 mh:consent 权限）', async ({ page }) => {
    await page.goto('/complication')
    await waitPageReady(page)

    await expect(page).toHaveURL(/\/complication$/)

    const body = page.locator('h1, h2, h3, .el-card__header, .page-title, .el-table, .el-empty').first()
    await expect(body).toBeVisible({ timeout: 8000 })
  })

  test('MH 模块路由无 5xx 错误', async ({ page }) => {
    const failures: any[] = []
    page.on('response', (resp) => {
      if (resp.status() >= 500) {
        failures.push({ url: resp.url(), status: resp.status() })
      }
    })

    for (const path of ['/mh/visual', '/mh/consent', '/complication']) {
      await page.goto(path)
      await waitPageReady(page)
    }

    expect(failures, JSON.stringify(failures)).toHaveLength(0)
  })
})
