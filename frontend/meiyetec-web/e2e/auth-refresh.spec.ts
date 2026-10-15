// AUTH-005 Token 刷新 — API 闭环测试
//
// 直接 REST 验证：
//  1) /auth/refresh 带合法 token → 200 + 新 token + expiresIn
//  2) 新 token 必须能访问 /auth/userinfo
//  3) 新旧 token 必须不同（jti 强制递增）
//  4) refresh 不带 Authorization → 401
//  5) refresh 带假 token → 401
//  6) refresh 带被篡改签名 token → 401

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE = process.env.BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080'

async function loginAdmin(): Promise<string> {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/login', {
    data: { username: 'admin', password: '123456' }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  await ctx.dispose()
  return body.data.token as string
}

test('AUTH-005 refresh 带合法 token 返回新 token', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/refresh', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(body.data).toBeTruthy()
  expect(typeof body.data.token).toBe('string')
  expect(body.data.token.length).toBeGreaterThan(20)
  // expiresIn：后端返回的是秒数（24h = 86400）
  expect(typeof body.data.expiresIn).toBe('number')
  await ctx.dispose()
})

test('AUTH-005 新 token 可访问 /auth/userinfo', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const ref = await ctx.post('/auth/refresh', {
    headers: { Authorization: `Bearer ${token}` }
  })
  const refBody = await ref.json()
  const newToken = refBody.data.token as string

  const ui = await ctx.get('/auth/userinfo', {
    headers: { Authorization: `Bearer ${newToken}` }
  })
  expect(ui.status()).toBe(200)
  const uiBody = await ui.json()
  expect(uiBody.code).toBe(200)
  expect(uiBody.data.username).toBe('admin')
  expect(uiBody.data.id).toBe(1)
  await ctx.dispose()
})

test('AUTH-005 新旧 token 字符串不同', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const ref = await ctx.post('/auth/refresh', {
    headers: { Authorization: `Bearer ${token}` }
  })
  const refBody = await ref.json()
  const newToken = refBody.data.token as string
  // 由于 jti 强制递增，前后两次签发 token 必然不同
  expect(newToken).not.toBe(token)
  await ctx.dispose()
})

test('AUTH-005 refresh 不带 Authorization 头 必须 401', async () => {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/refresh')
  expect(res.status()).toBe(401)
  await ctx.dispose()
})

test('AUTH-005 refresh 带假 token 必须 401', async () => {
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/refresh', {
    headers: { Authorization: 'Bearer abc' }
  })
  expect(res.status()).toBe(401)
  await ctx.dispose()
})

test('AUTH-005 refresh 带被篡改签名 token 必须 401', async () => {
  const token = await loginAdmin()
  const parts = token.split('.')
  const tampered = parts[0] + '.' + parts[1] + '.AAAAAAAAAAAAAAA'
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.post('/auth/refresh', {
    headers: { Authorization: `Bearer ${tampered}` }
  })
  expect(res.status()).toBe(401)
  await ctx.dispose()
})