/**
 * 非正式工具（调查用，不属于 audit/scripts 正式守门序列）
 *
 * 补充探测：/biz/service/item/** 写操作端点无 @RequirePermission，
 * 需确认最低权限角色（operator01 / clerk01）能否直接写入 —— 越权验证。
 * 写入目标 ID 使用兜底 99999999（已确认不存在），只验证「是否穿过鉴权进入业务层」。
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'manager01', 'clerk01', 'operator01']
const ABSENT_ID = 99999999

const CASES = [
  { path: `/biz/service/item/${ABSENT_ID}`, method: 'PUT', body: { serviceName: 'audit-probe' }, perm: '无 @RequirePermission' },
  { path: `/biz/service/item/${ABSENT_ID}`, method: 'DELETE', body: {}, perm: '无 @RequirePermission' },
  { path: `/biz/service/item/${ABSENT_ID}/material`, method: 'POST', body: { productId: ABSENT_ID, quantity: 1 }, perm: '无 @RequirePermission' },
  { path: '/biz/service/item/page?pageNum=1&pageSize=1', method: 'GET', body: {}, perm: '无 @RequirePermission' }
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
  const init = { method, headers: { Authorization: `Bearer ${token}` } }
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
  } catch (e) { msg = txt.slice(0, 120) }
  return { status: r.status, code, msg }
}

;(async () => {
  const tokens = {}
  for (const u of USERS) tokens[u] = (await login(u)).token
  const out = { apiBase: API, generatedAt: new Date().toISOString(), probeId: ABSENT_ID, cases: [] }
  for (const c of CASES) {
    const row = { path: c.path, method: c.method, requirePermission: c.perm, results: {} }
    for (const u of USERS) {
      const r = await call(tokens[u], c.path, c.method, c.body)
      const denied = r.status === 403 || r.code === 403
      row.results[u] = { http: r.status, code: r.code, msg: r.msg, verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : 'ERROR' }
    }
    out.cases.push(row)
    console.log(`\n=== ${c.method} ${c.path}  (${c.perm}) ===`)
    for (const u of USERS) {
      const r = row.results[u]
      console.log(`  ${u.padEnd(12)} ${r.verdict.padEnd(10)} HTTP ${r.http} code=${r.code} ${String(r.msg).slice(0, 60)}`)
    }
  }
  fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-leftover-runtime3.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/i132-leftover-runtime3.json')
})().catch(e => { console.error('FATAL', e); process.exit(1) })
