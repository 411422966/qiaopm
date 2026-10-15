// P2.11 E2E — Smoke Tests (Phase 3)
// 仅验证 auth setup + 5 个最关键路由。

import { test, expect } from '@playwright/test'

test('smoke: dashboard 加载成功', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/dashboard/)
  // 标题由 router.beforeEach 设置为 '首页'（meta.title）
  await expect(page).toHaveTitle(/首页|美业SaaS系统/)
})

test('smoke: /member 可访问', async ({ page }) => {
  await page.goto('/member')
  await expect(page).toHaveURL(/\/member/)
})

test('smoke: /booking/calendar 可访问', async ({ page }) => {
  await page.goto('/booking/calendar')
  await expect(page).toHaveURL(/\/booking\/calendar/)
})

test('smoke: /pos/orders 可访问', async ({ page }) => {
  await page.goto('/pos/orders')
  await expect(page).toHaveURL(/\/pos\/orders/)
})

test('smoke: localStorage 中确实有 token', async ({ page }) => {
  await page.goto('/dashboard')
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token, 'token in localStorage').toBeTruthy()
  expect(typeof token).toBe('string')
  expect((token as string).length).toBeGreaterThan(20)
})
