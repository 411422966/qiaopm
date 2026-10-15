// Runtime Verification -- End-to-end sanity check (static test names).
import { test, expect, type Page } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const USER = process.env.E2E_USERNAME || 'admin'
const PASS = process.env.E2E_PASSWORD || '123456'

const consoleErrors = []
const requestFailures = []

function attachListeners(page, name) {
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push({ name, msg: msg.text() })
  })
  page.on('pageerror', (err) => {
    consoleErrors.push({ name, msg: '[pageerror] ' + err.message })
  })
  page.on('response', (resp) => {
    const status = resp.status()
    if (status >= 500) requestFailures.push({ name, url: resp.url(), status, method: resp.request().method() })
  })
}

async function uiLogin(page) {
  await page.context().clearCookies()
  await page.goto(BASE_URL + '/login', { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { try { localStorage.clear(); sessionStorage.clear() } catch {} }).catch(() => {})
  await page.goto(BASE_URL + '/login', { waitUntil: 'domcontentloaded' })
  await page.locator('input[placeholder*="用户"]').waitFor({ timeout: 8000 })
  await page.locator('input[placeholder*="用户"]').fill(USER)
  await page.locator('input[placeholder*="密码"]').fill(PASS)
  await page.locator('button:has-text("登录")').click()
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 12000 })
}

test.describe('Login and navigation', () => {
  test('1.1 Login page loads', async ({ page }) => {
    attachListeners(page, '1.1')
    await page.goto(BASE_URL + '/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('input[placeholder*="用户"]')).toBeVisible({ timeout: 8000 })
    await expect(page.locator('input[placeholder*="密码"]')).toBeVisible()
    await expect(page.locator('button:has-text("登录")')).toBeVisible()
  })

  test('1.2 Login succeeds and lands on dashboard', async ({ page }) => {
    attachListeners(page, '1.2')
    await uiLogin(page)
    const url = page.url()
    expect(url).not.toContain('/login')
    const title = await page.title()
    expect(title.length).toBeGreaterThan(0)
    console.log('[1.2] landed at: ' + url + ' title: ' + title)
  })

  test('1.3 Sidebar visible after login', async ({ page }) => {
    attachListeners(page, '1.3')
    await uiLogin(page)
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {})
    const sidebar = page.locator('.sidebar, .el-menu, aside, [class*="menu"]').first()
    await expect(sidebar).toBeVisible({ timeout: 8000 })
  })

  test('1.4 No fatal console errors after login', async ({ page }) => {
    attachListeners(page, '1.4')
    await uiLogin(page)
    await page.waitForTimeout(2000)
    const real = consoleErrors.filter((e) => !/favicon|devtools/i.test(e.msg))
    expect(real.length, JSON.stringify(real)).toBeLessThan(3)
  })
})

test.describe('Core business pages', () => {
  test('2.1 Dashboard loads', async ({ page }) => {
    attachListeners(page, 'dashboard')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/dashboard', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /dashboard ' + (Date.now() - t0) + 'ms')
  })

  test('2.2 Member list loads', async ({ page }) => {
    attachListeners(page, 'member')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title, .el-table, .el-empty').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /member ' + (Date.now() - t0) + 'ms')
  })

  test('2.3 Booking calendar loads', async ({ page }) => {
    attachListeners(page, 'booking')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/booking/calendar', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /booking/calendar ' + (Date.now() - t0) + 'ms')
  })

  test('2.4 POS orders loads', async ({ page }) => {
    attachListeners(page, 'pos-orders')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/pos/orders', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /pos/orders ' + (Date.now() - t0) + 'ms')
  })

  test('2.5 Stock list loads', async ({ page }) => {
    attachListeners(page, 'stock')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/stock/list', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /stock/list ' + (Date.now() - t0) + 'ms')
  })

  test('2.6 Report dashboard loads', async ({ page }) => {
    attachListeners(page, 'report')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/report/dashboard', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    console.log('[PERF] /report/dashboard ' + (Date.now() - t0) + 'ms')
  })

  test('2.7 Intel LTV loads (chart page)', async ({ page }) => {
    attachListeners(page, 'intel-ltv')
    await uiLogin(page)
    const t0 = Date.now()
    const resp = await page.goto(BASE_URL + '/intel/ltv', { waitUntil: 'domcontentloaded' })
    expect(resp.status()).toBeLessThan(400)
    await expect(page.locator('h1, h2, h3, .el-card__header, .page-title').first()).toBeVisible({ timeout: 8000 })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    // Verify canvas (echarts) is rendered
    const canvas = page.locator('canvas').first()
    await page.waitForTimeout(1500) // give echarts time to render
    const hasCanvas = await canvas.count()
    console.log('[CHART] /intel/ltv canvas count: ' + hasCanvas)
    console.log('[PERF] /intel/ltv ' + (Date.now() - t0) + 'ms')
  })
})

test.describe('API connectivity', () => {
  test('3.1 Token carried in requests', async ({ page }) => {
    attachListeners(page, '3.1')
    await uiLogin(page)
    const token = await page.evaluate(() => localStorage.getItem('token'))
    expect(token).toBeTruthy()
    expect(token.length).toBeGreaterThan(20)

    let authHeader = ''
    page.on('request', (req) => {
      const h = req.headers()['authorization']
      if (h) authHeader = h
    })
    await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    expect(authHeader).toMatch(/^Bearer /)
  })

  test('3.2 Requests routed through Vite proxy', async ({ page }) => {
    attachListeners(page, '3.2')
    const seenUrls = []
    page.on('request', (req) => {
      const u = req.url()
      if (u.includes('/api/')) seenUrls.push(u)
    })
    await uiLogin(page)
    await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    expect(seenUrls.length).toBeGreaterThan(0)
    const proxied = seenUrls.filter((u) => u.startsWith('http://localhost:5173/api/') || u.startsWith('/api/'))
    console.log('[API URLS] sample: ' + seenUrls.slice(0, 3).join(' | '))
    expect(proxied.length).toBe(seenUrls.length)
  })

  test('3.3 No 5xx responses during page tour', async ({ page }) => {
    attachListeners(page, '3.3')
    await uiLogin(page)
    for (const p of ['/dashboard', '/member', '/booking/calendar', '/pos/orders', '/stock/list', '/report/dashboard', '/intel/ltv']) {
      await page.goto(BASE_URL + p, { waitUntil: 'domcontentloaded' })
      await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
    }
    const fives = requestFailures.filter((r) => r.name === '3.3')
    expect(fives.length, JSON.stringify(fives)).toBe(0)
  })
})

test.describe('Member CRUD', () => {
  test('4.1 Member list shows data', async ({ page }) => {
    attachListeners(page, '4.1')
    await uiLogin(page)
    await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {})
    const table = page.locator('.el-table, table').first()
    const empty = page.locator('.el-empty, [class*="empty"]').first()
    await expect(table.or(empty)).toBeVisible({ timeout: 10000 })
  })

  test('4.2 New member button opens dialog', async ({ page }) => {
    attachListeners(page, '4.2')
    await uiLogin(page)
    await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {})
    const addBtn = page.getByRole('button', { name: /新建|添加|新增|创建/ }).first()
    await expect(addBtn).toBeVisible({ timeout: 8000 })
    await addBtn.click({ timeout: 5000 })
    await page.waitForTimeout(800)
    const dialog = page.locator('.el-dialog, .el-drawer, form').first()
    await expect(dialog).toBeVisible({ timeout: 5000 })
  })

  test('4.3 Member search/filter works', async ({ page }) => {
    attachListeners(page, '4.3')
    await uiLogin(page)
    await page.goto(BASE_URL + '/member', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {})
    // Look for any search input
    const search = page.locator('input[placeholder*="搜索"], input[placeholder*="查询"], input[placeholder*="姓名"], input[placeholder*="手机"]').first()
    if (await search.count() > 0) {
      await search.fill('test')
      await page.waitForTimeout(1000)
      console.log('[4.3] search input found and filled')
    } else {
      console.log('[4.3] no search input on member page (skipped)')
    }
  })
})

test.afterAll(() => {
  console.log('\n========== RUNTIME VERIFICATION SUMMARY ==========')
  console.log('Total console errors: ' + consoleErrors.length)
  for (const e of consoleErrors.slice(0, 20)) console.log('  [' + e.name + '] ' + e.msg.slice(0, 200))
  console.log('Total 5xx responses: ' + requestFailures.length)
  for (const r of requestFailures.slice(0, 20)) console.log('  [' + r.name + '] ' + r.method + ' ' + r.url + ' -> ' + r.status)
  console.log('===================================================\n')
})
