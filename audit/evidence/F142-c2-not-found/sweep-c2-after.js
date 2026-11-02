// 非正式工具（审计调查附件，不进 audit/scripts 正式守门序列，不接 CI）
// 用途：对后端全部 @GetMapping 端点做一次全量 GET 扫描（872 端点，C2 契约修复后复扫）。
// 参数：无（硬编码 http://localhost:8080 与源码目录）
// 退出码：0=正常完成；1=致命错误
// 失败处理：写 audit/evidence/F142-c2-not-found/sweep-c2-after.json，人工判读
// 与 F141 的 sweep-c1-after.js 保持同一 URL 枚举口径，仅输出路径与统计字段不同。
const fs = require('fs');
const path = require('path');

const ROOT = 'D:/QiaoPM';
const SRC = ROOT + '/backend/src/main/java/com/meiyetec';
const OUT = ROOT + '/audit/evidence/F142-c2-not-found';
const BASE = 'http://localhost:8080';

function walk(dir, acc) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, acc);
    else if (e.name.endsWith('.java')) acc.push(p);
  }
  return acc;
}

function classPrefix(src) {
  const m = src.match(/@RequestMapping\(\s*(?:value\s*=\s*)?["']([^"']+)["']/);
  return m ? m[1].replace(/\/+$/, '') : '';
}

function methodPaths(src) {
  const out = [];
  const re = /@GetMapping(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["']\s*\))?/g;
  let m;
  while ((m = re.exec(src))) out.push(m[1] === undefined ? '' : m[1]);
  return out;
}

function fillPath(p) {
  return p.replace(/\{[^}]+\}/g, '1548').replace(/\/{2,}/g, '/');
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

async function call(u, token) {
  const r = await fetch(BASE + u, { method: 'GET', headers: { Authorization: `Bearer ${token}` } });
  const txt = await r.text();
  let bc = '', trace = '', msg = '';
  try { const j = JSON.parse(txt); bc = j.code ?? ''; trace = j.traceId ?? ''; msg = j.message ?? ''; } catch (_) { bc = 'n/a'; }
  return { http: r.status, bc, trace, msg, body: txt.replace(/\s+/g, ' ').slice(0, 200) };
}

(async () => {
  const files = walk(SRC, []);
  const eps = new Map();
  for (const f of files) {
    const src = fs.readFileSync(f, 'utf8');
    if (!src.includes('@GetMapping')) continue;
    const pre = classPrefix(src);
    const cls = path.basename(f, '.java');
    for (const mp of methodPaths(src)) {
      const url = fillPath((pre + (mp ? '/' + mp : '')) || '/');
      if (!eps.has(url)) eps.set(url, { url, cls, file: f.replace(ROOT + '/', '') });
    }
  }
  const list = [...eps.values()].sort((a, b) => a.url.localeCompare(b.url));
  console.error('GET endpoints discovered: ' + list.length);

  const token = await login();
  const results = [];
  const codeDist = {};
  let n500 = 0, n404 = 0;
  for (const e of list) {
    let r;
    try { r = await call(e.url, token); }
    catch (err) { r = { http: -1, bc: 'EXC', trace: '', msg: String(err.message), body: '' }; }
    results.push({ ...e, ...r });
    codeDist[String(r.bc)] = (codeDist[String(r.bc)] || 0) + 1;
    if (r.bc === 500) { n500++; console.error('500 ' + e.url + '  | ' + r.msg); }
    if (r.bc === 404) n404++;
    await new Promise(z => setTimeout(z, 60));
  }
  fs.writeFileSync(OUT + '/sweep-c2-after.json',
    JSON.stringify({ ts: new Date().toISOString(), base: BASE, total: list.length, code500: n500, code404: n404, codeDist, results }, null, 2), 'utf8');
  console.error('done: total=' + list.length + ' code500=' + n500 + ' code404=' + n404);
  console.error('codeDist=' + JSON.stringify(codeDist));
})().catch(e => { console.error('FATAL', e); process.exit(1); });
