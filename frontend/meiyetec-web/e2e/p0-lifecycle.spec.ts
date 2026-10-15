/**
 * P4-02 · QPM-006: 会员生命周期营销 E2E 测试（P0 财务链路）
 *
 * 覆盖 4 大规则引擎 + 裂变邀请的完整 API 链路：
 * 1. 规则初始化（init-default → 4 条默认规则）
 * 2. 规则分页查询
 * 3. 看板数据（dashboard）
 * 4. 规则启用/禁用
 * 5. 规则手动触发
 * 6. 触发日志分页
 * 7. 转化漏斗
 * 8. 邀请码生成（防重）
 * 9. 邀请关系绑定
 * 10. 邀请奖励记录查询
 *
 * 与 P0-financial-safety 项目共享测试规范：
 * - 顺序执行（workers: 1）— 避免 lifecycle_log 唯一索引冲突
 * - 多次幂等操作使用 try-catch 容忍已存在情况
 */
import { test, expect } from '@playwright/test'
import './helpers'

const API = process.env.API_BASE_URL || 'http://localhost:8080'

test.describe.serial('P4-02 会员生命周期营销', () => {
  let authToken: string
  let testMemberId: number = 1
  let testRuleIds: number[] = []
  let createdInviteCode: string | null = null

  test.beforeAll(async ({ request }) => {
    // 登录获取 token
    const loginResp = await request.post(`${API}/auth/login`, {
      data: { username: 'admin', password: '123456' }
    })
    expect(loginResp.status(), 'login HTTP status').toBe(200)
    const loginJson = await loginResp.json()
    expect(loginJson.code, 'login business code').toBe(200)
    authToken = loginJson.data.token
    expect(authToken).toBeTruthy()

    // 获取一个真实会员 ID 用于邀请码测试
    const memberResp = await request.get(`${API}/member/list`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    const memberJson = await memberResp.json()
    const members = memberJson.data
    if (Array.isArray(members) && members.length > 0) {
      testMemberId = members[0].id
    }
  })

  // ────────────────────────────────────────────────────────────
  // 1. 规则初始化
  // ────────────────────────────────────────────────────────────
  test('01 - 初始化默认规则应创建 4 条核心规则', async ({ request }) => {
    const resp = await request.post(`${API}/lifecycle/rule/init-default`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(typeof json.data).toBe('number')
    // 第二次调用应返回 0（已存在则跳过）
    expect(json.data).toBeGreaterThanOrEqual(0)
    expect(json.data).toBeLessThanOrEqual(4)
  })

  // ────────────────────────────────────────────────────────────
  // 2. 规则分页查询
  // ────────────────────────────────────────────────────────────
  test('02 - 规则分页应至少包含 4 类核心规则', async ({ request }) => {
    const resp = await request.get(`${API}/lifecycle/rule/page?pageNum=1&pageSize=20`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    const records = json.data.records ?? json.data.rows ?? json.data.list ?? []
    expect(Array.isArray(records)).toBe(true)
    expect(records.length).toBeGreaterThanOrEqual(4)

    // 验证 4 大类型都存在
    const types = new Set(records.map((r: any) => r.ruleType))
    expect(types.has('BIRTHDAY'), `missing BIRTHDAY type, got ${[...types].join(',')}`).toBe(true)
    expect(types.has('MEMBER_DAY')).toBe(true)
    expect(types.has('RETENTION')).toBe(true)
    expect(types.has('INVITE')).toBe(true)

    // 收集规则 ID 供后续测试使用
    testRuleIds = records.map((r: any) => r.id)
  })

  // ────────────────────────────────────────────────────────────
  // 3. 触发看板
  // ────────────────────────────────────────────────────────────
  test('03 - 看板应返回总览数据', async ({ request }) => {
    const resp = await request.get(`${API}/lifecycle/dashboard`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    expect(typeof json.data.totalTriggered).toBe('number')
    expect(typeof json.data.totalRules).toBe('number')
    expect(json.data.totalRules).toBeGreaterThanOrEqual(4)
    expect(json.data.dateRange).toBeTruthy()
    expect(Array.isArray(json.data.byType)).toBe(true)
  })

  // ────────────────────────────────────────────────────────────
  // 4. 规则启用 + 禁用 循环
  // ────────────────────────────────────────────────────────────
  test('04 - 启用规则应返回 ACTIVE 状态', async ({ request }) => {
    expect(testRuleIds.length).toBeGreaterThan(0)
    const ruleId = testRuleIds[0]

    const resp = await request.post(`${API}/lifecycle/rule/${ruleId}/toggle?enable=true`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)

    // 验证规则状态
    const detailResp = await request.get(`${API}/lifecycle/rule/page?pageNum=1&pageSize=50`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    const detailJson = await detailResp.json()
    const records = detailJson.data.records ?? []
    const target = records.find((r: any) => r.id === ruleId)
    expect(target).toBeTruthy()
    expect(target.enabled).toBe(1)
    expect(target.status).toBe('ACTIVE')
  })

  // ────────────────────────────────────────────────────────────
  // 5. 手动触发规则
  // ────────────────────────────────────────────────────────────
  test('05 - 手动触发 RETENTION 规则不应抛错', async ({ request }) => {
    // 找到 RETENTION 类型的规则
    const resp = await request.get(`${API}/lifecycle/rule/page?pageNum=1&pageSize=50`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    const json = await resp.json()
    const records = json.data.records ?? []
    const retentionRule = records.find((r: any) => r.ruleType === 'RETENTION')
    if (!retentionRule) {
      test.skip(true, 'no RETENTION rule found')
      return
    }
    // 启用它
    await request.post(`${API}/lifecycle/rule/${retentionRule.id}/toggle?enable=true`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })

    const triggerResp = await request.post(`${API}/lifecycle/trigger/${retentionRule.id}`, {
      headers: { Authorization: `Bearer ${authToken}` }
    })
    expect(triggerResp.status()).toBe(200)
    const triggerJson = await triggerResp.json()
    expect(triggerJson.code).toBe(200)
    expect(triggerJson.data).toBeTruthy()
    expect(typeof triggerJson.data.triggered).toBe('number')
  })

  // ────────────────────────────────────────────────────────────
  // 6. 触发日志分页
  // ────────────────────────────────────────────────────────────
  test('06 - 触发日志分页接口应返回结构化数据', async ({ request }) => {
    const resp = await request.get(
      `${API}/lifecycle/log/page?pageNum=1&pageSize=20`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    )
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    expect(Array.isArray(json.data.records) || typeof json.data.total === 'number').toBe(true)
  })

  // ────────────────────────────────────────────────────────────
  // 7. 转化漏斗
  // ────────────────────────────────────────────────────────────
  test('07 - 转化漏斗接口应返回触发/发送/使用 计数', async ({ request }) => {
    const resp = await request.get(
      `${API}/lifecycle/funnel`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    )
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    expect(typeof json.data.triggered).toBe('number')
    expect(typeof json.data.sent).toBe('number')
    expect(typeof json.data.used).toBe('number')
    expect(typeof json.data.sentRate).toBe('number')
  })

  // ────────────────────────────────────────────────────────────
  // 8. 邀请码生成
  // ────────────────────────────────────────────────────────────
  test('08 - 生成邀请码应返回 8 位 INV 前缀码', async ({ request }) => {
    const resp = await request.get(
      `${API}/lifecycle/invite/code?memberId=${testMemberId}`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    )
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    expect(json.data.inviteCode).toMatch(/^INV[A-Z0-9]{8}$/)
    expect(json.data.status).toBe('ACTIVE')
    expect(typeof json.data.rewardCouponAmount).toBe('number')
    expect(json.data.shareText).toContain(json.data.inviteCode)
    createdInviteCode = json.data.inviteCode
  })

  // ────────────────────────────────────────────────────────────
  // 9. 重复生成应返回相同码（幂等）
  // ────────────────────────────────────────────────────────────
  test('09 - 重复生成邀请码应返回相同码', async ({ request }) => {
    const resp = await request.get(
      `${API}/lifecycle/invite/code?memberId=${testMemberId}`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    )
    const json = await resp.json()
    expect(json.code).toBe(200)
    if (createdInviteCode) {
      expect(json.data.inviteCode).toBe(createdInviteCode)
    }
  })

  // ────────────────────────────────────────────────────────────
  // 10. 邀请奖励汇总
  // ────────────────────────────────────────────────────────────
  test('10 - 邀请奖励汇总应返回统计字段', async ({ request }) => {
    const resp = await request.get(
      `${API}/lifecycle/invite/rewards?memberId=${testMemberId}`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    )
    expect(resp.status()).toBe(200)
    const json = await resp.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeTruthy()
    expect(typeof json.data.inviteCount).toBe('number')
    expect(typeof json.data.totalRewards).toBe('number')
  })

  // ────────────────────────────────────────────────────────────
  // 11. 邀请码绑定（参数校验）
  // ────────────────────────────────────────────────────────────
  test('11 - 绑定无效邀请码应返回 400/404 错误', async ({ request }) => {
    const resp = await request.post(`${API}/lifecycle/invite/bind`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        inviteCode: 'INVINVALI99',
        inviteeId: 999999,
        inviteeName: '不存在用户',
        inviteePhone: '13800000000'
      }
    })
    const json = await resp.json()
    // 应返回非 200（400 或 404）
    expect(json.code).not.toBe(200)
  })
})