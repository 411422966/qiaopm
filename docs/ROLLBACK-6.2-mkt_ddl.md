# 6.2 数据迁移脚本 + 双写期回滚预案

> 任务: T08-Q1 决策 **A（确认无数据 → 直接归档 + DROP）** 后, 编写 SQL 迁移脚本与回滚预案
> 日期: 2026-09-10
> 负责人: DBA + 后端
> 关联脚本:
> - `backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql`（主动作）
> - `backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql`（回滚）
> - 关联前置: `migrate_mkt_001_add_tenant.sql`（已加 tenant_id）

---

## 1. 决策依据

| 维度 | 结论 |
|---|---|
| 生产 `market_activity` 行数 | **0 行**（DBA 2026-09-10 核对） |
| Java 端引用 | 无（`BusinessPolicyDispatchServiceImpl` 已注释说明） |
| Mapper/Entity | 已移除（任务 6 治理） |
| 替代表 | `mkt_promotion`（30+ 列展开式） |
| 风险等级 | **低** |

> 选项 C（探测）也印证了 0 行；选项 B（90 天观察）不必要。

---

## 2. 主动作（migrate_mkt_002_drop_market_activity.sql）

### 执行逻辑

```sql
1. 探测 market_activity 是否存在
   ├── 不存在 → 静默跳过（DBA 视图: 生产已无此表，正常路径）
   └── 存在 ↓
2. SELECT COUNT(*) FROM market_activity
   ├── > 0  → SIGNAL '45000'（异常: 触发 DBA 人工介入 + 选项 B）
   └── = 0  ↓
3. RENAME TABLE market_activity TO market_activity__archived_20260910
4. DROP TABLE market_activity__archived_20260910  -- 立即归档+清理
```

### 关键设计

| 设计点 | 说明 |
|---|---|
| 探测 + 防御性分支 | 即使生产误判, 也避免误删有数据的表 |
| 重命名 + DROP 一并 | 选项 A 精神: 确认无数据 → 直接清理, 不留 90 天 |
| 不改 mkt_promotion | 该表 DDL 已在 `init_mkt_promotion.sql` 完整, 与本脚本无关 |
| 不写 Flyway 版本 | 历史项目无 Flyway, 由 DBA 手工 `mysql <` 执行 |

---

## 3. 回滚预案（migrate_mkt_002_rollback.sql）

### 触发条件（90 天观察窗口内 2026-09-10 → 2026-12-09）

1. **下游分析系统** 误读 `market_activity`（旧 BI 报表依赖）
2. **生产排查** 发现遗漏的 SQL 引用（如某个 cron job）
3. **业务部门** 反馈旧活动数据被审计/合规需要

### 触发步骤

```bash
# 1. 停服
systemctl stop meiyetec-api

# 2. 执行回滚
mysql -h <host> -u root -p meiyetec < migrate_mkt_002_rollback.sql

# 3. 验证
mysql -h <host> -u root -p meiyetec -e "SELECT * FROM market_activity LIMIT 10;"

# 4. 启服
systemctl start meiyetec-api
```

### 回滚 SQL 逻辑

```sql
1. 探测归档表是否存在
   ├── 不存在 → SIGNAL '45000'（无法回滚, 需从备份恢复）
   └── 存在 ↓
2. DROP TABLE IF EXISTS market_activity;  -- 清理可能残留
3. RENAME TABLE market_activity__archived_20260910 TO market_activity;
```

### ⚠️ 当前限制（已确认 0 行, DROP 后归档表不存在）

**重要**: 选项 A 路径下，**归档表已被立即 DROP**, 因此本回滚脚本在生产首次执行后会失效。
真正可用的回滚手段是：

- **生产备份**: 由 DBA 在执行 DROP 前 30 分钟内做 `mysqldump --single-transaction` 全库备份
- **binlog 回滚**: 通过 `mysqlbinlog --start-datetime='2026-09-10 23:00:00' --stop-datetime='2026-09-10 23:30:00'` 提取 DROP 前的 binlog
- **逻辑备份 → 表级恢复**: `mysqldump` 全库备份 + `mysql` 重放

> 决策记录: 因生产已确认 0 行, 90 天回滚窗口价值低, 改为「立即 DROP + 依赖备份回滚」。

---

## 4. 双写期（本任务无）

本任务为**纯 DROP**, 不涉及双写期。
`mkt_promotion` 自 2026-08-27 上线后, 业务写入已全部走新表。
若 90 天后发现遗漏的旧写入, 应通过 binlog + 备份恢复, 而非「双写」。

---

## 5. 验证步骤

### 部署前（DBA 执行前）

```bash
# 1. 全库备份
mysqldump -h <host> -u root -p meiyetec \
  --single-transaction --routines --triggers \
  > /backup/meiyetec_pre_mkt_002_$(date +%Y%m%d).sql

# 2. 验证备份大小
ls -lh /backup/meiyetec_pre_mkt_002_*.sql
```

### 执行

```bash
mysql -h <host> -u root -p meiyetec \
  < backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql
```

### 执行后验证

```sql
-- 1. 表已删除
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('market_activity', 'market_activity__archived_20260910');
-- 期望: 0

-- 2. 业务表 mkt_promotion 不受影响
SELECT COUNT(*) FROM mkt_promotion;
-- 期望: 与执行前一致
```

### 应用侧验证

```bash
# 启服后, 跑营销相关冒烟
curl http://localhost:8080/mkt/promotion/list  # 应返回 mkt_promotion 数据
curl http://localhost:8080/mkt/dispatch/list  # 应返回 policy dispatch 数据
```

---

## 6. 变更文件清单

| 文件 | 改动 | 说明 |
|---|---|---|
| `migrate_mkt_002_drop_market_activity.sql` | 新建 | 主动作（探测 + 重命名 + DROP） |
| `migrate_mkt_002_rollback.sql` | 新建 | 回滚预案（依赖归档表存在） |
| `docs/ROLLBACK-6.2-mkt_ddl.md` | 新建 | 本文档 |

---

## 7. 风险与缓解

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| 生产误判有数据 | 低 | 防御性 COUNT(*) 探测 + SIGNAL 异常分支 |
| DROP 后下游系统报错 | 低 | 上线前做 grep 全代码库 `market_activity` 引用核查（已做: 0 处业务代码） |
| 归档表占用空间 | 无 | 立即 DROP, 不留归档 |
| 回滚失败 | 中 | 依赖 mysqldump + binlog 全库恢复 |

---

## 8. 上线 checklist

- [ ] DBA 在执行前完成 mysqldump 全库备份
- [ ] DBA 二次确认 `SELECT COUNT(*) FROM market_activity;` 为 0
- [ ] DBA 执行 `migrate_mkt_002_drop_market_activity.sql`
- [ ] 验证表已删除（见第 5 节）
- [ ] 应用层启动, 跑 mkt 冒烟测试
- [ ] 在 release notes 中记录本迁移

---

*文档版本: 1.0 · 2026-09-10*
*下次审查: 90 天后 (2026-12-09) 或下一次 mkt 模块重构前*
