/**
 * 非正式工具（调查用，不属于 audit/scripts 正式守门序列）
 *
 * 补充探测：package:add / package:purchase 端点带 @Valid body，
 * 首次探测因参数校验（400）先于权限切面返回，无法判定鉴权结果。
 * 本次传入「合法但指向不存在 ID」的 body（id=99999999 / memberId=99999999 / packageId=99999999），
 * 使请求能通过 @Valid 并进入方法内部，然后在业务层以「不存在」结束 —— 不产生任何写入。
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'manager01', 'clerk01']
const ABSENT_ID = 99999999

const CASES = [
  { path: '/packages', method: 'POST', body: { id: ABSENT_ID, name: 'audit-probe', price: 1 }, perm: 'package:add (更新分支, id 不存在)' },
  { path: '/packages/purchase', method: 'POST', body: { memberId: ABSENT_ID, packageId: ABSENT_ID, price: 1 }, perm: 'package:purchase' },
  { path: '/packages/redeem-split', method: 'POST', body: { orderId: ABSENT_ID, times: 1 }, perm: 'package:redeem' },
  { path: '/packages/order/99999999/extend?days=1', method: 'POST', body: {}, perm: 'package:edit' }
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
  const init = { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
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
  const out = { apiBase: API, generatedAt: new Date().toISOString(), probeId: ABSENT_ID, note: 'body 合法但 ID 不存在，用于穿透 @Valid 校验层判定鉴权', cases: [] }
  for (const c of CASES) {
    const row = { path: c.path, method: c.method, body: c.body, requirePermission: c.perm, results: {} }
    for (const u of USERS) {
      const r = await call(tokens[u], c.path, c.method, c.body)
      const denied = r.status === 403 || r.code === 403
      row.results[u] = { http: r.status, code: r.code, msg: r.msg, verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : 'ERROR' }
    }
    out.cases.push(row)
    console.log(`\n=== ${c.method} ${c.path}  (${c.perm}) ===`)
    for (const u of USERS) {
      const r = row.results[u]
      console.log(`  ${u.padEnd(11)} ${r.verdict.padEnd(10)} HTTP ${r.http} code=${r.code} ${String(r.msg).slice(0, 60)}`)
    }
  }
  fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-leftover-runtime2.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/i132-leftover-runtime2.json')
})().catch(e => { console.error('FATAL', e); process.exit(1) })
