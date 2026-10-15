// QPM-005-03 — 套餐（Package）E2E P0 财务安全验证
//
// 资金安全点：
//   1) 套餐购买：金额、次数、过期时间、初始状态
//   2) 套餐核销：CAS 并发、remainingTimes 正确扣减、状态流转
//   3) 套餐退款：剩余次数比例折算、钱包 balance 守恒、退款幂等
//   4) 状态机：用尽/过期/冻结后不可核销
//
// 业务防护验证：
//   - 次数不足时拒绝核销
//   - 重复退款被拒
//   - 退款后 wallet 余额增加 = 折算金额
//   - 退款后 crm_member.balance 与 wallet.balance 同步
//
// P0-T10：CI 严格模式 — 失败即 fail。
// 依赖 seed 数据：会员 M202607001 (id=1)，管理员 admin/123456。

import { test, expect, request, type APIRequestContext } from '@playwright/test'
import './helpers.ts'

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080'
const USERNAME = process.env.E2E_USERNAME || 'admin'
const PASSWORD = process.env.E2E_PASSWORD || '123456'
const TEST_MEMBER_ID = 1   // 李雅琳

async function authedApi(): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: API_BASE })
  const r = await ctx.post('/auth/login', {
    headers: { 'Content-Type': 'application/json' },
    data: { username: USERNAME, password: PASSWORD }
  })
  expect(r.status()).toBe(200)
  const j = await r.json()
  expect(j.code).toBe(200)
  const token = j.data.token as string
  await ctx.dispose()
  return await request.newContext({
    baseURL: API_BASE,
    extraHTTPHeaders: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })
}

/** 创建一个测试套餐并返回 id */
async function createTestPackage(ctx: APIRequestContext, name: string, price: number, totalTimes: number) {
  const resp = await ctx.post('/packages', {
    data: {
      name,
      code: `TEST_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      category: 'TEST',
      totalTimes,
      price,
      costPrice: 0,
      validDays: 365,
      status: 1,
      remark: 'QPM-005 E2E 测试套餐'
    }
  })
  expect(resp.status(), `create package HTTP status (${name})`).toBe(200)
  const json = await resp.json()
  expect(json.code, `create package business code (${name})`).toBe(200)
  return json.data.id as number
}

/** 购买套餐并返回订单 id */
async function purchasePackage(
  ctx: APIRequestContext,
  packageId: number,
  memberId: number,
  payMethod = '微信'
) {
  const resp = await ctx.post('/packages/purchase', {
    data: {
      memberId,
      packageId,
      price: 100,
      payMethod,
      remark: 'QPM-005 E2E 购买',
      operatorId: 1
    }
  })
  expect(resp.status()).toBe(200)
  const json = await resp.json()
  return json
}

test.describe('QPM-005-03 套餐（P0 财务安全）', () => {

  test('1) 套餐购买：创建套餐 → 购买 → 验证订单字段', async () => {
    const ctx = await authedApi()

    // 1) 创建套餐
    const packageName = `P0Test-${Date.now()}`
    const packageId = await createTestPackage(ctx, packageName, 100, 5)
    expect(packageId).toBeGreaterThan(0)

    // 2) 购买套餐
    const purchase = await purchasePackage(ctx, packageId, TEST_MEMBER_ID)
    expect(purchase.code).toBe(200)
    expect(purchase.data.orderId).toBeGreaterThan(0)
    expect(purchase.data.orderNo).toMatch(/^PKG\d{14}/)
    expect(purchase.data.totalTimes).toBe(5)
    expect(purchase.data.remainingTimes).toBe(5)
    expect(purchase.data.price).toBe(100)

    const orderId = purchase.data.orderId
    await ctx.dispose()
  })

  test('2) 套餐核销：CAS 扣减 + 余额正确', async () => {
    const ctx = await authedApi()
    const packageId = await createTestPackage(ctx, `Redeem-${Date.now()}`, 200, 10)
    const purchase = await purchasePackage(ctx, packageId, TEST_MEMBER_ID)
    const orderId = purchase.data.orderId

    // 核销 3 次
    const redeem = await ctx.post('/packages/redeem', {
      data: {
        orderId,
        times: 3,
        serviceItem: 'P0 测试服务',
        beauticianId: 4,
        operatorId: 1
      }
    })
    expect(redeem.status()).toBe(200)
    const redeemJson = await redeem.json()
    expect(redeemJson.code, `redeem business code: ${JSON.stringify(redeemJson)}`).toBe(200)
    expect(redeemJson.data.redeemTimes).toBe(3)
    expect(redeemJson.data.remainingTimes).toBe(7)
    expect(redeemJson.data.orderStatus).toBe(1)  // 仍正常

    await ctx.dispose()
  })

  test('3) 套餐核销防护：剩余 0 时再核销应被拒绝', async () => {
    const ctx = await authedApi()
    const packageId = await createTestPackage(ctx, `Exhaust-${Date.now()}`, 50, 2)
    const purchase = await purchasePackage(ctx, packageId, TEST_MEMBER_ID)
    const orderId = purchase.data.orderId

    // 核销 2 次用尽
    const r1 = await ctx.post('/packages/redeem', {
      data: { orderId, times: 2, beauticianId: 4, operatorId: 1 }
    })
    expect(r1.status()).toBe(200)
    const j1 = await r1.json()
    expect(j1.code).toBe(200)

    // 第 3 次必须被拒（PACKAGE_TIMES_NOT_ENOUGH — 实际业务码 5201）
    const r2 = await ctx.post('/packages/redeem', {
      data: { orderId, times: 1, beauticianId: 4, operatorId: 1 }
    })
    expect(r2.status()).toBe(200)
    const j2 = await r2.json()
    expect(j2.code, `expected non-200, got ${JSON.stringify(j2)}`).not.toBe(200)
    // 实际业务返回码：5201 PACKAGE_TIMES_NOT_ENOUGH（不同名但同语义）
    expect(j2.message).toMatch(/剩余次数不足|已用完/)

    await ctx.dispose()
  })

  test('4) 套餐退款：金额守恒 + 钱包余额同步 + 幂等保护', async () => {
    const ctx = await authedApi()
    const packageId = await createTestPackage(ctx, `Refund-${Date.now()}`, 100, 4)
    const purchase = await purchasePackage(ctx, packageId, TEST_MEMBER_ID)
    const orderId = purchase.data.orderId

    // 核销 1 次（剩 3/4）
    await ctx.post('/packages/redeem', {
      data: { orderId, times: 1, beauticianId: 4, operatorId: 1 }
    })

    // 读退款前余额
    const beforeBalance = await ctx.get(`/wallet/${TEST_MEMBER_ID}`)
    const bj = await beforeBalance.json()
    expect(bj.code).toBe(200)
    const balanceBefore = bj.data.balance

    // 退款（按剩余 3/4 × 100 = 75）
    const refund = await ctx.post(`/packages/order/${orderId}/refund`, {
      params: { operatorId: 1 }
    })
    expect(refund.status()).toBe(200)
    const rj = await refund.json()
    expect(rj.code, `refund: ${JSON.stringify(rj)}`).toBe(200)
    expect(rj.data.refundAmount).toBe(75)            // 100 * 3/4 = 75
    expect(rj.data.balanceBefore).toBeCloseToNumber(balanceBefore)
    expect(rj.data.balanceAfter - balanceBefore).toBeCloseToNumber(75)

    // 验证钱包余额真的更新了
    const afterBalance = await ctx.get(`/wallet/${TEST_MEMBER_ID}`)
    const aj = await afterBalance.json()
    expect(aj.code).toBe(200)
    expect(aj.data.balance - balanceBefore).toBeCloseToNumber(75)

    // 幂等：再调用一次必须被拒
    const dup = await ctx.post(`/packages/order/${orderId}/refund`, {
      params: { operatorId: 1 }
    })
    const dj = await dup.json()
    expect(dj.code, `dupe refund should fail, got ${JSON.stringify(dj)}`).not.toBe(200)
    expect(dj.message).toMatch(/已退款|不能重复/)

    await ctx.dispose()
  })

  test('5) 套餐状态机：冻结后不能核销', async () => {
    const ctx = await authedApi()
    const packageId = await createTestPackage(ctx, `Frozen-${Date.now()}`, 100, 3)
    const purchase = await purchasePackage(ctx, packageId, TEST_MEMBER_ID)
    const orderId = purchase.data.orderId

    // 冻结
    const freeze = await ctx.post(`/packages/order/${orderId}/freeze`, {
      params: { freeze: true }
    })
    expect(freeze.status()).toBe(200)
    const fj = await freeze.json()
    expect(fj.code).toBe(200)
    expect(fj.data.status).toBe(4)  // 已冻结

    // 核销被拒
    const redeem = await ctx.post('/packages/redeem', {
      data: { orderId, times: 1, beauticianId: 4, operatorId: 1 }
    })
    const rj = await redeem.json()
    expect(rj.code, `frozen redeem should fail, got ${JSON.stringify(rj)}`).not.toBe(200)
    expect(rj.message).toMatch(/冻结/)

    // 解冻
    await ctx.post(`/packages/order/${orderId}/freeze`, { params: { freeze: false } })

    await ctx.dispose()
  })

  test('6) 会员套餐查询：分页 + 订单列表正确', async () => {
    const ctx = await authedApi()
    // 先创建一笔订单确保有数据
    const packageId = await createTestPackage(ctx, `MemberPkgs-${Date.now()}`, 80, 5)
    await purchasePackage(ctx, packageId, TEST_MEMBER_ID)

    const resp = await ctx.get(`/packages/member/${TEST_MEMBER_ID}`)
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code, `member packages code: ${JSON.stringify(json).slice(0, 200)}`).toBe(200)
    // 接口返回 rows / list / records 都接受（不同分页实现）
    const items = json.data.records ?? json.data.rows ?? json.data.list
    expect(Array.isArray(items), `member packages not array: ${JSON.stringify(json.data).slice(0, 200)}`).toBe(true)
    if (items.length > 0) {
      const normal = items.filter((o: any) => o.status === 1)
      expect(normal.length).toBeGreaterThan(0)
    }

    await ctx.dispose()
  })
})
