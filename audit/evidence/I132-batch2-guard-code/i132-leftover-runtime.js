/**
 * 非正式工具（调查用，不属于 audit/scripts 正式守门序列）
 *
 * I132 批次 2 遗留项运行时验证（写操作全部使用兜底 ID 99999999，已确认不存在）：
 *   1) package:* 操作码（purchase/redeem/freeze/suspend/refund/transfer）在 MANAGER/BOSS 下的实际结果
 *   2) biz:service-item:edit（别名控制器 /biz/service-item vs 前端实际调用 /biz/service/item）的实际结果
 *   3) stock:list 相关端点（BOM 弹窗前端调 /stock/page）
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'manager01', 'clerk01']
const ABSENT_ID = 99999999

const CASES = [
  // ---- package:* 操作码 ----
  { group: 'package', route: '/package/member-orders', path: '/packages/purchase', method: 'POST', body: { memberId: ABSENT_ID, packageId: ABSENT_ID, quantity: 1 }, perm: 'package:purchase' },
  { group: 'package', route: '/package/member-orders', path: '/packages/redeem', method: 'POST', body: { orderId: ABSENT_ID, times: 1 }, perm: 'package:redeem' },
  { group: 'package', route: '/package/member-orders', path: `/packages/order/${ABSENT_ID}/freeze?freeze=true`, method: 'POST', body: {}, perm: 'package:freeze' },
  { group: 'package', route: '/package/member-orders', path: `/packages/order/${ABSENT_ID}/suspend`, method: 'POST', body: { action: 'SUSPEND', reason: 'audit-probe' }, perm: 'package:suspend' },
  { group: 'package', route: '/package/member-orders', path: `/packages/order/${ABSENT_ID}/resume`, method: 'POST', body: { action: 'RESUME', reason: 'audit-probe' }, perm: 'package:suspend' },
  { group: 'package', route: '/package/member-orders', path: `/packages/order/${ABSENT_ID}/refund`, method: 'POST', body: {}, perm: 'package:refund' },
  { group: 'package', route: '/package/member-orders', path: `/packages/order/${ABSENT_ID}/transfer`, method: 'POST', body: { toMemberId: ABSENT_ID }, perm: 'package:transfer' },
  { group: 'package', route: '/package/list', path: '/packages', method: 'POST', body: { packageName: 'audit-probe' }, perm: 'package:add' },
  { group: 'package', route: '/package/list', path: `/packages/${ABSENT_ID}`, method: 'DELETE', perm: 'package:delete' },

  // ---- biz:service-item:edit（别名控制器带注解 / 真实 service 无注解）----
  { group: 'biz-service-item', route: '/biz/service-item', path: '/biz/service-item', method: 'POST', body: { serviceCode: 'AUDIT_PROBE', serviceName: 'audit-probe' }, perm: 'biz:service-item:edit (别名控制器)' },
  { group: 'biz-service-item', route: '/biz/service-item', path: `/biz/service-item/${ABSENT_ID}`, method: 'PUT', body: { serviceName: 'audit-probe' }, perm: 'biz:service-item:edit (别名控制器)' },
  { group: 'biz-service-item', route: '/biz/service-item', path: '/biz/service/item', method: 'POST', body: { serviceCode: 'AUDIT_PROBE', serviceName: 'audit-probe' }, perm: '无 @RequirePermission (前端实际调用)' },
  { group: 'biz-service-item', route: '/biz/service-item', path: `/biz/service/item/${ABSENT_ID}`, method: 'PUT', body: { serviceName: 'audit-probe' }, perm: '无 @RequirePermission (前端实际调用)' },
  { group: 'biz-service-item', route: '/biz/service-item', path: `/biz/service/item/${ABSENT_ID}`, method: 'DELETE', perm: '无 @RequirePermission (前端实际调用)' },
  { group: 'biz-service-item', route: '/biz/service-item', path: `/biz/service/item/${ABSENT_ID}/material`, method: 'POST', body: { productId: ABSENT_ID, quantity: 1 }, perm: '无 @RequirePermission (前端 BOM 绑定)' },

  // ---- stock:list（BOM 弹窗取耗材列表）----
  { group: 'stock', route: '/biz/service-item', path: '/stock/page?pageNum=1&pageSize=200&status=1', method: 'GET', perm: 'stock:list (前端 /stock/page)' },
  { group: 'stock', route: '/biz/service-item', path: '/stock/products/page?pageNum=1&pageSize=200', method: 'GET', perm: 'stock:product:list' },
  { group: 'stock', route: '/biz/service-item', path: `/stock/consumption/bom/${ABSENT_ID}`, method: 'GET', perm: 'stock:consumption' }
]

async function login(user) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: '123456' })
  })
  const j = await r.json()
  if (j.code !== 200) throw new Error(`${user} 登录失败: ${JSON.stringify(j)}`)
  return j.data
}

async function call(token, path, method, body) {
  const init = { method: method || 'GET', headers: { Authorization: `Bearer ${token}` } }
  if (body && Object.keys(body).length) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  const r = await fetch(`${API}${path}`, init)
  const txt = await r.text()
  let code = null
  let msg = null
  try {
    const j = JSON.parse(txt)
    code = j.code
    msg = j.message
  } catch (e) {
    msg = txt.slice(0, 120)
  }
  return { status: r.status, code, msg }
}

;(async () => {
  const tokens = {}
  for (const u of USERS) {
    const d = await login(u)
    tokens[u] = d.token
  }
  const out = { apiBase: API, generatedAt: new Date().toISOString(), probeId: ABSENT_ID, cases: [] }
  for (const c of CASES) {
    const row = { group: c.group, route: c.route, path: c.path, method: c.method, requirePermission: c.perm, results: {} }
    for (const u of USERS) {
      const r = await call(tokens[u], c.path, c.method, c.body)
      const denied = r.status === 403 || r.code === 403
      row.results[u] = {
        http: r.status,
        code: r.code,
        msg: r.msg,
        verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : r.status === 404 ? 'NOT_FOUND' : 'ERROR'
      }
    }
    out.cases.push(row)
    console.log(`\n=== [${c.group}] ${c.method} ${c.path}  (${c.perm}) ===`)
    for (const u of USERS) {
      const r = row.results[u]
      console.log(`  ${u.padEnd(11)} ${r.verdict.padEnd(10)} HTTP ${r.http} code=${r.code} ${String(r.msg).slice(0, 60)}`)
    }
  }
  fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-leftover-runtime.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/i132-leftover-runtime.json')
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
