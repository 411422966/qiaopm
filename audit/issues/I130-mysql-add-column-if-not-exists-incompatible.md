# I130 — MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，历史迁移脚本中存在多处使用（P1）

> 发现时间：2026-10-26
> 发现方式：batch1 权限体系对齐验收（`docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/`）修复受阻 → MySQL 8.0.46 语法实测 → 全量脚本扫描
> 严重级别：**P1**（不直接导致功能故障，但**阻塞所有依赖这些脚本的修复**，且存量脚本大面积不可执行）
> 状态：**Open**（未修复；与 I129 一并转批次二统一评审）
> 关联：I129（直接受害者）、EXT-22 §0.7 / EXT-19 §0.5（SoT 文档记载有误，需修订）、I118（DDL 缺失同类）
> 基线：commit `c71323f589d1cdb11c052600492ad6fd254e5c9f`（batch1-fix.sql 执行前）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 1. 一句话结论

**`ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `DROP COLUMN IF EXISTS` 是 MariaDB 语法，MySQL 8.0 至今不支持。**
本项目 `backend/src/main/resources/db/sql/` 下的 **16 个脚本、共 71 处**用了这个语法，
在本环境（MySQL 8.0.46）**执行即报 `ERROR 1064`** —— 这些脚本**一条都跑不通**。

更严重的是：`EXT-22 §0.7` 与 `EXT-19 §0.5` 作为事实来源（SoT）明确写着「MySQL 8.0.29+ 可用」，
**该结论与实测矛盾**，属于文档层面的错误指引，会持续误导后续批次。

---

## 2. 硬证据

### 2.1 MySQL 8.0.46 实测（探针表，测完即删）

```
mysql> ALTER TABLE _t_probe_i129 ADD COLUMN IF NOT EXISTS c1 INT;
ERROR 1064 (42000) at line 1: You have an error in your SQL syntax; check the manual that
corresponds to your MySQL server version for the right syntax to use near 'IF NOT EXISTS c1 INT' at line 1

mysql> CREATE INDEX IF NOT EXISTS idx_probe ON _t_probe_i129(id);
ERROR 1064 (42000) at line 1: You have an error in your SQL syntax; check the manual that
corresponds to your MySQL server version for the right syntax to use near
'IF NOT EXISTS idx_probe ON _t_probe_i129(id)' at line 1

mysql> ALTER TABLE _t_probe_i129 DROP COLUMN IF EXISTS nope;
ERROR 1064 (42000) at line 1: You have an error in your SQL syntax; check the manual that
corresponds to your MySQL server version for the right syntax to use near 'IF EXISTS nope' at line 1
```

**对照组（MySQL 8.0 支持，不受影响）**：

```
mysql> CREATE TABLE IF NOT EXISTS _t_probe_i129 (id INT);   -- ✅ 成功
```

> 关键区分：`CREATE TABLE IF NOT EXISTS` 是 MySQL **原生支持**的；
> 只有 **`ALTER TABLE … ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `DROP … IF EXISTS`**
> 这三类是 MariaDB 专有语法。
> 因此 Flyway 基线脚本 `db/migration/V1__init_baseline.sql`（36 处 `IF NOT EXISTS`，全部是 `CREATE TABLE IF NOT EXISTS`）**不受影响**。

### 2.2 全量扫描结果（`backend/src/main/resources/db/sql/`）

扫描正则：`ADD COLUMN IF NOT EXISTS` | `CREATE [UNIQUE] INDEX IF NOT EXISTS` | `DROP [COLUMN|INDEX] IF EXISTS`

**命中 16 个脚本 / 71 处**（`db/sql/` 全量子目录，含 `audit-subset/`）：

| 脚本 | `ADD COLUMN IF NOT EXISTS` | `CREATE INDEX IF NOT EXISTS` | `DROP … IF EXISTS` |
|---|---|---|---|
| `init_m21_lbs_dispatch.sql` | 16 | — | — |
| `migrate_m12_05_business_policy_dispatch.sql` | 7 | 2 | — |
| `migrate_p2_11_dual_verify.sql` | 8 | — | — |
| `migrate_m02_pos_change_amount.sql` | 4 | 2 | — |
| `crm_follow_up_plan.sql` | 6 | — | — |
| `migrate_ext_22_google_maps_place_details.sql` | 4 | — | — |
| `migrate_dcep01_wallet_pin.sql` | 4 | — | — |
| `migrate_stock_check_v1.sql` | 3 | — | — |
| `migrate_m03_member_profile_hair_nail.sql` | 3 | — | — |
| `init_m13_waitlist_scoring.sql` | 3 | — | — |
| `patch_t_c07_test.sql` | 3 | — | — |
| `migrate_m05_05_promotion_time_window.sql` | 2 | — | — |
| `init_treatment_report.sql` | 1 | — | — |
| `migrate_m01_04_booking_store_id.sql` | — | — | 1 |
| `migrate_ext_19_mini_program_openid.sql` | 1 | — | — |
| `audit-subset/migrate_ext_22_subset.sql` | 1 | — | — |
| **合计（16 文件）** | **66** | **4** | **1** |

> 另有 `db/migration/V1__init_baseline.sql`（Flyway 自动执行）36 处 `IF NOT EXISTS`，
> 经逐条核对**全部为 `CREATE TABLE IF NOT EXISTS`**（MySQL 原生支持）→ **不在本单影响范围**。

### 2.3 SoT 文档与实测冲突（按规则 11 标 ISSUE）

EXT-* Operational Notes 是本项目的事实来源（SoT）。以下两处与实测**直接矛盾**：

| 位置 | 原文 | 实测 |
|---|---|---|
| `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc:106` | 「`migrate_ext_22_google_maps_place_details.sql` 使用 `ADD COLUMN IF NOT EXISTS`（**MySQL 8.0.29+**）」 | ❌ MySQL 8.0.46 不支持，报 1064 |
| `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc:107` | 「旧版本 MySQL（< 8.0.29）需先 `SHOW COLUMNS …` 检查再 ALTER」 | ❌ 判定条件错；**任何** MySQL 8.0 版本都需先判存 |
| `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc:159` | 「`mysql --version` ≥ 8.0.29（否则手动 `SHOW COLUMNS` 检查）」 | ❌ 版本门槛错 |
| `.cursor/rules/ext-19-operational-notes.mdc:34` | 「`migrate_ext_19_mini_program_openid.sql` 用了 `ADD COLUMN IF NOT EXISTS`（**MySQL 8.0.29+**）」 | ❌ 同上 |
| `.cursor/rules/ext-19-operational-notes.mdc:35` | 「旧版本 MySQL（< 8.0.29）需先 `SHOW COLUMNS …` 检查再 ALTER」 | ❌ 同上 |
| `.cursor/rules/ext-19-operational-notes.mdc:84` | 「MySQL 版本 ≥ 8.0.29（或 `ALTER` 前 `SHOW COLUMNS` 检查）」 | ❌ 版本门槛错 |

> 按项目规则 11「审计结论与 EXT-* 冲突时以 EXT-* 为准并标 ISSUE」——
> 本单即为该 ISSUE 的登记，**EXT-* 文档本身需要修订**（修订属治理文件写入，需单独批准，本次未改）。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **修复通道被堵** | 任何依赖这些脚本的修复（如 I129）都无法执行，必须先改写脚本语法 |
| **存量脚本大面积不可执行** | 16 个脚本 71 处语法错误，任何一次「补跑历史迁移」都会在第一条 DDL 断掉 |
| **静默缺失风险** | `db/sql/` 不在 Flyway 扫描路径（`application.yml:22-23`），脚本跑了没有台账、没跑没有告警 → 缺失只在运行期暴露 |
| **SoT 误导** | EXT-19 / EXT-22 的「8.0.29+ 可用」会被后续批次当作免检前提，重复踩坑 |
| **不影响 Flyway 基线** | `db/migration/V1__init_baseline.sql` 只用 `CREATE TABLE IF NOT EXISTS`，MySQL 原生支持 → 不受影响 |

---

## 4. 修复方向（待批次二统一评审，未实施）

### 4.1 统一改写为「判存 + 动态执行」

两种可选写法：

**写法 A（`information_schema` 判存 + `PREPARE`）** —— 推荐，纯 SQL，无过程定义：

```sql
-- 列添加模板
SET @db  = DATABASE();
SET @tbl = 'pos_order';
SET @col = 'tendered_amount';
SET @ddl = 'ALTER TABLE `pos_order` ADD COLUMN `tendered_amount` DECIMAL(12,2) DEFAULT NULL COMMENT ''...''';
SELECT COUNT(*) INTO @n FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = @col;
SET @stmt = IF(@n = 0, @ddl, 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
```

- 索引：判 `information_schema.STATISTICS`
- 删列：判 `information_schema.COLUMNS`

**写法 B（存储过程封装）** —— 语义更清晰，但需先 `DROP PROCEDURE IF EXISTS` 再 `CREATE PROCEDURE`，
在无 `DELIMITER` 支持的客户端（部分 JDBC 工具）下容易出错：

```sql
DROP PROCEDURE IF EXISTS meiyetec_add_column_if_absent;
CREATE PROCEDURE meiyetec_add_column_if_absent(
  IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
    SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END;
```

### 4.2 批量改造顺序（建议）

1. 先做 **I129 的两个脚本**（当前阻塞项），验证写法在 MySQL 8.0.46 可跑且**重复执行幂等**；
2. 再按 §2.2 表格顺序批量改写其余 14 个脚本；
3. 全部改写后，在**只读副本**上按脚本顺序完整回放一遍，确认无 1064；
4. 建立「脚本 → 已执行」台账，杜绝静默缺失（当前完全缺失）。

### 4.3 文档修订（需单独批准）

| 位置 | 修订方向 |
|---|---|
| `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc:106,107,159` | 删除「MySQL 8.0.29+ 可用」的错误结论，改为「MySQL 8.0 全部版本不支持，须判存 + 动态执行」 |
| `.cursor/rules/ext-19-operational-notes.mdc:34,35,84` | 同上 |

> ⚠️ `.cursor/rules/*.mdc` 属治理文件，**本次未修改**，按规则 18 需用户单独批准。

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/evidence.md` §5.3 | 1064 原始实测输出 |
| 同上 §7 问题清单 P3 / P4 | 本单的原始登记项 |
| `backend/src/main/resources/db/sql/`（16 个脚本） | 71 处不兼容语法命中位置 |
| `backend/src/main/resources/db/sql/migrate_m02_pos_change_amount.sql:5-6,9-10,14-15` | `ADD COLUMN IF NOT EXISTS` ×4 + `CREATE INDEX IF NOT EXISTS` ×2 |
| `backend/src/main/resources/db/sql/migrate_m04_bundle.sql:4,7,10` | `ADD COLUMN IF NOT EXISTS` ×3（无 `IF NOT EXISTS`，但同样未执行） |
| `backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql:7,10,11,12` | EXT-22 脚本，SoT 与实测冲突点 |
| `backend/src/main/resources/db/sql/migrate_ext_19_mini_program_openid.sql` | EXT-19 脚本，SoT 与实测冲突点 |
| `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc:104-108,159` | SoT 错误结论原文（**未修改**） |
| `.cursor/rules/ext-19-operational-notes.mdc:34-35,84` | SoT 错误结论原文（**未修改**） |
| `backend/src/main/resources/db/migration/V1__init_baseline.sql` | 36 处 `CREATE TABLE IF NOT EXISTS`（对照组，MySQL 支持） |
| `backend/src/main/resources/application.yml:22-23,30` | Flyway 只扫 `db/migration`；`db/sql/` 为手工执行区 |
| `audit/issues/I129-missing-columns-pos-crm.md` | 直接受害者 |

**绑定基线**：commit `c71323f589d1cdb11c052600492ad6fd254e5c9f` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **这 16 个脚本中哪些「曾经被成功执行过」** —— 部分脚本可能在 MySQL 5.x 或 MariaDB 环境下跑过
   （MariaDB 支持该语法），导致库内状态与脚本预期不一致 → **未确认**，需逐脚本对照 `information_schema` 核对。
2. **生产环境库的实际列状态** —— 本单只覆盖本地 Docker 验收环境 → **未确认**。
3. **各脚本的期望执行顺序与依赖关系** —— `db/sql/` 无执行台账，脚本间依赖（如 `init_*` 先于 `migrate_*`）
   未在文件中声明 → **未确认**，批量改造时需人工梳理。
4. **`migrate_m04_bundle.sql` 的语法问题与其「从未执行」的关系** ——
   该脚本用的是**无条件** `ADD COLUMN`（无 `IF NOT EXISTS`），语法本身在 MySQL 8.0 可跑，
   即「未执行」与「语法不兼容」是**两个独立原因**；本单已把 I129 的失败归因于「从未执行」，
   但**为何从未执行**（漏执行 vs 执行失败 vs 未纳入流程）→ **未确认**。
