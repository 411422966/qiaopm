# I093 — ecommerce_* dev 测试残留数据清理

> **登记日期**：2026-09-30
> **优先级**：P3（仅 test 数据，生产无影响）
> **关联 Issue**：I090 / I092
> **Fix Phase**：Post-Audit 三期

---

## 1. 现象

`ecommerce_*` 4 张表有 **7 行测试残留数据**（tenant_id=0）：

### 1.1 ecommerce_platform（3 行）

| id | tenant_id | platform | shop_name | shop_id | created_at | updated_at |
|---|---|---|---|---|---|---|
| 1 | 0 | WEIDIAN | "S" | "WD1" | 2026-08-17 00:49:18 | NULL |
| 2 | 0 | YOUZAN | "test-shop" | "test-kdt-5B" | 2026-09-14 01:01:25 | NULL |
| 3 | 0 | YOUZAN | "test-shop" | "test-kdt-5B" | 2026-09-14 01:01:50 | NULL |

**测试证据**：
- `shop_name="S"`（id=1，无意义字符）
- `shop_name="test-shop"`（id=2/3，明文"test"）
- `shop_id="WD1"` / `"test-kdt-5B"`（"test-" 前缀 + "5B" 测试标签）

### 1.2 ecommerce_product（3 行）

| id | tenant_id | platform | external_product_id | product_name |
|---|---|---|---|---|
| 1 | 0 | MOCK | 1001 | 补水面膜 |
| 2 | 0 | MOCK | 1002 | 头皮护理液 |
| 3 | 0 | MOCK | 1003 | 氨基酸洁面乳 |

**测试证据**：
- 全 `platform=MOCK`（dev fallback）
- 商品名来自 `MockEcommerceAdapter` 的样本数据

### 1.3 ecommerce_order

0 行 ✅

### 1.4 ecommerce_sync_log（1 行）

| id | tenant_id | platform | sync_type | status |
|---|---|---|---|---|
| 1 | 0 | (测试值) | (测试值) | RUNNING |

---

## 2. 影响

| 影响项 | 风险等级 |
|---|---|
| 出现在业务列表 | 🟡 P3（污染 dashboard 数字） |
| 跨租户访问 | ✅ 已由 I090 `selectPlatformByTenant` 阻断（403） |
| dataScope=ALL admin 看 | 🟡 P3（admin 列表里看到测试数据） |
| 数据库性能 | 🟢 无（数据量小） |

**关键说明**：
- I090 修复后，**普通用户看不到这 7 行**（selectPlatformByTenant 403）
- **admin dataScope=ALL 用户仍能看到**（admin 全局视图）
- **dashboard 全表统计**仍包含这 7 行（I090 修复后 tenantId 过滤）

---

## 3. 关联

| Issue | 关系 |
|---|---|
| **I090** | EcommerceService 租户隔离（FIXED commit `1c2c165`）— 阻断普通用户跨租户访问 |
| **I092** | 4 表 `tenant_id=0` 历史数据迁移（P1 OPEN）— 决定是 UPDATE 还是 DELETE |
| **I091** | secrets 清洗（CLOSED）— 与本 issue 无关 |

**I092 与 I093 的关系**：
- **I092 = 方案 A**（UPDATE `tenant_id=0` → `tenant_id=1`，保留数据）
- **I093 = 方案 B**（DELETE 测试数据，不保留）
- 二选一：
  - 若选 I092 → I093 自动 CLOSED（保留 = 测试数据归属 tenant_id=1）
  - 若选 I093 → I092 自动 CLOSED（不需要迁移，直接 DELETE）

**用户决策（2026-09-30 phase-2.30）**：Q2 选 A+I093 拆分
- I092：只迁移（UPDATE tenant_id）
- I093：清理测试残留（DELETE）

---

## 4. 修复前置条件

| 条件 | 状态 |
|---|---|
| 1. 确认无 dev 联调依赖（QA / 前端 / 集成测试） | ⏳ 待确认 |
| 2. dev / staging 环境无此 id 引用 | ⏳ 待确认 |
| 3. integration test 不依赖这 7 行 | ⏳ 待确认 |

---

## 5. 修复方案（SQL）

```sql
-- audit/sql/cleanup_ecommerce_test_data.sql
-- I093 P3：清理 ecommerce_* 4 表 dev 测试残留

USE meiyetec;

START TRANSACTION;

-- 1. ecommerce_sync_log（无外键依赖，可直接删）
DELETE FROM ecommerce_sync_log WHERE tenant_id = 0;
-- 预期影响行：1

-- 2. ecommerce_product（无外键依赖，可直接删）
DELETE FROM ecommerce_product WHERE tenant_id = 0;
-- 预期影响行：3

-- 3. ecommerce_platform（无外键依赖，可直接删）
DELETE FROM ecommerce_platform WHERE tenant_id = 0;
-- 预期影响行：3

-- 校验
SELECT 'ecommerce_platform' AS tbl, COUNT(*) AS remaining FROM ecommerce_platform WHERE tenant_id = 0;
SELECT 'ecommerce_product' AS tbl, COUNT(*) AS remaining FROM ecommerce_product WHERE tenant_id = 0;
SELECT 'ecommerce_sync_log' AS tbl, COUNT(*) AS remaining FROM ecommerce_sync_log WHERE tenant_id = 0;

COMMIT;
```

**预期输出**：
```
remaining  0
remaining  0
remaining  0
```

---

## 6. 修复后语义

| 端点 | 修复前 | 修复后 |
|---|---|---|
| `GET /ecommerce/platform/list`（admin dataScope=ALL） | 3 行测试数据 | 0 行 |
| `GET /ecommerce/product/list`（admin dataScope=ALL） | 3 行 MOCK 数据 | 0 行 |
| `GET /ecommerce/dashboard` `totalPlatforms` | 3 | 0 |
| `GET /ecommerce/dashboard` `totalProducts` | 3 | 0 |
| `GET /ecommerce/dashboard` `totalOrders` | 0 | 0 |

---

## 7. 与 I092 决策的二选一

| 选择 | 含义 | 本 issue 状态 |
|---|---|---|
| **I092 优先** | UPDATE `tenant_id=0` → `tenant_id=1` | I093 自动 CLOSED（测试数据归属 tenant_id=1 admin） |
| **I093 优先** | DELETE 测试数据 | I092 自动 CLOSED（不需要迁移） |
| **两者都做** | 先 I092 UPDATE → 再 I093 DELETE 测试残留 | 两次 SQL 执行 |

**推荐顺序**：先 I092 后 I093
- 先 UPDATE 给老数据一个合法 tenant_id（避免跨租户 403）
- 再 DELETE dev 测试残留（清理 dashboard 数字污染）

---

## 8. 回滚方案

```sql
-- I093 回滚（INSERT 备份回）
-- 但 I093 是 DELETE，无备份 → 不可回滚
-- 因此修复前必须先确认无 dev 联调依赖

-- 若需恢复，使用 binlog：
-- mysqlbinlog --start-datetime="2026-09-30 23:00:00" \
--             --stop-datetime="2026-09-30 23:59:00" \
--             meiyetec-bin.000XXX | mysql -u root -p
```

**安全建议**：修复前先做 `mysqldump` 备份：

```bash
mysqldump -h 127.0.0.1 -P 3307 -u root -proot123456 \
  --where="tenant_id=0" meiyetec ecommerce_platform \
  > audit/sql/backup_ecommerce_platform_tenant_id_0.sql
```

---

## 9. 风险评估

| 风险 | 等级 | 缓解 |
|---|---|---|
| 删除生产数据 | ✅ **N/A**（确认全为测试） | DELETE WHERE tenant_id=0，限定范围 |
| dev 联调中断 | 🟡 中 | 修复前需 QA 确认无依赖 |
| 回滚失败 | 🟡 中 | 修复前 mysqldump 备份 |
| 与 I092 冲突 | 🟢 低 | I092 先 UPDATE 后 I093 DELETE，幂等 |

---

## 10. 验收

| 检查项 | 期望 |
|---|---|
| `SELECT COUNT(*) FROM ecommerce_platform WHERE tenant_id=0` | 0 |
| `SELECT COUNT(*) FROM ecommerce_product WHERE tenant_id=0` | 0 |
| `SELECT COUNT(*) FROM ecommerce_sync_log WHERE tenant_id=0` | 0 |
| `GET /ecommerce/platform/list`（admin） | 0 行 |
| `GET /ecommerce/dashboard` `totalPlatforms` | 0 |
| `GET /ecommerce/dashboard` `totalProducts` | 0 |
| 单元测试 / 集成测试 | 通过 |

---

## 11. 待用户拍板

| # | 决策点 | 建议 |
|---|---|---|
| 1 | 与 I092 的执行顺序 | 先 I092 后 I093（安全升级） |
| 2 | dev 联调依赖确认 | QA 团队确认 |
| 3 | 是否做 mysqldump 备份 | 必做（低成本，高安全） |
| 4 | 修复窗口 | 维护窗口 / 业务低峰期 |
| 5 | 是否同步建 idx_tenant 索引（I092 范围） | 是（同一脚本） |

---

## 13. Fix Phase

**Post-Audit 三期**（与 I092 同步）

---

**结束语**：本 issue 是 I090 / I092 的衍生清理项，DB 无真实生产数据，**风险等级 LOW**。