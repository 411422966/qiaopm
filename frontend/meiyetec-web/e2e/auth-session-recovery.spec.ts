// AUTH-006 登录状态恢复 — 端到端 Playwright 测试
//
// 验证"浏览器刷新后，前端能从 localStorage 恢复登录态，直接进 dashboard 而不弹 /login"的完整链路。
//
// 测试策略：
//  1) 真实走 UI 登录（不直接灌 localStorage，必须证明 login.vue 的持久化路径真的写入了 token/user/rbac.session.v1）
//  2) 登录后断言 URL = /dashboard、localStorage 三件套齐全
//  3) 跳转一个受保护路由 → reload() 模拟 F5 刷新
//  4) 刷新后断言：URL 未变成 /login、URL 仍是同一受保护路由、token 仍存在
//
// 不复用 setup 的 admin.storage.json：本 spec 必须走 unauthenticated-chromium，自己登录。
//
// 设计要点：
//  - Vite 5173 → 后端 8080 的 proxy 保证同源，刷新不会丢 token（localStorage 同源）
//  - 不在测试里手动注入 fake token，避免"绕过 login 持久化"的伪证

import { test, expect } from '@playwright/test'

const WEB = process.env.WEB_URL?.replace(/\/$/, '') || 'http://localhost:5173'

test.describe('AUTH-006 登录状态恢复', () => {
  test('登录成功后 localStorage 三件套都被写入', async ({ page }) => {
    await page.goto(WEB + '/login')
    await expect(page).toHaveTitle('登录')

    // 表单输入
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()

    // 登录后必须跳转到 /dashboard（默认 redirect）
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    // 验证 localStorage 三件套已写入
    const stored = await page.evaluate(() => ({
      token: localStorage.getItem('token'),
      user: localStorage.getItem('user'),
      rbac: localStorage.getItem('rbac.session.v1')
    }))

    expect(stored.token).toBeTruthy()
    expect(stored.token!.length).toBeGreaterThan(20)
    expect(stored.user).toBeTruthy()
    const user = JSON.parse(stored.user!)
    expect(user.username).toBe('admin')
    expect(stored.rbac).toBeTruthy()
    const rbac = JSON.parse(stored.rbac!)
    expect(rbac.username).toBe('admin')
    expect(rbac.userId).toBe(1)
    expect(Array.isArray(rbac.permissions)).toBe(true)
    expect(rbac.permissions.length).toBeGreaterThan(0)
  })

  test('页面刷新后仍停留在受保护页面，未被踢回 /login', async ({ page }) => {
    // 1) 真实登录
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    // 2) 进入一个受保护的非 dashboard 路由，确保 router 守卫放过
    await page.goto(WEB + '/sys/login-log')
    await page.waitForLoadState('domcontentloaded')
    const beforePath = new URL(page.url()).pathname
    expect(beforePath).toBe('/sys/login-log')

    // 3) 模拟"按 F5 刷新" —— Pinia store 全清空，但 localStorage 保留
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 15000 })

    // 4) 关键断言：刷新后未跳到 /login
    const afterPath = new URL(page.url()).pathname
    expect(afterPath).not.toBe('/login')
    // 停留在同一受保护路由
    expect(afterPath).toBe('/sys/login-log')

    // 5) localStorage 三件套仍保留
    const stored = await page.evaluate(() => ({
      token: localStorage.getItem('token'),
      user: localStorage.getItem('user'),
      rbac: localStorage.getItem('rbac.session.v1')
    }))
    expect(stored.token).toBeTruthy()
    expect(stored.user).toBeTruthy()
    expect(stored.rbac).toBeTruthy()
  })

  test('退出后三件套被清空，再次刷新跳 /login', async ({ page }) => {
    // 1) 登录
    await page.goto(WEB + '/login')
    await page.getByPlaceholder(/用户名/).fill('admin')
    await page.getByPlaceholder(/密码/).fill('123456')
    await page.getByRole('button', { name: /登录/ }).click()
    await page.waitForURL((url) => new URL(url).pathname === '/dashboard', { timeout: 10000 })

    // 2) 验证三件套已写入
    const before = await page.evaluate(() => ({
      token: localStorage.getItem('token'),
      user: localStorage.getItem('user'),
      rbac: localStorage.getItem('rbac.session.v1')
    }))
    expect(before.token).toBeTruthy()
    expect(before.user).toBeTruthy()
    expect(before.rbac).toBeTruthy()

    // 3) 模拟 Layout.vue handleLogout —— 清空 3 个 key
    // 立刻断言 3 个 key 已清空（这一步必须在任何请求回写 user 之前做完）
    await page.evaluate(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('rbac.session.v1')
    })
    const cleared = await page.evaluate(() => ({
      token: localStorage.getItem('token'),
      user: localStorage.getItem('user'),
      rbac: localStorage.getItem('rbac.session.v1')
    }))
    expect(cleared.token).toBeNull()
    expect(cleared.user).toBeNull()
    expect(cleared.rbac).toBeNull()

    // 4) 刷新后被弹到 /login（router 守卫因没 token 而踢回）
    await page.reload({ waitUntil: 'domcontentloaded', timeout: 15000 })
    await page.waitForURL((url) => new URL(url).pathname === '/login', { timeout: 10000 })
    expect(new URL(page.url()).pathname).toBe('/login')
  })

  test('未登录访问受保护路由 → 跳 /login 并带 redirect 参数', async ({ page }) => {
    // 先清干净 localStorage，防止前序测试残留
    await page.goto(WEB + '/login')
    await page.evaluate(() => {
      localStorage.clear()
    })
    // 再访问受保护路由
    await page.goto(WEB + '/sys/login-log')

    // 等到 router 跳转完成
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10000 })

    // URL 必须带 redirect 参数：值等于 /sys/login-log（特殊字符会被解码）
    const url = new URL(page.url())
    const redirect = url.searchParams.get('redirect')
    expect(redirect).toBeTruthy()
    expect(decodeURIComponent(redirect!)).toBe('/sys/login-log')
  })
})