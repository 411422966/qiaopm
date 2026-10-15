// AUTH-008 菜单加载 — 端到端 API + 浏览器集成测试
//
// 验证菜单加载完整闭环：
//  1) /auth/me/permissions.data.menus：返回完整菜单树（root + children），来源 DB
//  2) /auth/me/menu：独立接口，200 + 完整菜单树
//  3) 两个接口返回的菜单树结构必须完全相同（同一棵 sys_menu_permission + sys_permission 拼出的树）
//  4) 401 边界：无 token / 篡改签名 token
//  5) 浏览器侧：admin 登录后 Layout.vue 把 store.menus 渲染到 el-menu，DOM 文本含业务菜单名
//
// 数据真实性断言：
//  - menu 至少 15+ 个 root
//  - 至少 5 个 root 含 children
//  - 包含必出现的菜单节点：首页 / 客户运营 / 系统设置 等

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE = process.env.BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080'
const WEB = process.env.WEB_URL?.replace(/\/$/, '') || 'http://localhost:5173'

async function loginAdmin(): Promise<{ token: string }> {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/login', { data: { username: 'admin', password: '123456' } })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  await ctx.dispose()
  return { token: body.data.token }
}

test.describe('AUTH-008 菜单加载 - API 闭环', () => {
  test('/auth/me/permissions.data.menus 含完整菜单树 + 来源 DB', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const body = await res.json()
    const menus = body.data.menus

    expect(Array.isArray(menus)).toBe(true)
    expect(menus.length).toBeGreaterThanOrEqual(15)

    // root 节点：每个 root 含 id / title / path
    for (const r of menus) {
      expect(typeof r.id).toBe('number')
      expect(typeof r.title).toBe('string')
      expect(r.title.length).toBeGreaterThan(0)
      expect(typeof r.path).toBe('string')
    }

    // 至少 5 个 root 含 children 子菜单
    const withChildren = menus.filter((m: any) => Array.isArray(m.children) && m.children.length > 0)
    expect(withChildren.length).toBeGreaterThanOrEqual(5)

    // 必出现的菜单节点（DB sys_menu_permission 中存在的）
    const titles = menus.map((m: any) => m.title)
    const allTitles = [...titles, ...menus.flatMap((m: any) => (m.children || []).map((c: any) => c.title))]
    expect(allTitles).toContain('首页')
    expect(allTitles).toContain('客户运营')
    expect(allTitles).toContain('系统设置')

    await ctx.dispose()
  })

  test('/auth/me/menu 独立接口 200 + 菜单树', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', {
      headers: { Authorization: `Bearer ${token}` }
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.code).toBe(200)

    const menus = body.data
    expect(Array.isArray(menus)).toBe(true)
    expect(menus.length).toBeGreaterThanOrEqual(15)

    // 第一个 root 字段完整性
    const first = menus[0]
    expect(typeof first.id).toBe('number')
    expect(typeof first.title).toBe('string')
    expect(typeof first.path).toBe('string')

    await ctx.dispose()
  })

  test('/auth/me/menu 与 /auth/me/permissions.data.menus 菜单树完全一致', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })

    const permRes = await ctx.get('/auth/me/permissions', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const menuRes = await ctx.get('/auth/me/menu', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const permMenus = (await permRes.json()).data.menus
    const menuMenus = (await menuRes.json()).data

    // root 数相同
    expect(permMenus.length).toBe(menuMenus.length)

    // id 序列相同（同一棵拼出来的树，id=permissionId 是稳定排序锚点）
    const permIds = JSON.stringify(permMenus.map((m: any) => m.id))
    const menuIds = JSON.stringify(menuMenus.map((m: any) => m.id))
    expect(permIds).toBe(menuIds)

    // 任选一个 root 的 children id 序列
    const a = permMenus.find((m: any) => m.children?.length)
    const b = menuMenus.find((m: any) => m.children?.length)
    expect(a).toBeTruthy()
    expect(b).toBeTruthy()
    const aChildIds = JSON.stringify(a.children.map((c: any) => c.id))
    const bChildIds = JSON.stringify(b.children.map((c: any) => c.id))
    expect(aChildIds).toBe(bChildIds)

    await ctx.dispose()
  })

  test('/auth/me/menu 无 token → 401', async () => {
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', { failOnStatusCode: false })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('/auth/me/menu 篡改 token → 401', async () => {
    const { token } = await loginAdmin()
    const parts = token.split('.')
    const tampered = `${parts[0]}.${parts[1]}.AAAAAAAAAAAA`
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', {
      headers: { Authorization: `Bearer ${tampered}` },
      failOnStatusCode: false
    })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('/auth/me/menu Bearer 格式错 → 401', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', {
      headers: { Authorization: token },
      failOnStatusCode: false
    })
    expect(res.status()).toBe(401)
    await ctx.dispose()
  })

  test('菜单 hidden 过滤：所有 root hidden=0（sys_menu_permission.hidden=0）', async () => {
    const { token } = await loginAdmin()
    const ctx = await pwRequest.newContext({ baseURL: BASE })
    const res = await ctx.get('/auth/me/menu', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const menus = (await res.json()).data

    // SysMenuPermissionMapper SQL 过滤了 hidden=1，所以 menu 接口所有 root.hidden 都应是 0 或不存在
    for (const m of menus) {
      if (m.hidden !== undefined && m.hidden !== null) {
        expect(m.hidden).toBe(0)
      }
    }

    await ctx.dispose()
  })
})

test.describe('AUTH-008 菜单加载 - 浏览器渲染', () => {
  test('admin 登录后 Layout.vue el-menu 真实渲染菜单文本（含 DB 数据）', async ({ page }) => {
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })
    await page.waitForTimeout(800)

    // el-menu 容器必须渲染
    const elMenu = page.locator('.el-menu').first()
    await expect(elMenu).toBeVisible()

    // 必出现的菜单文本（来自 DB sys_menu_permission + sys_permission 真表）
    const expectedTexts = ['首页', '客户运营', '系统设置']
    for (const text of expectedTexts) {
      await expect(page.locator('.el-menu').getByText(text, { exact: false }).first()).toBeVisible({ timeout: 5000 })
    }

    // 至少 10 个菜单节点
    const menuItemCount = await page.locator('.el-menu .el-menu-item, .el-menu .el-sub-menu').count()
    expect(menuItemCount).toBeGreaterThanOrEqual(10)
  })

  test('store.menus localStorage 缓存含完整菜单树', async ({ page }) => {
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    const cached = await page.evaluate(() => {
      const raw = localStorage.getItem('rbac.session.v1')
      return raw ? JSON.parse(raw) : null
    })
    expect(cached).toBeTruthy()
    expect(Array.isArray(cached.menus)).toBe(true)
    expect(cached.menus.length).toBeGreaterThanOrEqual(15)
    // 至少含一个 root 有 children
    expect(cached.menus.some((m: any) => Array.isArray(m.children) && m.children.length > 0)).toBe(true)
  })
})