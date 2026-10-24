/**
 * QIAOPM-ACC-002 深化验证：三类死链全量归类
 *   D1 菜单可见但守卫拦截 -> 403 页（有权限点体系错配）
 *   D2 菜单可见但路由不存在 -> 跳回 /login（兜底路由命中）
 *   D3 菜单可见但组件缺失 -> 渲染空白（待单独核）
 */
const { chromium } = require('playwright')
const fs = require('fs')
const path = require('path')

const WEB = process.env.WEB_URL || 'http://localhost:8088'
const API = process.env.BASE_URL || 'http://localhost:8080'
const USERS = ['admin', 'boss01', 'clerk01', 'manager01', 'operator01']
const PW = '123456'
const ROUTER_SRC = 'D:/QiaoPM/frontend/meiyetec-web/src/router/index.ts'

function parseRoutes(src) {
  const out = []
  const re = /\{\s*path:\s*'([^']*)'\s*,\s*name:\s*'([^']*)'([\s\S]*?)\}/g
  let m
  while ((m = re.exec(src))) {
    const body = m[3]
    const metaIdx = body.indexOf('meta:')
    if (metaIdx < 0) continue
    const metaBody = body.slice(metaIdx + 5)
    const perm = metaBody.match(/permission:\s*'([^']*)'/)
    const title = metaBody.match(/title:\s*'([^']*)'/)
    const comp = body.match(/import\('@\/([^']+)'\)/)
    out.push({
      path: m[1],
      name: m[2],
      permission: perm ? perm[1] : null,
      title: title ? title[1] : '',
      component: comp ? comp[1] : null
    })
  }
  return out
}

function resolvePath(parent, child) {
  if (child && child.startsWith('/')) return child
  if (!child) return parent
  return `${parent}/${child}`
}

function menuEntries(menus) {
  const out = []
  for (const n of menus || []) {
    const kids = n.children && n.children.length ? n.children : null
    if (kids) {
      kids.forEach(c => out.push({ title: c.title, target: resolvePath(n.path, c.path), code: c.permissionCode, group: n.title }))
    } else {
      out.push({ title: n.title, target: n.path, code: n.permissionCode, group: null })
    }
  }
  return out
}

async function apiLogin(user) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: PW })
  })
  const j = await r.json()
  if (j.code !== 200) throw new Error(`${user} 登录失败`)
  return j.data
}

;(async () => {
  const src = fs.readFileSync(ROUTER_SRC, 'utf8')
  const parsed = parseRoutes(src)
  // vue-router 同名后注册覆盖先注册
  const byName = new Map()
  parsed.forEach(r => byName.set(r.name, r))
  const effective = [...byName.values()]
  const routeByPath = new Map(effective.map(r => [r.path.startsWith('/') ? r.path : '/' + r.path, r]))

  const browser = await chromium.launch({ headless: true })
  const results = []

  for (const user of USERS) {
    const data = await apiLogin(user)
    const entries = menuEntries(data.menus)
    const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
    const page = await ctx.newPage()
    await page.addInitScript(
      ({ token, u, session }) => {
        localStorage.setItem('token', token)
        localStorage.setItem('user', JSON.stringify(u))
        localStorage.setItem('rbac.session.v1', JSON.stringify(session))
      },
      {
        token: data.token,
        u: { id: data.id, username: data.username, realName: data.realName || data.username },
        session: {
          userId: data.id,
          username: data.username,
          tenantId: data.tenantId || null,
          dataScope: data.dataScope || 'STORE',
          storeIds: data.storeIds || [],
          roles: data.roles || [],
          permissions: data.permissions || [],
          menus: data.menus || []
        }
      }
    )

    const rows = []
    for (const c of entries) {
      const route = routeByPath.get(c.target)
      await page.goto(`${WEB}${c.target}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(420)
      const url = new URL(page.url())
      const landed = url.pathname
      let deniedCode = ''
      if (landed === '/403') {
        deniedCode = await page.evaluate(() => {
          const m = document.body.innerText.match(/权限码：(\S+)/)
          return m ? m[1] : ''
        })
      }
      let kind = 'OK'
      if (landed === '/403') kind = 'D1_GUARD_403'
      else if (landed === '/login') kind = 'D2_NO_ROUTE'
      rows.push({
        target: c.target,
        title: c.title,
        menuCode: c.code,
        group: c.group,
        hasRoute: !!route,
        routePermission: route ? route.permission : null,
        component: route ? route.component : null,
        landed,
        kind,
        deniedCode
      })
    }

    const d1 = rows.filter(r => r.kind === 'D1_GUARD_403')
    const d2 = rows.filter(r => r.kind === 'D2_NO_ROUTE')
    results.push({
      user,
      roles: data.roles,
      permCount: (data.permissions || []).length,
      menuItemCount: rows.length,
      d1Count: d1.length,
      d2Count: d2.length,
      okCount: rows.filter(r => r.kind === 'OK').length,
      rows
    })

    console.log(`\n=== ${user} (${JSON.stringify(data.roles)}) 权限码=${(data.permissions || []).length} 菜单项=${rows.length} ===`)
    console.log(`  D1 守卫403=${d1.length}  D2 无路由跳登录=${d2.length}  可进入=${rows.filter(r => r.kind === 'OK').length}`)
    d1.forEach(r => console.log(`      D1 ${r.target.padEnd(26)} ${String(r.title).padEnd(18)} menu=${String(r.menuCode).padEnd(24)} need=${r.deniedCode}`))
    d2.forEach(r => console.log(`      D2 ${r.target.padEnd(26)} ${String(r.title).padEnd(18)} menu=${r.menuCode}`))

    await ctx.close()
  }

  await browser.close()
  fs.writeFileSync('D:/QiaoPM/.audit-tmp/browser-deadlinks.json', JSON.stringify(results, null, 1))
  console.log('\nwritten .audit-tmp/browser-deadlinks.json')
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
