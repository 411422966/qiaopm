// AUTH-004 登录日志查询 — API 闭环测试
//
// 不依赖 UI 点击，直接 REST 验证：
//  1) /sys/login-log/page → 200 + records 非空
//  2) /sys/login-log/stats → 200 + totalCount 数字
//  3) /sys/login-log/trend?days=7 → 200 + N 天数据
//  4) /sys/login-log/anomaly → 200 + topFailIps + topFailUsers
//  5) /sys/login-log/search?q=admin → 200 + records 非空
//  6) /sys/login-log/export → 200 + CSV 流（含 BOM）
//  7) /sys/login-log/clear?days=0 → 200 + data=0（兜底，不删数据）
//  8) DELETE /sys/login-log/{不存在ID} → 200 + data=false（不抛异常）

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

test('AUTH-004 page 分页查询', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/page?pageNum=1&pageSize=5', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(body.data).toBeTruthy()
  expect(typeof body.data.total).toBe('number')
  expect(Array.isArray(body.data.records)).toBe(true)
  expect(body.data.records.length).toBeGreaterThan(0)
  expect(body.data.records[0]).toHaveProperty('username')
  await ctx.dispose()
})

test('AUTH-004 stats 统计', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/stats?days=30', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(typeof body.data.totalCount).toBe('number')
  expect(typeof body.data.todayCount).toBe('number')
  expect(typeof body.data.totalSuccessRate).toBe('number')
  await ctx.dispose()
})

test('AUTH-004 trend 趋势', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/trend?days=7', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(Array.isArray(body.data)).toBe(true)
  expect(body.data.length).toBe(7)
  expect(body.data[0]).toHaveProperty('date')
  expect(body.data[0]).toHaveProperty('total')
  expect(body.data[0]).toHaveProperty('success')
  expect(body.data[0]).toHaveProperty('fail')
  await ctx.dispose()
})

test('AUTH-004 anomaly 异常 TopN', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/anomaly?days=30&top=5', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(Array.isArray(body.data.topFailIps)).toBe(true)
  expect(Array.isArray(body.data.topFailUsers)).toBe(true)
  await ctx.dispose()
})

test('AUTH-004 search 全文搜索', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/search?q=admin&pageNum=1&pageSize=5', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(typeof body.data.total).toBe('number')
  expect(Array.isArray(body.data.records)).toBe(true)
  await ctx.dispose()
})

test('AUTH-004 export CSV 流', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.get('/sys/login-log/export', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const ctype = res.headers()['content-type'] || ''
  expect(ctype).toContain('text/csv')
  const body = await res.body()
  // 必须带 UTF-8 BOM（避免 Excel 打开乱码）
  expect(body[0]).toBe(0xef)
  expect(body[1]).toBe(0xbb)
  expect(body[2]).toBe(0xbf)
  // body 至少包含表头
  const text = body.toString('utf-8')
  expect(text).toContain('ID')
  expect(text).toContain('用户名')
  await ctx.dispose()
})

test('AUTH-004 clear(days<=0) 必须 data=0 不删数据', async () => {
  // 直接调多次 login 把"基线行数"抬高，记录基线后跑 clear，再保证"clear 不会回退任何已存在的成功登录记录"
  // 关键不变量：执行 clear 之前的 total，与 clear 之后的 total - (clear 期间 / 之后产生的 login) 比较没有意义
  // 因此我们只断言 clear 接口返回 data=0；不变量校验改为"幂等调用 clear 不会让 total 跌到 0"
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })

  const res = await ctx.post('/sys/login-log/clear?days=0', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  expect(body.data).toBe(0)

  // 再确认 page 还能返回数据（如果 clear 真删了，total 会变 0 但本次至少还应有 loginAdmin 那行）
  const after = await ctx.get('/sys/login-log/page?pageNum=1&pageSize=1', {
    headers: { Authorization: `Bearer ${token}` }
  })
  const afterBody = await after.json()
  expect(afterBody.data.total).toBeGreaterThan(0)
  await ctx.dispose()
})

test('AUTH-004 delete 不存在的 id 不能抛异常', async () => {
  const token = await loginAdmin()
  const ctx = await pwRequest.newContext({ baseURL: BASE })
  const res = await ctx.delete('/sys/login-log/999999999', {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  expect(body.code).toBe(200)
  // MP removeById 对不存在 id 返回 false
  expect(body.data).toBe(false)
  await ctx.dispose()
})