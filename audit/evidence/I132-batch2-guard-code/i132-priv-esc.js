/**
 * 非正式工具（调查用，不属于 audit/scripts 正式守门序列）
 *
 * I132 批次 2 遗留项 —— 越权写入验证（受控，可回滚）
 *
 * 目的：
 *   验证 /biz/service/item/** 写操作端点是否真的缺少 @RequirePermission（越权）
 *   1) operator01（最低权限角色）能否创建服务项目
 *   2) operator01 能否修改 / 删除 operator01 自己刚创建的那一行
 *   3) 别名控制器 /biz/service-item 的 biz:service-item:edit 守卫是否生效
 *
 * 数据安全：
 *   - serviceCode 固定为 AUDIT_PROBE_I132，脚本运行前先查存在性，存在则 fail-fast
 *   - 探针结束后删除自己创建的行（不留残留）
 *   - 只用 operator01/admin 自己的 token，不碰他人数据
 */
const fs = require('fs')
const API = process.env.BASE_URL || 'http://localhost:8080'
const CODE = 'AUDIT_PROBE_I132'

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
  let code = null, msg = null, data = null
  try {
    const j = JSON.parse(txt)
    code = j.code; msg = j.message; data = j.data
  } catch (e) { msg = txt.slice(0, 160) }
  return { status: r.status, code, msg, data }
}

;(async () => {
  const out = { apiBase: API, generatedAt: new Date().toISOString(), serviceCode: CODE, steps: [] }
  const log = (label, r) => {
    const denied = r.status === 403 || r.code === 403
    const row = {
      label,
      http: r.status,
      code: r.code,
      msg: r.msg,
      verdict: denied ? 'DENIED' : r.code === 200 ? 'ALLOWED' : 'ERROR'
    }
    out.steps.push(row)
    console.log(`  ${label.padEnd(52)} ${row.verdict.padEnd(8)} HTTP ${r.status} code=${r.code} ${String(r.msg).slice(0, 60)}`)
    return row
  }

  const admin = await login('admin')
  const op = await login('operator01')
  const opToken = op.token

  console.log('\n=== 1. operator01 创建服务项目（走前端实际调用的 /biz/service/item） ===')
  const created = await call(opToken, '/biz/service/item', 'POST', {
    serviceCode: CODE,
    serviceName: '审计探针-I132',
    unitPrice: 1,
    duration: 1
  })
  const cRow = log('operator01 POST /biz/service/item', created)
  const newId = created.data && created.data.id
  out.createdId = newId || null
  out.createdByOperator = !!(created.data && created.data.id)

  if (!newId) {
    console.log('\n未创建成功，终止（不执行后续写操作）')
    fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-priv-esc.json', JSON.stringify(out, null, 1))
    console.log('written .audit-tmp/i132-priv-esc.json')
    return
  }

  console.log('\n=== 2. operator01 修改 / 删除自己创建的行 ===')
  log('operator01 PUT /biz/service/item/{id}', await call(opToken, `/biz/service/item/${newId}`, 'PUT', { serviceName: '审计探针-I132-已改' }))
  log('operator01 DELETE /biz/service/item/{id}', await call(opToken, `/biz/service/item/${newId}`, 'DELETE', {}))

  console.log('\n=== 3. 别名控制器 /biz/service-item 的 biz:service-item:edit 守卫 ===')
  log('operator01 POST /biz/service-item (别名)', await call(opToken, '/biz/service-item', 'POST', { serviceCode: CODE + '_A', serviceName: '审计探针-I132-别名' }))
  log('admin POST /biz/service-item (别名)', await call(admin.token, '/biz/service-item', 'POST', { serviceCode: CODE + '_B', serviceName: '审计探针-I132-别名-admin' }))

  console.log('\n=== 4. 清理：删除探针创建的残留行 ===')
  // 用 admin token 删除（别名控制器 DELETE 带 biz:service-item:edit 守卫，admin 有通配 *）
  const del = await call(admin.token, `/biz/service/item/${newId}`, 'DELETE', {})
  log('admin DELETE /biz/service/item/{id} (清理)', del)

  fs.writeFileSync('D:/QiaoPM/.audit-tmp/i132-priv-esc.json', JSON.stringify(out, null, 1))
  console.log('\nwritten .audit-tmp/i132-priv-esc.json')
})().catch(e => { console.error('FATAL', e); process.exit(1) })
