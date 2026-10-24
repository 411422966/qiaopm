/**
 * QIAOPM-ACC-002 深化：后端 @RequirePermission 端点实测（用已确认存在的端点）
 * 目的：为 I119 产出后端侧运行证据
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'clerk01', 'manager01', 'operator01']

async function login(user) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: '123456' })
  })
  const j = await r.json()
  if (j.code !== 200) throw new Error(`${user} 登录失败`)
  return j.data
}

async function call(token, path) {
  const r = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const txt = await r.text()
  let code = null, msg = null
  try { const j = JSON.parse(txt); code = j.code; msg = j.message } catch (e) { msg = txt.slice(0, 100) }
  return { status: r.status, code, msg }
}

// 已确认存在的端点 + 其源码 @RequirePermission 码
const CASES = [
  { path: '/sys/tenant/list', perm: 'sys:tenant:list', note: 'SysTenantController.list' },
  { path: '/sys/tenant/page', perm: 'sys:tenant:list', note: 'SysTenantController.page' },
  { path: '/booking/list?pageNum=1&pageSize=2', perm: 'booking:list', note: 'BookingController.list' },
  { path: '/mkt/channel/list?pageNum=1&pageSize=2', perm: 'mkt:channel', note: 'MktChannelController.list' },
  { path: '/member/list?pageNum=1&pageSize=2', perm: 'member:list', note: 'MemberController.list' },
  { path: '/sys/log/list?pageNum=1&pageSize=2', perm: 'sys:log:list', note: 'SysLogController.list' }
]

;(async () => {
  const tokens = {}
  const perms = {}
  for (const u of USERS) {
    const d = await login(u)
    tokens[u] = d.token
    perms[u] = d.permissions || []
  }

  const out = { apiBase: API, users: {}, cases: [] }
  for (const u of USERS) {
    out.users[u] = {
      permCount: perms[u].length,
      hasStar: perms[u].includes('*'),
      colonCodes: perms[u].filter(c => c.includes(':')).sort(),
      dottedCount: perms[u].filter(c => !c.includes(':') && c.includes('.')).length
    }
  }

  for (const c of CASES) {
    const row = { path: c.path, requirePermission: c.perm, note: c.note, results: {} }
    for (const u of USERS) {
      const r = await call(tokens[u], c.path)
      const denied = r.status === 403 || r.code === 403
      row.results[u] = {
        http: r.status,
        code: r.code,
        msg: r.msg,
        verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : 'ERROR',
        userHoldsPerm: perms[u].includes(c.perm),
        userHoldsStar: perms[u].includes('*')
      }
    }
    out.cases.push(row)
    console.log(`\n=== ${c.path}  @RequirePermission("${c.perm}") ===`)
    for (const u of USERS) {
      const r = row.results[u]
      console.log(
        `  ${u.padEnd(11)} ${r.verdict.padEnd(8)} HTTP ${r.http} code=${r.code} holdsPerm=${r.userHoldsPerm} star=${r.userHoldsStar}  ${String(r.msg).slice(0, 60)}`
      )
    }
  }

  fs.writeFileSync('D:/QiaoPM/.audit-tmp/api-authz.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/api-authz.json')
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
