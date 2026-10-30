// ============================================================
// 非正式工具（调查证据附件），不进入 audit/scripts 正式守门序列，不接 CI
// 用途：比对实体 @TableName 字段 vs 仓库内全部 DDL（CREATE TABLE + ALTER TABLE ADD COLUMN）
//       找出「建表后仍会 Unknown column」的表
// 修正：v1 有两个解析缺陷 —— (1) 未剥离 SQL `--` 注释与字符串内逗号
//       (2) 未处理 MyBatis-Plus 会忽略 transient 字段（ReflectionKit.getFieldList 过滤 isTransient）
//       (3) 只扫了 12 个脚本，未覆盖 backend/src/main/resources/db/sql 下 171 个脚本与 ALTER 语句
// 用法：node audit/evidence/I118-missing-ddl-20261029/column-diff.js
// 退出码：0 = 无实体列缺口；1 = 存在实体列缺口
// ============================================================
const fs = require('fs');
const path = require('path');
const ROOT = 'D:/QiaoPM';
const ENTITY_DIR = path.join(ROOT, 'backend/src/main/java/com/meiyetec/entity');

// 缺表清单（本轮 38 张）
const TABLES = [
  'birthday_member','birthday_send_log','biz_expiry_handle_log','card_package_template',
  'care_formula','care_formula_apply','care_plan','cloud_trace_log','crm_churn_alert',
  'crm_churn_prediction','dcep_config','dianping_deal','dianping_order','dianping_shop',
  'dianping_sync_log','fiscal_config','lbs_service_order','lbs_technician','ltv_model_registry',
  'ltv_training_run_log','market_point_rule','no_show_model','no_show_prediction','payment_gateway',
  'payment_intent','price_list','price_list_item','price_list_view_log','report_snapshot',
  'risk_event','risk_rule','shared_package','shared_package_invite','shared_package_member',
  'shared_package_usage','skin_profile','wms_stock_transfer','wms_transfer_suggestion'
];

// DDL 来源：后端全部脚本 + docs/audit 全部脚本
function collectSources() {
  const list = [];
  const be = path.join(ROOT, 'backend/src/main/resources/db/sql');
  for (const f of fs.readdirSync(be)) if (f.endsWith('.sql')) list.push('backend/src/main/resources/db/sql/' + f);
  const da = path.join(ROOT, 'docs/audit');
  for (const f of fs.readdirSync(da)) if (f.endsWith('.sql')) list.push('docs/audit/' + f);
  return list;
}
const DDL_SOURCES = collectSources();

function camelToSnake(s) {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').toLowerCase();
}

// ---------- 1. 实体字段 -> 列名 ----------
// MyBatis-Plus ReflectionKit.getFieldList 会剔除 static / transient 字段，
// 因此 transient 字段（如 confirmPassword）不入库，不构成列缺口。
function entityColumns(table) {
  let hit = null;
  for (const f of fs.readdirSync(ENTITY_DIR)) {
    if (!f.endsWith('.java')) continue;
    const src = fs.readFileSync(path.join(ENTITY_DIR, f), 'utf8');
    if (new RegExp('@TableName\\(\\s*"' + table + '"\\s*\\)').test(src)) { hit = { file: f, src }; break; }
  }
  if (!hit) return null;

  const cols = [];
  const lines = hit.src.split('\n');
  let pendingTableField = null;
  for (const line of lines) {
    const tf = line.match(/@TableField\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/);
    if (tf) pendingTableField = tf[1];
    if (/@TableField\s*\(\s*exist\s*=\s*false/.test(line)) pendingTableField = '__SKIP__';
    const fm = line.match(/private\s+(?:static\s+|final\s+|transient\s+)*([A-Za-z0-9_<>,.\[\]\s]+?)\s+([a-zA-Z0-9_]+)\s*;/);
    if (!fm) continue;
    if (/\bstatic\b/.test(line)) continue;
    if (/\btransient\b/.test(line)) { pendingTableField = null; continue; }  // MP 不映射 transient
    let col;
    if (pendingTableField === '__SKIP__') col = null;
    else if (pendingTableField) col = pendingTableField;
    else col = camelToSnake(fm[2]);
    pendingTableField = null;
    if (col) cols.push(col);
  }
  return { file: hit.file, cols };
}

// ---------- 2. 解析 DDL：CREATE TABLE 列 + ALTER TABLE ADD COLUMN ----------
// 修复：剥离 `--` 行注释；按引号感知的顶层逗号切分（字符串内逗号不算分隔符）
function stripSqlComments(src) {
  // 保留字符串字面量内容，仅剥离行注释
  return src.split('\n').map(line => {
    let out = '', inStr = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === "'" || ch === '"' || ch === '`') {
        if (ch === "'" && !inStr) inStr = true;
        else if (ch === "'" && inStr) inStr = false;
        out += ch;
        continue;
      }
      if (!inStr && ch === '-' && line[i + 1] === '-') break;
      out += ch;
    }
    return out;
  }).join('\n');
}

function splitTopLevel(body) {
  const parts = [];
  let depth = 0, buf = '', inStr = false, quote = '';
  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    if (inStr) {
      buf += ch;
      if (ch === '\\') { if (i + 1 < body.length) { buf += body[++i]; } continue; }
      if (ch === quote) inStr = false;
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') { inStr = true; quote = ch; buf += ch; continue; }
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (ch === ',' && depth === 0) { parts.push(buf); buf = ''; continue; }
    buf += ch;
  }
  parts.push(buf);
  return parts;
}

function ddlColumns(table) {
  const found = [];
  for (const rel of DDL_SOURCES) {
    const full = path.join(ROOT, rel);
    if (!fs.existsSync(full)) continue;
    const raw = fs.readFileSync(full, 'utf8');
    const src = stripSqlComments(raw);

    // CREATE TABLE
    const re = new RegExp('CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?' + table + '`?\\s*\\(', 'gi');
    let m;
    while ((m = re.exec(src)) !== null) {
      let depth = 1, i = m.index + m[0].length;
      while (i < src.length && depth > 0) {
        const ch = src[i];
        if (ch === '(') depth++;
        else if (ch === ')') depth--;
        i++;
      }
      const body = src.slice(m.index + m[0].length, i - 1);
      const cols = splitTopLevel(body)
        .map(c => c.trim())
        .filter(c => c && !/^(PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|FOREIGN|CHECK)\b/i.test(c))
        .map(c => (c.match(/^`?([A-Za-z0-9_]+)`?/) || [, null])[1])
        .filter(Boolean);
      found.push({ src: rel, kind: 'CREATE', cols, startLine: raw.slice(0, m.index).split('\n').length });
    }

    // ALTER TABLE ... ADD COLUMN
    const are = new RegExp('ALTER\\s+TABLE\\s+`?' + table + '`?\\s+(.*?);', 'gis');
    let a;
    while ((a = are.exec(src)) !== null) {
      const body = a[1];
      const cols = [];
      const cre = /ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?/gi;
      let c;
      while ((c = cre.exec(body)) !== null) cols.push(c[1]);
      if (cols.length) {
        found.push({ src: rel, kind: 'ALTER', cols, startLine: raw.slice(0, a.index).split('\n').length });
      }
    }
  }
  return found;
}

// ---------- 3. 比对 ----------
const out = [];
out.push('# I118 缺表 — 实体字段 vs 仓库 DDL（CREATE + ALTER）差异比对');
out.push('# 生成：audit/evidence/I118-missing-ddl-20261029/column-diff.js');
out.push('# 扫描 DDL 源：backend/src/main/resources/db/sql/*.sql (' + DDL_SOURCES.filter(s => s.includes('backend')).length +
         ') + docs/audit/*.sql (' + DDL_SOURCES.filter(s => s.includes('docs')).length + ')');
out.push('# transient 字段按 MyBatis-Plus 行为忽略（不映射为列）');
out.push('');
out.push('表\t实体文件\tDDL来源\t类型\tDDL列数\t实体有但DDL缺\tDDL有但实体无');
out.push('');

let gapTables = 0;
const gapDetail = [];
for (const t of TABLES) {
  const ent = entityColumns(t);
  const ddls = ddlColumns(t);
  if (!ent) { out.push(`${t}\t<无实体>\t${ddls.length ? ddls.map(d => d.src).join('|') : '<无DDL>'}\t-\t-\t-\t-`); continue; }
  const entCols = ent.cols;
  const entSet = new Set(entCols);
  if (!ddls.length) {
    out.push(`${t}\t${ent.file}\t<无DDL>\t-\t0\t<全部 ${entCols.length} 列>\t-`);
    gapTables++;
    gapDetail.push(`${t}\t<无DDL>\t${entCols.join(',')}`);
    continue;
  }
  // 合并同一张表在多个脚本里的 CREATE + ALTER 列
  const union = new Map(); // col -> 来源描述
  for (const d of ddls) for (const c of d.cols) if (!union.has(c)) union.set(c, d);
  const uCols = [...union.keys()];
  const uSet = new Set(uCols);
  const missing = entCols.filter(c => !uSet.has(c));
  const extra = uCols.filter(c => !entSet.has(c));

  for (const d of ddls) {
    const dSet = new Set(d.cols);
    const m = entCols.filter(c => !dSet.has(c));
    const e = d.cols.filter(c => !entSet.has(c));
    out.push(`${t}\t${ent.file}\t${d.src}:${d.startLine}\t${d.kind}\t${d.cols.length}\t${m.join(',') || '-'}\t${e.join(',') || '-'}`);
  }
  if (missing.length) {
    gapTables++;
    gapDetail.push(`${t}\t${ent.file}\t缺 ${missing.join(',')}`);
  }
  out.push(`  >> ${t} 合并后（${ddls.length} 个 DDL 片段）: 实体 ${entCols.length} 列 / DDL ${uCols.length} 列 | 缺=${missing.join(',') || '-'} | 多=${extra.join(',') || '-'}`);
  out.push('');
}
out.push('=== 存在实体列缺口的表数：' + gapTables + ' ===');
if (gapDetail.length) { out.push(''); out.push('--- 缺口明细 ---'); gapDetail.forEach(x => out.push(x)); }

fs.writeFileSync(path.join(__dirname, 'column-diff.txt'), out.join('\n'), 'utf8');
console.log(out.join('\n'));
process.exit(gapTables ? 1 : 0);
