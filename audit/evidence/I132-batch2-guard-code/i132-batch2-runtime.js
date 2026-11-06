/**
 * 非正式工具（调查用，不属于 audit/scripts 正式守门序列）
 *
 * I132 假打通修复批次 2 运行时验证：
 *   1) 三角色登录后 permission 列表是否含目标守卫码
 *   2) 7 条路由首屏依赖的 API 是否 200（不是 403）
 *   3) 路由守卫码与 DB sys_permission.permission_code 是否一一对应
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'manager01']

const GUARD_CODES = [
  'market:coupon:list',
  'market:point:list',
  'market:activity:list',
  'package:list',
  'biz:service-item:list',
  'biz.treatment-plan'
]

// 7 条路由首屏实际调用的 API（只读 GET，无副作用）
const API_CASES = [
  { route: '/market/coupon', path: '/mkt/coupon/batch/page', method: 'POST', body: { pageNum: 1, pageSize: 5 }, perm: 'mkt:coupon' },
  { route: '/market/coupon', path: '/mkt/coupon/page?pageNum=1&pageSize=5', method: 'GET', perm: 'mkt:coupon' },
  { route: '/market/points', path: '/mkt/points/rule/list', method: 'GET', perm: 'mkt:points' },
  { route: '/market/activity', path: '/mkt/promotion/page?pageNum=1&pageSize=5', method: 'GET', perm: 'mkt:promotion' },
  { route: '/package/list', path: '/packages?pageNum=1&pageSize=5', method: 'GET', perm: 'package:list' },
  { route: '/package/list', path: '/packages/stats', method: 'GET', perm: 'package:list' },
  { route: '/package/member-orders', path: '/packages/members/recent-with-orders?limit=5', method: 'GET', perm: 'package:list' },
  { route: '/biz/service-item', path: '/biz/service/item/page?pageNum=1&pageSize=5', method: 'GET', perm: '(无 @RequirePermission)' },
  { route: '/biz/service-item', path: '/biz/service/category/list?status=1', method: 'GET', perm: '(无 @RequirePermission)' },
  { route: '/biz/treatment-plan', path: '/biz/service/item/page?pageNum=1&pageSize=5&status=1', method: 'GET', perm: '(无 @RequirePermission)' }
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
  if (body) {
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
  const perms = {}
  for (const u of USERS) {
    const d = await login(u)
    tokens[u] = d.token
    perms[u] = d.permissions || []
  }

  const out = { apiBase: API, generatedAt: new Date().toISOString(), users: {}, guardCodes: {}, cases: [] }

  for (const u of USERS) {
    out.users[u] = {
      permCount: perms[u].length,
      hasStar: perms[u].includes('*'),
      guardCodes: Object.fromEntries(GUARD_CODES.map(c => [c, perms[u].includes(c)]))
    }
    console.log(`\n=== ${u} (permCount=${perms[u].length}, star=${out.users[u].hasStar}) ===`)
    for (const c of GUARD_CODES) {
      console.log(`  guard ${c.padEnd(24)} ${perms[u].includes(c) ? 'HELD' : 'MISSING'}`)
    }
  }

  for (const c of API_CASES) {
    const row = { route: c.route, path: c.path, method: c.method || 'GET', requirePermission: c.perm, results: {} }
    for (const u of USERS) {
      const r = await call(tokens[u], c.path, c.method, c.body)
      const denied = r.status === 403 || r.code === 403
      row.results[u] = {
        http: r.status,
        code: r.code,
        msg: r.msg,
        verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : 'ERROR'
      }
    }
    out.cases.push(row)
    console.log(`\n=== ${c.route} -> ${c.path}  (${c.perm}) ===`)
    for (const u of USERS) {
      const r = row.results[u]
      console.log(`  ${u.padEnd(11)} ${r.verdict.padEnd(8)} HTTP ${r.http} code=${r.code} ${String(r.msg).slice(0, 50)}`)
    }
  }

  fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-batch2-runtime.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/i132-batch2-runtime.json')
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
