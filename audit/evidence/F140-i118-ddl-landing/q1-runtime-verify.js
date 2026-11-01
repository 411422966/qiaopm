// 非正式工具（审计调查附件）：不进 audit/scripts 正式守门序列，不接 CI
// 用途：对 Q1 修复涉及的 5 个端点做定向运行时复测，记录 HTTP/code/traceId/响应体。
// 参数：BASE 环境变量（默认 http://localhost:8081）
// 退出码：0=全部端点已探测完成；1=登录失败（无法取 token）
// 失败处理：把每条结果写入 q1-runtime-verify.json，人工判读；脚本本身不做断言。
const fs = require('fs');
const BASE = process.env.BASE || 'http://localhost:8081';
const OUT = 'D:/QiaoPM/audit/evidence/F140-i118-ddl-landing/q1-runtime-verify.json';

const TARGETS = [
  { name: 'Q1-promote-evaluate', url: '/technician/level/promote/evaluate?employeeId=1' },
  { name: 'Q1-level-eval-list', url: '/technician/level/evaluation/list?employeeId=1' },
  { name: 'Q1-level-eval-metrics', url: '/technician/level/evaluation/metrics/1' },
  { name: 'Q1-member-credit', url: '/biz/booking/member-credit?memberId=1' },
  { name: 'CHECK-conflict', url: '/biz/booking/resource/check-conflict?resourceId=1&bookingDate=2026-10-31&startTime=10:00:00&endTime=12:00:00' },
  { name: 'CHECK-sync-member-list', url: '/sync/member/list?sessionKey=probe-20261101' },
];

async function login() {
  const r = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  });
  const j = await r.json();
  if (!j || !j.data || !j.data.token) throw new Error('login failed: ' + JSON.stringify(j).slice(0, 200));
  return j.data.token;
}

(async () => {
  const token = await login();
  const results = [];
  for (const t of TARGETS) {
    const rec = { name: t.name, url: t.url, http: null, code: null, msg: null, trace: null, body: null };
    try {
      const r = await fetch(BASE + t.url, { method: 'GET', headers: { Authorization: `Bearer ${token}` } });
      const txt = await r.text();
      rec.http = r.status;
      try {
        const j = JSON.parse(txt);
        rec.code = j.code;
        rec.msg = j.message;
        rec.trace = j.traceId;
        rec.body = JSON.stringify(j.data).slice(0, 900);
      } catch (_) {
        rec.body = txt.replace(/\s+/g, ' ').slice(0, 300);
      }
    } catch (e) {
      rec.code = 'EXC';
      rec.body = String(e.message);
    }
    results.push(rec);
    console.log(rec.name + '\tHTTP=' + rec.http + '\tcode=' + rec.code + '\t' + (rec.msg || ''));
  }
  fs.writeFileSync(OUT, JSON.stringify({ ts: new Date().toISOString(), base: BASE, results }, null, 2), 'utf8');
  console.log('written: ' + OUT);
})().catch((e) => { console.error('FATAL', e.message); process.exit(1); });
