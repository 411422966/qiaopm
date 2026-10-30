// ============================================================
// 非正式工具（调查证据附件），不进入 audit/scripts 正式守门序列，不接 CI
// 用途：用「已在 MySQL 8.0.46 实建的影子库 audit_probe_i118」的 information_schema
//       实际列清单，对比实体 @TableName 字段，判定建表后是否仍会 Unknown column
//       （比静态解析 DDL 更强证据：DDL 已真实执行成功）
// 用法：node audit/evidence/I118-missing-ddl-20261029/probe-db-diff.js
// 退出码：0 = 影子库中实体列全部到位；1 = 仍有缺口
// ============================================================
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const ROOT = 'D:/QiaoPM';
const ENTITY_DIR = path.join(ROOT, 'backend/src/main/java/com/meiyetec/entity');
const PROBE_DB = 'audit_probe_i118';

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

function camelToSnake(s) {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').toLowerCase();
}

function entityColumns(table) {
  for (const f of fs.readdirSync(ENTITY_DIR)) {
    if (!f.endsWith('.java')) continue;
    const src = fs.readFileSync(path.join(ENTITY_DIR, f), 'utf8');
    if (!new RegExp('@TableName\\(\\s*"' + table + '"\\s*\\)').test(src)) continue;
    const cols = [];
    const lines = src.split('\n');
    let pending = null;
    for (const line of lines) {
      const tf = line.match(/@TableField\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/);
      if (tf) pending = tf[1];
      if (/@TableField\s*\(\s*exist\s*=\s*false/.test(line)) pending = '__SKIP__';
      const fm = line.match(/private\s+(?:static\s+|final\s+|transient\s+)*([A-Za-z0-9_<>,.\[\]\s]+?)\s+([a-zA-Z0-9_]+)\s*;/);
      if (!fm) continue;
      if (/\bstatic\b/.test(line)) continue;
      if (/\btransient\b/.test(line)) { pending = null; continue; }  // MyBatis-Plus 不映射 transient
      let col = pending === '__SKIP__' ? null : (pending || camelToSnake(fm[2]));
      pending = null;
      if (col) cols.push(col);
    }
    return { file: f, cols };
  }
  return null;
}

function dbColumns(table) {
  const sql = `SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${PROBE_DB}' AND TABLE_NAME='${table}' ORDER BY ORDINAL_POSITION;`;
  const out = execFileSync('mysql', ['-h','127.0.0.1','-P','3307','-uroot','-proot123456','-N','-B','-e',sql],
    { encoding: 'utf8', stdio: ['ignore','pipe','ignore'] });
  return out.split('\n').map(s => s.trim()).filter(Boolean);
}

const lines = [];
lines.push('# I118 缺表 — 实体字段 vs 影子库实际列（audit_probe_i118）差异比对');
lines.push('# 生成：audit/evidence/I118-missing-ddl-20261029/probe-db-diff.js');
lines.push('# 影子库：由仓库候选 DDL 脚本在 MySQL 8.0.46 实建（部分脚本失败，见 DDL 执行日志）');
lines.push('#');
lines.push('表\t实体文件\t实体列数\t影子库列数\t实体有但影子库缺\t影子库有但实体无\t结论');
lines.push('');

let gap = 0;
for (const t of TABLES) {
  const ent = entityColumns(t);
  const db = dbColumns(t);
  if (!ent) {
    lines.push(`${t}\t<无实体>\t-\t${db.length}\t-\t${db.join(',') || '-'}\tDDL 有表但无实体（纯 SQL 用途）`);
    continue;
  }
  if (!db.length) {
    lines.push(`${t}\t${ent.file}\t${ent.cols.length}\t0\t${ent.cols.join(',')}\t-\t影子库无此表（DDL 未成功建立）`);
    gap++;
    continue;
  }
  const dbSet = new Set(db);
  const entSet = new Set(ent.cols);
  const missing = ent.cols.filter(c => !dbSet.has(c));
  const extra = db.filter(c => !entSet.has(c));
  const verdict = missing.length ? '仍会 Unknown column' : '列齐备';
  if (missing.length) gap++;
  lines.push(`${t}\t${ent.file}\t${ent.cols.length}\t${db.length}\t${missing.join(',') || '-'}\t${extra.join(',') || '-'}\t${verdict}`);
}
lines.push('');
lines.push(`=== 影子库中仍有实体列缺口的表数：${gap} ===`);

fs.writeFileSync(path.join(__dirname, 'probe-db-diff.txt'), lines.join('\n'), 'utf8');
console.log(lines.join('\n'));
process.exit(gap ? 1 : 0);
