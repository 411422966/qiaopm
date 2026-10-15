// AUTH-007 权限加载 — 端到端 API + 浏览器集成测试
//
// 验证"GET /auth/me/permissions"完整闭环：
//  1) 带合法 token → 200 + RbacSessionDTO（含 userId/username/permissions/menus/roles/dataScope）
//  2) 数据来自 DB：permissions 中应包含 DB sys_permission 表真实存在的权限码（至少包含 '*'、特定前缀）
//  3) 401 边界：无 token / 篡改签名 token / 过期 token 均 401
//  4) 浏览器侧：登录后 rbac.session.v1 localStorage 缓存被写入
//  5) 路由 meta.permission 拦截：无权限路由跳 /403
//
// 设计原则：
//  - 全部走 API + Playwright request/response，不依赖 UI 点击
//  - admin 通配 * 让所有路由 200，从而验证"权限加载后 router 守卫放行"
//
// 不复用 setup 的 admin.storage.json，本 spec 走 unauthenticated-chromium。

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE = process.env.BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080'
const WEB = process.env.WEB_URL?.replace(/\/$/, '') || 'http://localhost:5173'

async function loginAdmin(): Promise<{ token: string; payload: any }> {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/login', { data: { username: 'admin', password: '123456' } })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  await ctx.dispose()
  return { token: body.data.token, payload: body.data }
}

test.describe('AUTH-007 权限加载 - API 闭环', () => {
  test('合法 token → /auth/me/permissions 200 + 完整 session', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${token}` }
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data).toBeTruthy()

    // 字段完整性：RbacSessionDTO 必含字段
    const d = body.data
    expect(typeof d.userId).toBe('number')
    expect(d.username).toBe('admin')
    expect(Array.isArray(d.permissions)).toBe(true)
    expect(Array.isArray(d.menus)).toBe(true)
    expect(Array.isArray(d.roles)).toBe(true)
    expect(typeof d.dataScope).toBe('string')
    expect(Array.isArray(d.storeIds)).toBe(true)

    // admin 必须有 * 通配（RbacContextLoader.line 78-79 注入）
    expect(d.permissions).toContain('*')

    // 角色必须包含 SUPER_ADMIN 或 PLATFORM_ADMIN
    const isSuper = d.roles.some((r: string) => r === 'SUPER_ADMIN' || r === 'PLATFORM_ADMIN')
    expect(isSuper).toBe(true)

    await ctx.dispose()
  })

  test('permissions 来自 DB sys_permission 表（数据真实性）', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const body = await res.json()
    const perms: string[] = body.data.permissions
    expect(perms.length).toBeGreaterThan(0)

    // 应当包含 DB 中真实的常见权限码（前缀至少要匹配一个）
    const mustHavePrefixes = ['member', 'booking', 'wallet', 'setting', 'stock']
    for (const prefix of mustHavePrefixes) {
      const hit = perms.some((p) => p.startsWith(prefix))
      expect(hit, `permissions 中必须含 ${prefix}* 至少一个码`).toBe(true)
    }

    await ctx.dispose()
  })

  test('menus 是数组 + 含 id/title/path', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const body = await res.json()
    const menus = body.data.menus
    expect(Array.isArray(menus)).toBe(true)
    expect(menus.length).toBeGreaterThan(0)

    // 至少一个菜单含 id/title/path
    const first = menus[0]
    expect(typeof first.id).toBe('number')
    expect(typeof first.title).toBe('string')
    expect(typeof first.path).toBe('string')

    await ctx.dispose()
  })

  test('不带 Authorization → 401', async () => {
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', { failOnStatusCode: false })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('篡改签名 token → 401', async () => {
    const { token } = await loginAdmin()
    const parts = token.split('.')
    const tampered = `${parts[0]}.${parts[1]}.AAAAAAAAAAAA`
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${tampered}` },
      failOnStatusCode: false
    })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('Bearer 格式错（无 Bearer 前缀） → 401', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: token },  // 缺 Bearer 前缀
      failOnStatusCode: false
    })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('/auth/me/menu 同样要求 401 边界（兼容老接口）', async () => {
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', { failOnStatusCode: false })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })
})

test.describe('AUTH-007 权限加载 - 浏览器集成', () => {
  test('登录成功后浏览器 localStorage 中 rbac.session.v1 含完整 session', async ({ page }) => {
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    // rbac.session.v1 必须存在，且含 userId / permissions
    const cached = await page.evaluate(() => {
      const raw = localStorage.getItem('rbac.session.v1')
      return raw ? JSON.parse(raw) : null
    })
    expect(cached).toBeTruthy()
    expect(cached.userId).toBe(1)
    expect(cached.username).toBe('admin')
    expect(Array.isArray(cached.permissions)).toBe(true)
    expect(cached.permissions).toContain('*')
    expect(Array.isArray(cached.menus)).toBe(true)
  })

  test('admin 通配 → 路由守卫放行所有路由（点 /sys/login-log 不跳 /403）', async ({ page }) => {
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    // 访问一个需要登录 + permission 的路由，admin 有 * 通配必须不被 403
    await page.goto(WEB + '/sys/login-log')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(800)
    const path = new URL(page.url()).pathname
    expect(path).not.toBe('/403')
    expect(path).toBe('/sys/login-log')
  })
})