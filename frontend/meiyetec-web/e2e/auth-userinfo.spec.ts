// AUTH-003 获取用户信息 — API 闭环测试
//
// 不引入新依赖：完全通过 REST 验证
//  1) /auth/login → 200 + token
//  2) /auth/userinfo (带 token) → 200，返回完整 SysUser（password 被脱敏）
//  3) /auth/userinfo 不带 token → 401
//
// 不依赖 UI 点击和 DB 直连，避免在受保护页面加新交互

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE = process.env.BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080'

test('AUTH-003 获取当前登录用户信息 API 闭环', async () => {
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

  // 2) 带 token 调 /auth/userinfo
  const infoRes = await ctx.get('/auth/userinfo', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(infoRes.status()).toBe(200)
  const infoBody = await infoRes.json()
  expect(infoBody.code).toBe(200)
  expect(infoBody.data).toBeTruthy()
  // 真实 DB 中 admin id=1
  expect(infoBody.data.id).toBe(1)
  expect(infoBody.data.username).toBe('admin')
  expect(typeof infoBody.data.realName).toBe('string')
  // password 必须被脱敏/清空
  expect(infoBody.data.password).toBeFalsy()

  await ctx.dispose()
})

test('AUTH-003 无 token 访问 /auth/userinfo 必须 401', async () => {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const infoRes = await ctx.get('/auth/userinfo')
  expect(infoRes.status()).toBe(401)
  await ctx.dispose()
})
