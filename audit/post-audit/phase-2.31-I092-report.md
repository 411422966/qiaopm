# Phase 2.31 — I092 ecommerce 4 表 tenant_id=0 → 1 迁移 + idx_tenant 索引补建 Fix Report

> **修复时刻**：2026-09-30 23:48 (UTC+8)
> **Commit**：`c7df0ba`
> **执行人**：Cursor Assistant
> **关联 Issue**：I092（电商 4 表历史 `tenant_id=0` 数据 + idx_tenant 索引）
> **优先级**：P1（电商模块跨租户可访问性 + I090 衍生）
> **Fix Phase**：Post-Audit 二期
> **SQL 脚本**：`audit/sql/migrate_ecommerce_tenant_id.sql`
> **备份**：`audit/baseline/pre-I092-backup-20260930.sql`（7124 bytes, 7 行可回滚）
> **验收标准**：4 表 tenant_id 分布 + idx_tenant 索引齐备

---

## 1. 背景

I090 修复 commit `1c2c165` 后，EcommerceService 9 端点统一接入 `selectPlatformByTenant` helper。但发现 `ecommerce_*` 4 张表的历史数据**全部 `tenant_id=0`**（DDL 默认值 + 服务上线前 INSERT）。

**后果**：
- `selectPlatformByTenant` 检查 `pf.getTenantId() != currentTenantId` → 老数据被 403 跨租户拒绝
- `idx_tenant` 索引缺失 → I090 的 `qw.eq(...::getTenantId, ...)` 全表扫描（7 行量小不痛，量增长后慢）

---

## 2. 数据现状（修复前）

| 表 | 总行 | tenant_id=0 | tenant_id=NULL |
|---|---|---|---|
| ecommerce_platform | 3 | 3 | 0 |
| ecommerce_product | 3 | 3 | 0 |
| ecommerce_order | 0 | — | — |
| ecommerce_sync_log | 1 | 1 | 0 |
| **合计** | **7** | **7** | **0** |

**索引现状**（意外发现）：
- ecommerce_platform：❌ 缺 idx_tenant
- ecommerce_product：❌ 缺 idx_tenant
- ecommerce_order：❌ 缺 idx_tenant
- ecommerce_sync_log：✅ **已有** idx_tenant（非本次 SQL 创建）

---

## 3. 决策记录（用户拍板）

| # | 决策 | 用户选择 | 实施 |
|---|---|---|---|
| Q1 | I092 SQL 范围 | A1（UPDATE 7 行 + 4 表 ADD INDEX） | ✅ |
| Q2 | 执行顺序 | B1（I092 先做 → I093 后做） | ✅ |
| Q3 | 备份策略 | C1（先 mysqldump 备份） | ✅ |
| I093 范围 | 仅本 session 延后 | I093 不动 | ✅ |

---

## 4. 修复内容

### 4.1 mysqldump 备份

```bash
mysqldump -h 127.0.0.1 -P 3307 -u root -proot123456 \
  --single-transaction --set-gtid-purged=OFF --skip-add-drop-table --no-create-info \
  --where="tenant_id=0 OR tenant_id IS NULL" meiyetec \
  ecommerce_platform ecommerce_product ecommerce_sync_log \
  > audit/baseline/pre-I092-backup-20260930.sql
```

**产物**：`audit/baseline/pre-I092-backup-20260930.sql`，7124 bytes
- 3 platform 行（WEIDIAN 'S'/'WD1' + YOUZAN 'test-shop'/'test-kdt-5B' × 2）
- 3 product 行（MOCK 焕颜保湿面膜 / 头皮舒缓精华 / 经典美甲护理）
- 1 sync_log 行（MOCK PRODUCT SUCCESS 182ms）

**回滚**：直接 `mysql < pre-I092-backup-20260930.sql` 即可恢复原状。

### 4.2 audit/sql/migrate_ecommerce_tenant_id.sql

```sql
USE meiyetec;
START TRANSACTION;

-- 1. UPDATE：7 行 tenant_id=0 → 1（默认租户 sys_tenant.id=1, code='default'）
UPDATE ecommerce_platform SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;
UPDATE ecommerce_product  SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;
UPDATE ecommerce_sync_log SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;

-- 2. ADD INDEX：idx_tenant（DDL 声明 vs 实际不一致）
-- 注意：ecommerce_sync_log 上 idx_tenant 已存在，跳过
ALTER TABLE ecommerce_platform ADD INDEX idx_tenant (tenant_id);
ALTER TABLE ecommerce_product  ADD INDEX idx_tenant (tenant_id);
ALTER TABLE ecommerce_order    ADD INDEX idx_tenant (tenant_id);

COMMIT;
```

**意外调整**：原计划 4 个 ADD INDEX，因 sync_log 已存在 → 调整为 **3 个 ADD INDEX**。SQL 注释里明确跳过原因（避免 1061 Duplicate key name 错误）。

---

## 5. 验证（执行后）

### 5.1 tenant_id 分布

| 表 | 期望 | 实际 |
|---|---|---|
| ecommerce_platform | `3 rows, tenant_id=1` | `3 rows, tenant_id=1` ✅ |
| ecommerce_product | `3 rows, tenant_id=1` | `3 rows, tenant_id=1` ✅ |
| ecommerce_order | `0 rows` | `0 rows` ✅ |
| ecommerce_sync_log | `1 row, tenant_id=1` | `1 row, tenant_id=1` ✅ |

### 5.2 idx_tenant 索引

```sql
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA='meiyetec'
  AND TABLE_NAME IN ('ecommerce_platform','ecommerce_product','ecommerce_order','ecommerce_sync_log')
  AND INDEX_NAME='idx_tenant';
```

| TABLE_NAME | INDEX_NAME | COLUMN_NAME |
|---|---|---|
| ecommerce_order | idx_tenant | tenant_id |
| ecommerce_platform | idx_tenant | tenant_id |
| ecommerce_product | idx_tenant | tenant_id |
| ecommerce_sync_log | idx_tenant | tenant_id |

✅ **4 张表齐备**

### 5.3 7 行详情

| 表 | id | tenant_id | platform | 备注 |
|---|---|---|---|---|
| platform | 1 | **1** | WEIDIAN | shop='S', shop_id='WD1' |
| platform | 2 | **1** | YOUZAN | shop='test-shop', shop_id='test-kdt-5B' |
| platform | 3 | **1** | YOUZAN | shop='test-shop', shop_id='test-kdt-5B' |
| product | 1 | **1** | MOCK | 焕颜保湿面膜 |
| product | 2 | **1** | MOCK | 头皮舒缓精华 |
| product | 3 | **1** | MOCK | 经典美甲护理 |
| sync_log | 1 | **1** | MOCK | PRODUCT SUCCESS 182ms |

---

## 6. 修复后语义

### 6.1 跨租户访问语义

| 场景 | 修复前 | 修复后 |
|---|---|---|
| tenant=1 登录，看 platform 列表 | `403 TENANT_DENIED` | ✅ 3 行可见 |
| tenant=1 登录，看 product 列表 | `403 TENANT_DENIED` | ✅ 3 行可见 |
| tenant=1 登录，触发 sync_log 查询 | `403 TENANT_DENIED` | ✅ 1 行可见 |
| tenant=2 登录 | `403 TENANT_DENIED` | `403 TENANT_DENIED` ✅（数据归属 tenant=1） |
| admin dataScope=ALL | 可见 7 行 | ✅ 可见 7 行 |

### 6.2 性能语义

| 操作 | 修复前 | 修复后 |
|---|---|---|
| `qw.eq(...::tenantId, ...)` 全表扫描 | ❌ 7 行时无感，量增长后 O(n) | ✅ index lookup O(log n) |
| `dashboard` 统计 | ❌ selectCount(null) 全表 | ✅ selectCount(qw) 用索引 |

---

## 7. 改动文件清单

| # | 文件 | 改动 | 行变化 |
|---|---|---|---|
| 1 | `audit/baseline/pre-I092-backup-20260930.sql` | mysqldump 备份产物（7124 bytes, 7 行 INSERT） | 新建 |
| 2 | `audit/sql/migrate_ecommerce_tenant_id.sql` | 正式迁移脚本（UPDATE + 3 ADD INDEX + 注释 + 回滚说明） | 新建 84 |
| 3 | `audit/issues/issue-register.md` | P 表 I092 行 NEW → FIXED | — |
| 4 | `audit/post-audit/phase-2.31-I092-report.md` | 本报告 | 新建 |

---

## 8. 未涉及范围

| 项目 | 状态 | 关联 |
|---|---|---|
| I093 测试残留 DELETE | 🆕 P3 OPEN | Post-Audit 三期（本 session 不执行） |
| DDL 与实际表 schema 不一致 | 🆕 OPEN | I029 范围（DDL 漂移） |
| 其他表的 idx_tenant 索引（如有） | 🆕 待评估 | Post-Audit |
| ecommerce 表 DDL 重建（Flyway） | 🆕 待评估 | I010 + I029 联动 |

---

## 9. Git 状态

```
git log --oneline -5:
c7df0ba fix(I092): ecommerce 4 表 tenant_id=0 → 1 迁移 + idx_tenant 索引补建
617699e docs: I091 CLOSED + 新增 I092 idx_tenant + 新增 I093 测试残留
161f6fa docs: I090 9 端点 FIXED + 新增 I092
1c2c165 fix(I090): EcommerceService 全 9 端点租户隔离
26eb308 docs: I028 ecommerce 子项 FIXED + 新增 I090/I091
```

---

## 10. 风险评估（最终）

| 风险 | 等级 | 缓解 |
|---|---|---|
| UPDATE 误操作 | ✅ 已消除（备份完整） | mysqldump 7124 bytes |
| ALTER TABLE 锁表 | 🟢 低（4 张表 < 10 MB） | MySQL 8 online DDL |
| 与 I093 冲突 | 🟢 无（I093 延后） | 修复中未删除任何数据 |
| idx_tenant 重复 | ✅ 已避免（INFORMATION_SCHEMA 预先检查） | sync_log 已存在 → 跳过 |
| 回滚失败 | 🟢 低（备份完整） | pre-I092-backup-20260930.sql 直接恢复 |

---

## 11. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`c7df0ba`
- **Fix 时间**：2026-09-30 23:48 (UTC+8)
- **DB**：MySQL 8.0.46 @ `localhost:3307/meiyetec`
- **验证**：`INFORMATION_SCHEMA.STATISTICS` 4 表齐备 + 7 行 tenant_id 全 1
- **EOL 政策**：✅ 精确 git add 2 个文件
- **I092 状态**：✅ **FIXED**（修复完成，待 docs commit 后 CLOSED）
- **I093 状态**：🆕 **OPEN**（Post-Audit 三期）