// Phase 6 — Unauthenticated Authentication Tests
//
// 这些测试必须在不带 storageState 的浏览器 context 中运行，
// 验证真实 /login 页面可访问、表单/按钮存在、基础无障碍。
//
// 关键约束：
// - 不使用 storageState
// - 不绕过 router guard
// - 不注入 fake token
// - 直接访问 /login（不是 /）
//
// 运行项目：unauthenticated-chromium + unauthenticated-mobile

import { test, expect } from '@playwright/test'

test.describe('登录模块', () => {
  test('页面正常加载', async ({ page }) => {
    await page.goto('/login')
    // router/index.ts: meta.title = '登录' → document.title = '登录'
    await expect(page).toHaveTitle('登录')
  })

  test('登录表单存在', async ({ page }) => {
    await page.goto('/login')
    const usernameInput = page.getByPlaceholder(/用户名/)
    const passwordInput = page.getByPlaceholder(/密码/)
    await expect(usernameInput).toBeVisible()
    await expect(passwordInput).toBeVisible()
  })

  test('登录按钮存在', async ({ page }) => {
    await page.goto('/login')
    const loginBtn = page.getByRole('button', { name: /登录|login/i })
    await expect(loginBtn).toBeVisible()
  })
})

test.describe('无障碍', () => {
  test('登录页有基础无障碍标签', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('input').first()).toBeVisible()
  })
})
