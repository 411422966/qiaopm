# I129 — pos_order / crm_package 缺列导致收银台与套餐列表必 500（P0）

> 发现时间：2026-10-26
> 发现方式：batch1 权限体系对齐验收（`docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/`）浏览器实测 + 后端日志 + `information_schema` 库结构核对
> 严重级别：**P0**（收银台与套餐列表的核心列表接口 100% 失败，功能不可用）
> 状态：**Open**（未修复；P1~P3 已由用户确认本批不做，转批次二统一评审）
> 关联：I130（脚本语法不兼容，本单的直接修复前置）、I118（DDL 缺失同类）、I122（表缺失同类）、EXT-22 §0.7 / EXT-19 §0.5（SoT 文档记载有误）
> 基线：commit `c71323f589d1cdb11c052600492ad6fd254e5c9f`（batch1-fix.sql 执行前）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 1. 一句话结论

**两张业务核心表的列从未进过库，而实体（Entity）已按这些列建模**，
导致 MyBatis-Plus 生成的 SELECT 语句引用不存在的列，后端抛 `BadSqlGrammarException`，
前端拿到 `code=500`「数据库异常」——**收银台「今日订单 / 统计」和套餐「列表 / 统计」两条链路 100% 不可用**。

| 表 | 缺失列 | 影响接口 | 实测响应 |
|---|---|---|---|
| `pos_order` | `tendered_amount` / `change_amount` | `GET /api/pos/orders/today`、`GET /api/pos/orders/stats` | HTTP 200 + `code=500`「数据库异常」 |
| `crm_package` | `bundle_type` / `bundle_config` / `bundle_item_count` | `GET /api/packages`、`GET /api/packages/stats` | HTTP 200 + `code=500`「数据库异常」 |

**与 batch1 权限改造无关**：两个字段组分别属于收银找零（M02-04）和项目组合（M04）两个历史特性，
其迁移脚本 `migrate_m02_pos_change_amount.sql` / `migrate_m04_bundle.sql` **从未被执行过**。

---

## 2. 硬证据

### 2.1 后端异常栈（实测）

`pos_order` 缺列：

```
org.springframework.jdbc.BadSqlGrammarException:
  Cause: java.sql.SQLSyntaxErrorException: Unknown column 'tendered_amount' in 'field list'
  The error may exist in com/meiyetec/mapper/PosOrderMapper.java
  SQL: SELECT id,tenant_id,...,refunded_amount,refund_count,tendered_amount,change_amount,...
       FROM pos_order WHERE deleted=0 AND (tenant_id = ? AND pay_time >= ?)
```

`crm_package` 缺列：

```
org.springframework.jdbc.BadSqlGrammarException:
  Cause: java.sql.SQLSyntaxErrorException: Unknown column 'bundle_type' in 'field list'
  The error may exist in com/meiyetec/mapper/PackageItemMapper.java
  SQL: SELECT id,tenant_id,name,...,bundle_type,bundle_config,bundle_item_count,...
       FROM crm_package WHERE (tenant_id = ?) ORDER BY id DESC LIMIT ?
```

> ⚠️ 异常是 `BadSqlGrammarException`（SQL 语法/列不存在），**不是** 权限不足、不是空数据、不是前端问题。
> 前端拿到 HTTP 200 但 `code=500`，属于**假成功外层 + 真失败内层**，需按业务失败计。

### 2.2 库内实际结构（`information_schema` 实测）

查询 `information_schema.COLUMNS`（`TABLE_SCHEMA='meiyetec'`）针对 5 个目标列 → **返回 0 行**，即全部不存在：

| 表 | 行数（`COUNT(*)` 实测） | 目标列是否存在 |
|---|---|---|
| `pos_order` | 1848 | ❌ `tendered_amount` ❌ `change_amount` |
| `crm_package` | 46 | ❌ `bundle_type` ❌ `bundle_config` ❌ `bundle_item_count` |

`pos_order` 现有列全集（26 列，无上述两列）：

```
booking_id, cashier_id, cashier_name, create_time, deleted, discount_amount, final_amount,
id, member_id, member_name, member_phone, order_no, paid_amount, pay_time,
refund_amount, refund_count, refund_reason, refund_time, refunded_amount,
remark, status, store_id, tenant_id, total_amount, type, update_time
```

`crm_package` 现有列全集（19 列，无上述三列）：

```
category, code, cost_price, cover, create_by, create_time, deleted, description,
id, name, price, redeem_count, remark, sale_count, status, tenant_id,
total_times, update_time, valid_days
```

> **对照**：`pos_payment` 表存在（448 行），现有列 `amount, create_time, deleted, error_msg, id,
> method, order_id, pay_time, payment_no, refund_time, status, tenant_id, transaction_id, update_time`
> —— 同样缺 `tendered_amount` / `change_amount`（`migrate_m02_pos_change_amount.sql` 第 2 段同样未执行）。
> 但 `PosPayment` 实体的 `tenderedAmount` / `changeAmount`（`entity/PosPayment.java:26-27`）当前**未被 SELECT 命中**
> （本次验收未观察到 `pos_payment` 相关 500），**实际影响面待批次二扩大验证**。

### 2.3 实体侧建模（代码是对的，库侧缺列）

| 实体 | 表映射 | 缺失字段 | 位置 |
|---|---|---|---|
| `com.meiyetec.entity.PosOrder` | `@TableName("pos_order")`（L13） | `tenderedAmount`（L47）/ `changeAmount`（L49） | `entity/PosOrder.java:47-49` |
| `com.meiyetec.entity.PosPayment` | `@TableName("pos_payment")`（L13） | `tenderedAmount`（L26）/ `changeAmount`（L28） | `entity/PosPayment.java:26-28` |
| `com.meiyetec.entity.PackageItem` | `@TableName("crm_package")`（L9） | `bundleType`（L32）/ `bundleConfig`（L34）/ `bundleItemCount`（L36） | `entity/PackageItem.java:32-36` |

MyBatis-Plus 默认驼峰转下划线（`application.yml` → `mybatis-plus.*`），实体字段 → 列名映射为
`tenderedAmount`→`tendered_amount`、`bundleItemCount`→`bundle_item_count`，
故 SELECT 自动带上这些列，库中没有就必然报错。

> ⚠️ **契约漂移**：后端实体与前端 DTO 口径是一致的（`dto/PosPayDTO.java:35` 有 `tenderedAmount`，
> `dto/PackageSaveDTO.java:45` 有 `bundleType`），**漂移只发生在「实体 ↔ 物理表」这一层**，
> 不属于前后端契约不一致。

### 2.4 已有迁移脚本从未执行

| 脚本 | 目标 | 库内是否生效 |
|---|---|---|
| `backend/src/main/resources/db/sql/migrate_m02_pos_change_amount.sql` | `pos_order` + `pos_payment` 各加 `tendered_amount` / `change_amount`，并建 2 个索引 | ❌ 未执行（列 0 行、索引不存在） |
| `backend/src/main/resources/db/sql/migrate_m04_bundle.sql` | `crm_package` 加 `bundle_type` / `bundle_config` / `bundle_item_count`，并建 `idx_bundle_type` | ❌ 未执行（列 0 行、索引不存在） |

> 两个脚本均位于 `backend/src/main/resources/db/sql/` 下 —— 该目录**不在 Flyway 扫描路径内**
> （`application.yml:22-23` → `spring.flyway.locations: classpath:db/migration`），
> 属「legacy 手工执行脚本」，**没有任何自动执行或启动校验机制**，漏执行即静默缺失。

### 2.5 脚本本身在本环境不可执行（修复前置，见 I130）

两个脚本都用了 MySQL 8.0 **不支持**的 `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`
（MariaDB 语法）。MySQL 8.0.46 实测：

```
mysql> ALTER TABLE _t_probe_i129 ADD COLUMN IF NOT EXISTS c1 INT;
ERROR 1064 (42000) at line 1: You have an error in your SQL syntax; check the manual that
corresponds to your MySQL server version for the right syntax to use near 'IF NOT EXISTS c1 INT' at line 1

mysql> CREATE INDEX IF NOT EXISTS idx_probe ON _t_probe_i129(id);
ERROR 1064 (42000) at line 1: You have an error in your SQL syntax; ... near 'IF NOT EXISTS idx_probe ON _t_probe_i129(id)' at line 1
```

→ **直接执行现有脚本会在第一条 DDL 就 1064 失败**，修复必须先改写脚本语法。详见 **I130**。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **收银台 `/pos/checkout`** | 页面顶部弹出「数据库异常」×2，「今日订单」与「统计」卡片全空 |
| **套餐 `/package`** | 页面顶部弹出「数据库异常」×2，列表与统计全空，且浏览器抛 `PAGEERROR` |
| **涉及角色** | admin / manager01 / clerk01（凡有 `pos:list` 或 `package:list` 权限的账号都会命中） |
| **数据不丢但功能不可用** | `pos_order` 1848 行、`crm_package` 46 行数据完好，纯 DDL 补齐即可恢复 |
| **阻塞收银闭环** | 现金实收/找零字段无法落库，M02-04「服务端独立计算找零」链路无法真正跑通 |

> **不影响权限体系本身**：batch1 权限改造（菜单收敛 / 路由守卫 / 越权拦截）验证通过，
> 本单是**独立的功能阻塞项**，不改变 batch1 权限验收结论。

---

## 4. 修复方向（待批次二统一评审，未实施）

### 4.1 脚本语法改写（前置，必做）

把 `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` 改为 MySQL 8.0 兼容的幂等写法，
即 **`information_schema` 判存 + `PREPARE` 动态执行**（模板）：

```sql
-- 幂等列添加模板（MySQL 8.0 兼容）
SET @db = DATABASE();
SET @tbl = 'pos_order';
SET @col = 'tendered_amount';
SET @ddl = 'ALTER TABLE `pos_order` ADD COLUMN `tendered_amount` DECIMAL(12,2) DEFAULT NULL
            COMMENT ''现金实收金额（本次支付中 cash 的实收总额）'' AFTER `refund_count`';
SELECT COUNT(*) INTO @exists FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = @col;
SET @stmt = IF(@exists = 0, @ddl, 'DO 0');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;
```

索引同理（判 `information_schema.STATISTICS`），`DROP COLUMN IF EXISTS` 判 `information_schema.COLUMNS`。

### 4.2 执行与验证

1. 改写 `migrate_m02_pos_change_amount.sql` 与 `migrate_m04_bundle.sql`；
2. 先在**只读副本**（用 `backup/meiyetec_20261025_020004.sql` 恢复到临时 schema）跑一遍，确认幂等（重复执行不报错）；
3. 在 live 库执行，`information_schema` 复核 5 列 + 3 索引到位；
4. 运行时复验：`GET /api/pos/orders/today`、`/api/pos/orders/stats`、`/api/packages`、`/api/packages/stats` → `code=200`；
5. 补一条**回归**：现金支付落库后 `pos_order.tendered_amount` / `change_amount` 与 `pos_payment` 一致。

### 4.3 防复发建议

| 建议 | 说明 |
|---|---|
| 迁移脚本纳入版本管理执行通道 | `db/sql/` 下脚本目前 100% 靠人工执行，无台账、无校验 → 建议建立「脚本 → 已执行」登记表，或迁入 Flyway `db/migration/` 目录 |
| 启动期 schema 校验 | 启动时按实体比对关键表列，缺列则 fail-fast（当前是运行期才炸） |
| 存量脚本语法普查 | 见 **I130**，16 个脚本 / 71 处命中同类语法 |

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/evidence.md` §5.1 / §5.2 | 缺列异常栈与 `information_schema` 核对 |
| 同上 §5.3 | `ADD COLUMN IF NOT EXISTS` 1064 实测 |
| 同上 §7 问题清单 P1 / P2 / P3 | 本单的原始登记项 |
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/admin-pos-checkout-page.png` | 收银台「数据库异常」toast 截图 |
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/admin-package-page.png` | 套餐页「数据库异常」toast + `PAGEERROR` 截图 |
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/clerk01-pos-checkout-error.png` | 错误 toast 专项截图 |
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/operator01-pos-checkout-error.png` | 同上 |
| `backend/src/main/resources/db/sql/migrate_m02_pos_change_amount.sql` | 未执行的迁移脚本（含 6 处不兼容语法） |
| `backend/src/main/resources/db/sql/migrate_m04_bundle.sql` | 未执行的迁移脚本（含 3 处不兼容语法） |
| `backend/src/main/java/com/meiyetec/entity/PosOrder.java:47-49` | 实体已建模 `tenderedAmount` / `changeAmount` |
| `backend/src/main/java/com/meiyetec/entity/PackageItem.java:32-36` | 实体已建模 `bundleType` / `bundleConfig` / `bundleItemCount` |
| `backend/src/main/java/com/meiyetec/entity/PosPayment.java:26-28` | `pos_payment` 同样缺列（影响面待扩验） |
| `backend/src/main/resources/application.yml:22-23,30` | Flyway 只扫 `db/migration`，`db/sql/` 为手工执行区 |
| `audit/issues/I130-mysql-add-column-if-not-exists-incompatible.md` | 脚本语法不兼容专项 |
| `backup/meiyetec_20261025_020004.sql` | DB snapshot |

**绑定基线**：commit `c71323f589d1cdb11c052600492ad6fd254e5c9f` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **`pos_payment` 的实际运行影响** —— 库内同样缺 2 列，但本次验收未观察到 `pos_payment` 相关的 500
   （可能因该链路未被 SELECT 命中，或被 `pos_order` 的异常先行掩盖）→ **未确认**，需批次二扩大验证。
2. **`bundle_config` 的存量数据** —— 补列后 `bundle_type` 默认 0（单商品），
   存量 46 行套餐是否为「组合型」→ **未确认**。
3. **生产环境库是否同样缺列** —— 本单只覆盖本地 Docker 验收环境 → **未确认**。
4. **缺列的起始时间点与漏执行原因** —— 脚本从未执行过，但**具体是哪个环节漏掉** → **未确认**。
