/**
 * QIAOPM-ACC-002 深化：前端路由守卫权限码 × DB sys_permission × 角色绑定 权威交叉
 * 数据源：
 *   - 前端路由表 frontend/meiyetec-web/src/router/index.ts（复刻 vue-router 同名后注册覆盖）
 *   - 运行态菜单 GET /auth/me/menu（菜单码）
 *   - 运行态权限 GET /auth/me/permissions（用户持有码）
 *   - DB sys_permission / sys_role_permission（码是否存在 + 角色绑定）
 */
const fs = require('fs')
const { execFileSync } = require('child_process')

const ROUTER_SRC = 'D:/QiaoPM/frontend/meiyetec-web/src/router/index.ts'
const TMP = 'D:/QiaoPM/.audit-tmp'

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
    out.push({
      path: m[1],
      name: m[2],
      permission: perm ? perm[1] : null,
      title: title ? title[1] : '',
      order: out.length
    })
  }
  return out
}

function mysql(sql) {
  const out = execFileSync(
    'mysql',
    ['-h', '127.0.0.1', '-P', '3307', '-u', 'root', '--default-character-set=utf8mb4', '-N', '-B', 'meiyetec', '-e', sql],
    { env: { ...process.env, MYSQL_PWD: 'root123456' }, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  )
  return out
}

;(async () => {
  const src = fs.readFileSync(ROUTER_SRC, 'utf8')
  const parsed = parseRoutes(src)

  // vue-router: 同 name 后注册覆盖先注册
  const byName = new Map()
  parsed.forEach(r => byName.set(r.name, r))
  const effective = [...byName.values()]

  // 收集所有需要查询的码
  const codes = new Set()
  effective.forEach(r => { if (r.permission) codes.add(r.permission) })
  const dead = JSON.parse(fs.readFileSync(TMP + '/browser-deadlinks.json', 'utf8'))
  dead.forEach(u => u.rows.forEach(r => { if (r.routePermission) codes.add(r.routePermission) }))
  const codeList = [...codes]

  // 分批查询（避开 IN 列表过长）
  const permMeta = new Map()
  for (let i = 0; i < codeList.length; i += 100) {
    const chunk = codeList.slice(i, i + 100).map(c => `'${c.replace(/'/g, "''")}'`).join(',')
    const sql = `SELECT p.permission_code, p.id, IFNULL(p.permission_type,''), IFNULL(p.permission_name,''),
      (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id),
      IFNULL((SELECT GROUP_CONCAT(DISTINCT r.role_code) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE rp.permission_id=p.id),'')
      FROM sys_permission p WHERE p.permission_code IN (${chunk});`
    mysql(sql)
      .split('\n')
      .filter(l => l.trim())
      .forEach(l => {
        const [code, id, type, name, binds, roles] = l.split('\t')
        permMeta.set(code, { id: +id, type, name, binds: +binds, roles: roles || '' })
      })
  }

  // 统计：DB 全部码的体系分布 + 角色绑定分布
  const dist = mysql(`SELECT
      SUM(CASE WHEN permission_code LIKE '%:%' THEN 1 ELSE 0 END) AS colon,
      SUM(CASE WHEN permission_code LIKE '%.%' AND permission_code NOT LIKE '%:%' THEN 1 ELSE 0 END) AS dotted,
      SUM(CASE WHEN permission_code NOT LIKE '%:%' AND permission_code NOT LIKE '%.%' THEN 1 ELSE 0 END) AS bare,
      COUNT(*) AS total FROM sys_permission;`)
  const roleBindStats = mysql(`SELECT
      COUNT(*) AS total_perms,
      SUM(b.cnt=0) AS zero_bind,
      SUM(b.cnt>0) AS has_bind
    FROM sys_permission p
    LEFT JOIN (SELECT permission_id, COUNT(*) cnt FROM sys_role_permission GROUP BY permission_id) b
      ON b.permission_id = p.id;`)
  const roleRows = mysql(`SELECT r.role_code, r.role_name, COUNT(rp.permission_id) FROM sys_role r
    LEFT JOIN sys_role_permission rp ON rp.role_id=r.id GROUP BY r.id ORDER BY r.id;`)

  // 菜单码（DB 绑定）
  const menuCodes = new Set()
  mysql(`SELECT DISTINCT p.permission_code FROM sys_menu_permission mp
    JOIN sys_permission p ON p.id = mp.permission_id
    WHERE p.permission_code IS NOT NULL AND p.permission_code <> '';`)
    .split('\n').filter(l => l.trim()).forEach(l => menuCodes.add(l.trim()))

  // 汇总前端守卫码状态
  const guardCodes = effective.filter(r => r.permission).map(r => r.permission)
  const uniqueGuard = [...new Set(guardCodes)]
  const classify = code => {
    const meta = permMeta.get(code)
    if (!meta) return 'MISSING_IN_DB'
    if (meta.binds === 0) return 'EXISTS_NO_BINDING'
    return 'OK_BOUND'
  }
  const bucket = { MISSING_IN_DB: [], EXISTS_NO_BINDING: [], OK_BOUND: [] }
  uniqueGuard.forEach(c => bucket[classify(c)].push(c))

  const report = {
    routerRouteCount: parsed.length,
    effectiveRouteCount: effective.length,
    duplicateNameOverridden: parsed.length - effective.length,
    uniqueGuardCodes: uniqueGuard.length,
    guardCodeStatus: {
      missingInDb: bucket.MISSING_IN_DB.length,
      existsNoBinding: bucket.EXISTS_NO_BINDING.length,
      okBound: bucket.OK_BOUND.length
    },
    dbDistribution: dist.trim(),
    roleBindStats: roleBindStats.trim(),
    roleTable: roleRows.trim().split('\n'),
    menuCodeCount: menuCodes.size,
    missingInDbList: bucket.MISSING_IN_DB.sort(),
    existsNoBindingList: bucket.EXISTS_NO_BINDING.sort(),
    okBoundList: bucket.OK_BOUND.sort(),
    guardCodesNotInMenu: uniqueGuard.filter(c => !menuCodes.has(c)).length,
    menuCodesNotInGuard: [...menuCodes].filter(c => !uniqueGuard.includes(c)).length
  }

  // 用户维度：持有的码 vs D1 路由码
  const users = []
  for (const u of ['admin', 'boss01', 'clerk01', 'manager01', 'operator01']) {
    const p = JSON.parse(fs.readFileSync(`${TMP}/rtperm-${u}.json`, 'utf8'))
    const held = new Set(p.perms || [])
    const rows = dead.find(x => x.user === u).rows
    const d1 = rows.filter(r => r.kind === 'D1_GUARD_403')
    const d1Held = d1.filter(r => held.has(r.routePermission))
    users.push({
      user: u,
      roles: p.roles,
      heldCount: held.size,
      hasStar: held.has('*'),
      menuItems: rows.length,
      d1: d1.length,
      d2: rows.filter(r => r.kind === 'D2_NO_ROUTE').length,
      ok: rows.filter(r => r.kind === 'OK').length,
      d1HeldDespiteNot403: d1Held.length,
      heldColon: [...held].filter(c => c.includes(':')).sort(),
      heldDotted: [...held].filter(c => !c.includes(':') && c.includes('.')).sort(),
      heldBare: [...held].filter(c => !c.includes(':') && !c.includes('.')).sort()
    })
  }
  report.users = users

  fs.writeFileSync(TMP + '/authz-cross.json', JSON.stringify(report, null, 1))
  console.log(JSON.stringify({
    routerRouteCount: report.routerRouteCount,
    effectiveRouteCount: report.effectiveRouteCount,
    duplicateNameOverridden: report.duplicateNameOverridden,
    uniqueGuardCodes: report.uniqueGuardCodes,
    guardCodeStatus: report.guardCodeStatus,
    dbDistribution: report.dbDistribution,
    roleBindStats: report.roleBindStats,
    menuCodeCount: report.menuCodeCount,
    guardCodesNotInMenu: report.guardCodesNotInMenu,
    menuCodesNotInGuard: report.menuCodesNotInGuard
  }, null, 1))
  console.log('\nroleTable:')
  report.roleTable.forEach(r => console.log('  ', r))
  console.log('\nusers:')
  users.forEach(u =>
    console.log(
      `  ${u.user.padEnd(12)} held=${String(u.heldCount).padStart(3)} star=${u.hasStar ? 'Y' : 'N'} menu=${String(u.menuItems).padStart(2)} D1=${String(u.d1).padStart(2)} D2=${u.d2} OK=${String(u.ok).padStart(2)} heldColon=${u.heldColon.length} heldDotted=${u.heldDotted.length} heldBare=${u.heldBare.length}`
    )
  )
  console.log('\nmissingInDbList (' + report.missingInDbList.length + '):')
  console.log('  ' + report.missingInDbList.join(', '))
  console.log('\nexistsNoBindingList (' + report.existsNoBindingList.length + '):')
  console.log('  ' + report.existsNoBindingList.join(', '))
  console.log('\nokBoundList (' + report.okBoundList.length + '):')
  console.log('  ' + report.okBoundList.join(', '))
})().catch(e => {
  console.error('FATAL', e)
  process.exit(1)
})
