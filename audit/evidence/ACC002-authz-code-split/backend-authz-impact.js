/**
 * I119 补充：当前源码(HEAD)的 @RequirePermission 码集合 × DB 角色绑定
 * 目的：评估「按源码重建镜像后」后端会放行/拦截多少
 */
const fs = require('fs')
const { execFileSync } = require('child_process')

function mysql(sql) {
  return execFileSync(
    'mysql',
    ['-h', '127.0.0.1', '-P', '3307', '-u', 'root', '--default-character-set=utf8mb4', '-N', '-B', 'meiyetec', '-e', sql],
    { env: { ...process.env, MYSQL_PWD: 'root123456' }, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  )
}

const CTRL_DIR = 'D:/QiaoPM/backend/src/main/java/com/meiyetec/controller'

// 收集源码中所有 @RequirePermission 的码 + 所在文件
const codes = new Map()
for (const f of fs.readdirSync(CTRL_DIR)) {
  if (!f.endsWith('.java')) continue
  const txt = fs.readFileSync(CTRL_DIR + '/' + f, 'utf8')
  const re = /@RequirePermission\(\s*"([^"]+)"\s*\)/g
  let m
  while ((m = re.exec(txt))) {
    const c = m[1]
    if (!codes.has(c)) codes.set(c, new Set())
    codes.get(c).add(f)
  }
}

const list = [...codes.keys()]
const meta = new Map()
for (let i = 0; i < list.length; i += 100) {
  const chunk = list.slice(i, i + 100).map(c => `'${c.replace(/'/g, "''")}'`).join(',')
  mysql(
    `SELECT p.permission_code, p.id, IFNULL(p.permission_type,''),
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id),
       IFNULL((SELECT GROUP_CONCAT(DISTINCT r.role_code) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE rp.permission_id=p.id),'')
     FROM sys_permission p WHERE p.permission_code IN (${chunk});`
  )
    .split('\n')
    .filter(l => l.trim())
    .forEach(l => {
      const [code, id, type, binds, roles] = l.split('\t')
      meta.set(code, { id: +id, type, binds: +binds, roles: roles || '' })
    })
}

const missing = []
const noBind = []
const bound = []
list.forEach(c => {
  const m = meta.get(c)
  if (!m) missing.push(c)
  else if (m.binds === 0) noBind.push(c)
  else bound.push(c)
})

console.log('源码 @RequirePermission 唯一码数:', list.length)
console.log('  DB 中不存在:', missing.length)
console.log('  DB 中存在但零角色绑定:', noBind.length)
console.log('  DB 中存在且有绑定:', bound.length)

// 受影响端点数量
let affectedEndpoints = 0
for (const [c, files] of codes) {
  const m = meta.get(c)
  if (!m || m.binds === 0) affectedEndpoints += files.size
}
console.log('\n按当前源码重建后，非超管将被拒的控制器文件数(按文件去重近似):', affectedEndpoints)

console.log('\n--- 零绑定明细 (码 -> 控制器) ---')
noBind.sort().forEach(c => console.log('  ', c.padEnd(34), [...codes.get(c)].join(',')))

console.log('\n--- DB 中不存在的码 ---')
missing.sort().forEach(c => console.log('  ', c.padEnd(34), [...codes.get(c)].join(',')))

fs.writeFileSync(
  'D:/QiaoPM/.audit-tmp/backend-authz-impact.json',
  JSON.stringify(
    {
      uniqueCodes: list.length,
      missingInDb: missing,
      existsNoBinding: noBind,
      okBound: bound,
      detail: list.map(c => ({ code: c, status: !meta.get(c) ? 'MISSING' : meta.get(c).binds === 0 ? 'NO_BINDING' : 'BOUND', roles: meta.get(c) ? meta.get(c).roles : null, controllers: [...codes.get(c)] }))
    },
    null,
    1
  )
)
console.log('\nwritten .audit-tmp/backend-authz-impact.json')
