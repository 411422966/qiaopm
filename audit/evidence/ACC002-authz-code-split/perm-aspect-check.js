/**
 * QIAOPM-ACC-002 深化：后端 @RequirePermission 是否真的生效
 * 取样：明确带 @RequirePermission(冒号码) 且该码在 DB 中绑定数为 0 的端点
 * 预期：非授权角色应 403（PermissionAspect 抛 PERMISSION_DENIED）
 */
const API = process.env.BASE_URL || 'http://localhost:8080'

async function login(user) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: '123456' })
  })
  const j = await r.json()
  return j.data
}

async function raw(token, path) {
  const r = await fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${token}` } })
  const txt = await r.text()
  return { status: r.status, body: txt.slice(0, 300) }
}

;(async () => {
  const USERS = ['admin', 'boss01', 'clerk01', 'manager01', 'operator01']
  const tokens = {}
  for (const u of USERS) tokens[u] = (await login(u)).token

  // 逐个端点单独验证，打印原始 body
  const PATHS = [
    '/sys/tenant/list',       // @RequirePermission("sys:tenant:list") 绑定数=0
    '/sys/tenant/page',       // 同上
    '/sys/tenant/1',          // 同上
    '/booking/list?pageNum=1&pageSize=2',   // booking:list 绑定数=0
    '/mkt/channel/list?pageNum=1&pageSize=2', // mkt:channel 绑定数=0
    '/member/list?pageNum=1&pageSize=2'      // member:list 有绑定
  ]

  for (const p of PATHS) {
    console.log(`\n=== ${p} ===`)
    for (const u of USERS) {
      const r = await raw(tokens[u], p)
      console.log(`  ${u.padEnd(11)} HTTP ${r.status}  ${r.body}`)
    }
  }
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
