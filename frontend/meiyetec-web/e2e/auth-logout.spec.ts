// AUTH-002 用户退出 — API 闭环测试
//
// 不引入新依赖：完全通过 REST 验证
//  1) /auth/login → 200 + token
//  2) /auth/logout (带 token) → 200
//  3) /auth/logout 不在 JWT 拦截黑名单中（接口可达）
//
// 不依赖 UI 点击和 DB 直连，避免在受保护页面加新交互

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE = process.env.BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080'

test('AUTH-002 用户退出 API 闭环', async () => {
  const ctx = await pwRequest.newContext({ baseURL: BASE })

  // 1) 登录拿 token
  const loginRes = await ctx.post('/auth/login', {
    data: { username: 'admin', password: '123456' }
  })
  expect(loginRes.status()).toBe(200)
  const loginBody = await loginRes.json()
  expect(loginBody.code).toBe(200)
  const token = loginBody.data.token
  expect(typeof token).toBe('string')
  expect(token.length).toBeGreaterThan(20)

  // 2) 带 token 退出
  const logoutRes = await ctx.post('/auth/logout', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(logoutRes.status()).toBe(200)
  const logoutBody = await logoutRes.json()
  expect(logoutBody.code).toBe(200)
  expect(logoutBody.message).toBeTruthy()

  await ctx.dispose()
})
