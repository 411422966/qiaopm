// 非正式工具（审计调查附件，不进 audit/scripts 正式守门序列，不接 CI）
// 用途：对 C2 改造涉及的代表性非 GET 端点做一次抽样实测，确认「记录不存在」返回 code=404 而非 500。
// 参数：无（硬编码 http://localhost:8080）
// 退出码：0=全部抽样端点均返回 404；1=存在非 404 响应
// 失败处理：写 audit/evidence/F142-c2-not-found/sweep-c2-nonget.json，人工判读
//
// 安全约束（v2，修正 v1 的越界写入事故）：
//   v1 用 id=1548 做"不存在"样本，但 crm_member 里 id=1548 是真实会员，导致 DELETE /member/1548
//   真实软删了该会员（已于 2026-11-02 14:45 用 backup/meiyetec_20261102_140226.sql 快照还原）。
//   v2 改用 id=99999999，并在执行前对每张目标表做存在性预检；命中任一存在行即整体中止（fail-fast），
//   确保抽样只落在「记录不存在」分支，不产生真实写入。
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = 'D:/QiaoPM';
const OUT = ROOT + '/audit/evidence/F142-c2-not-found';
const BASE = 'http://localhost:8080';
const MISSING_ID = 99999999;

// 执行前必须确认「不存在」的表（id 列 = 抽样 ID）
const PRECHECK_TABLES = [
  'crm_member', 'crm_package', 'crm_package_order', 'pos_order',
  'biz_level_benefit', 'mem_level', 'biz_technician_level', 'stock_product',
  'stock_check', 'member_message', 'pos_store', 'sys_tenant'
];

const CASES = [
  { method: 'POST',   url: `/booking/${MISSING_ID}/checkin`,      ctl: 'BookingController',     expect: 404, desc: '预约不存在' },
  { method: 'POST',   url: `/booking/${MISSING_ID}/complete`,     ctl: 'BookingController',     expect: 404, desc: '预约不存在' },
  { method: 'DELETE', url: `/booking/${MISSING_ID}`,              ctl: 'BookingController',     expect: 404, desc: '预约不存在' },
  { method: 'PUT',    url: `/booking/${MISSING_ID}`,              ctl: 'BookingController',     expect: 404, desc: '预约不存在', body: {} },
  { method: 'POST',   url: `/technician/level/benefit/batch/${MISSING_ID}`, ctl: 'LevelBenefitController', expect: 404, desc: '等级不存在或无权访问', body: [] },
  { method: 'PUT',    url: `/technician/level/benefit/${MISSING_ID}`,      ctl: 'LevelBenefitController', expect: 404, desc: '权益不存在或无权访问', body: {} },
  { method: 'DELETE', url: `/technician/level/benefit/${MISSING_ID}`,      ctl: 'LevelBenefitController', expect: 404, desc: '权益不存在或无权访问' },
  { method: 'POST',   url: `/member/${MISSING_ID}/freeze`,        ctl: 'MemberController',      expect: 404, desc: '会员不存在或无权访问' },
  { method: 'POST',   url: `/member/${MISSING_ID}/unfreeze`,      ctl: 'MemberController',      expect: 404, desc: '会员不存在或无权访问' },
  { method: 'POST',   url: `/member/${MISSING_ID}/profile`,       ctl: 'MemberController',      expect: 404, desc: '会员不存在或无权访问', body: {} },
  { method: 'DELETE', url: `/member/${MISSING_ID}`,               ctl: 'MemberController',      expect: 404, desc: '会员不存在或无权访问' },
  { method: 'DELETE', url: `/member-level/${MISSING_ID}`,         ctl: 'MemberLevelController', expect: 404, desc: '等级不存在或无权访问' },
  { method: 'POST',   url: `/member/message/${MISSING_ID}/read`,  ctl: 'MemberMessageController', expect: 404, desc: '消息不存在' },
  { method: 'POST',   url: '/packages/purchase',                 ctl: 'PackageController',     expect: 404, desc: '会员不存在', body: { memberId: MISSING_ID, packageId: MISSING_ID, price: 1 } },
  { method: 'POST',   url: `/packages/order/${MISSING_ID}/refund`, ctl: 'PackageController',   expect: 404, desc: '套餐订单不存在' },
  { method: 'POST',   url: `/packages/order/${MISSING_ID}/extend`, ctl: 'PackageController',   expect: 404, desc: '套餐订单不存在' },
  { method: 'POST',   url: '/packages/upgrade',                  ctl: 'PackageController',     expect: 404, desc: '原套餐订单不存在', body: { fromOrderId: MISSING_ID, toPackageId: MISSING_ID } },
  { method: 'DELETE', url: `/packages/${MISSING_ID}`,             ctl: 'PackageController',     expect: 404, desc: '套餐不存在' },
  { method: 'POST',   url: `/pos/orders/${MISSING_ID}/items`,    ctl: 'PosController',         expect: 404, desc: '订单不存在', body: { itemId: MISSING_ID, quantity: 1 } },
  { method: 'POST',   url: `/pos/orders/${MISSING_ID}/pay`,      ctl: 'PosController',         expect: 404, desc: '订单不存在', body: { method: 'cash', amount: 1, tenderedAmount: 1 } },
  { method: 'POST',   url: `/pos/orders/${MISSING_ID}/void`,     ctl: 'PosController',         expect: 404, desc: '订单不存在' },
  { method: 'POST',   url: `/pos/orders/${MISSING_ID}/refund`,   ctl: 'PosController',         expect: 404, desc: '订单不存在', body: { reason: 'C2 audit probe' } },
  { method: 'POST',   url: `/pos/orders/${MISSING_ID}/discount`, ctl: 'PosController',         expect: 404, desc: '订单不存在', body: { discountAmount: 1 } },
  { method: 'DELETE', url: `/pos/orders/${MISSING_ID}/items/${MISSING_ID}`, ctl: 'PosController', expect: 404, desc: '订单不存在' },
  { method: 'PUT',    url: `/stock/products/${MISSING_ID}`,      ctl: 'StockController',       expect: 404, desc: '商品不存在', body: {} },
  { method: 'DELETE', url: `/stock/products/${MISSING_ID}`,      ctl: 'StockController',       expect: 404, desc: '商品不存在' },
  { method: 'PUT',    url: `/sys/tenant/${MISSING_ID}/status/1`, ctl: 'SysTenantController',  expect: 404, desc: '租户不存在' }
];

// ---- 执行前预检：确认抽样 ID 在所有目标表中都不存在 ----
function precheck() {
  const sql = PRECHECK_TABLES
    .map(t => `SELECT '${t}' AS t, COUNT(*) AS c FROM ${t} WHERE id = ${MISSING_ID};`)
    .join(' ');
  const sqlFile = ROOT + '/.audit-tmp/c2-precheck.sql';
  fs.mkdirSync(ROOT + '/.audit-tmp', { recursive: true });
  fs.writeFileSync(sqlFile, sql, 'utf8');
  execFileSync('docker', ['cp', sqlFile, 'meiyetec-mysql:/tmp/c2pre.sql'], { stdio: 'pipe' });
  const out = execFileSync('docker', ['exec', 'meiyetec-mysql', 'sh', '-c',
    "mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec < /tmp/c2pre.sql 2>/dev/null"], { encoding: 'utf8' });
  const hits = [];
  for (const line of out.split('\n')) {
    const p = line.split('\t');
    if (p.length >= 2 && p[1] && Number(p[1]) > 0) hits.push(p[0] + '=' + p[1]);
  }
  return hits;
}

async function login() {
  const r = await fetch(BASE + '/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' })
  });
  const j = await r.json();
  if (!j?.data?.token) throw new Error('login failed: ' + JSON.stringify(j).slice(0, 200));
  return j.data.token;
}

async function call(c, token) {
  const opt = { method: c.method, headers: { Authorization: `Bearer ${token}` } };
  if (c.body !== undefined) {
    opt.headers['Content-Type'] = 'application/json';
    opt.body = JSON.stringify(c.body);
  }
  const r = await fetch(BASE + c.url, opt);
  const txt = await r.text();
  let bc = '', trace = '', msg = '';
  try { const j = JSON.parse(txt); bc = j.code ?? ''; trace = j.traceId ?? ''; msg = j.message ?? ''; } catch (_) { bc = 'n/a'; }
  return { http: r.status, bc, trace, msg, body: txt.replace(/\s+/g, ' ').slice(0, 200) };
}

(async () => {
  const hits = precheck();
  if (hits.length) {
    console.error('ABORT: 抽样 ID 在以下表中存在真实数据，拒绝执行（避免越界写入）: ' + hits.join(', '));
    process.exit(2);
  }
  console.error('precheck OK: 抽样 ID ' + MISSING_ID + ' 在 ' + PRECHECK_TABLES.length + ' 张目标表中均不存在');

  const token = await login();
  const results = [];
  let bad = 0;
  for (const c of CASES) {
    let r;
    try { r = await call(c, token); }
    catch (err) { r = { http: -1, bc: 'EXC', trace: '', msg: String(err.message), body: '' }; }
    const ok = Number(r.bc) === c.expect;
    if (!ok) bad++;
    results.push({ ...c, ...r, ok });
    console.error((ok ? 'OK  ' : 'BAD ') + c.method + ' ' + c.url + ' -> http=' + r.http + ' code=' + r.bc + ' | ' + r.msg);
    await new Promise(z => setTimeout(z, 80));
  }
  fs.writeFileSync(OUT + '/sweep-c2-nonget.json',
    JSON.stringify({ ts: new Date().toISOString(), base: BASE, missingId: MISSING_ID, total: CASES.length, mismatch: bad, results }, null, 2), 'utf8');
  console.error('done: total=' + CASES.length + ' mismatch=' + bad);
  process.exit(bad === 0 ? 0 : 1);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
