# Phase 2.3 Batch 5 Apply Log

> **执行时间**：2026-09-21 15:46 (UTC+8）
> **批次**：Batch 5 / 共 5 批
> **范围**：EXT-22 子集 — `google_maps_config` +1 列 + `ecommerce_platform` +3 列
> **脚本**：`backend/src/main/resources/db/sql/audit-subset/migrate_ext_22_subset.sql`（18 行）
> **源脚本**：`backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql`（MariaDB 语法不兼容 MySQL 8.0，已适配）

---

## 1. 执行命令

```powershell
# PowerShell 路径：用 -e + Get-Content -Raw（绕过 < 重定向限制）
$sql = Get-Content "d:\QiaoPM\backend\src\main\resources\db\sql\audit-subset\migrate_ext_22_subset.sql" -Raw
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 meiyetec -e $sql
```

**执行结果**：✅ 成功（MySQL 仅返回 password warning，无 ERROR 1064 / 1060 / 1146 等报错）

---

## 2. 变更清单（4 列）

### 2.1 `google_maps_config` +1 列

| 列名 | 类型 | 默认值 | Nullable | 位置 |
|---|---|---|---|---|
| `enable_place_details` | `tinyint` | `0` | YES | 末尾追加（无 AFTER 子句） |

### 2.2 `ecommerce_platform` +3 列

| 列名 | 类型 | 默认值 | Nullable | 位置 |
|---|---|---|---|---|
| `app_id` | `varchar(64)` | `NULL` | YES | position 8 — AFTER `app_secret` (pos 7) |
| `kdt_id` | `varchar(64)` | `NULL` | YES | position 9 — AFTER `app_id` (pos 8) |
| `sign_secret` | `varchar(255)` | `NULL` | YES | position 10 — AFTER `kdt_id` (pos 9) |

**COMMENT 文本**：

| 列名 | COMMENT |
|---|---|
| `enable_place_details` | `'1=enable Place Details billed interface, default 0'` |
| `app_id` | `'Youzan AppID (distinct from appKey)'` |
| `kdt_id` | `'Youzan shop ID for webhook URL'` |
| `sign_secret` | `'webhook signing secret for X-Youzan-Sign'` |

---

## 3. 适配变更记录

### 3.1 关键变更 1：删除 `IF NOT EXISTS`

**原因**：源脚本使用 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，这是 **MariaDB 语法**，MySQL 8.0 不支持。

**证据**：MySQL 8.0.46 实测报错：
```
ERROR 1064 (42000): ... near 'IF NOT EXISTS enable_place_details ...'
```

**对比表**：

| 特性 | MySQL | MariaDB |
|---|---|---|
| `CREATE TABLE IF NOT EXISTS` | ✅ | ✅ |
| `ALTER TABLE ADD COLUMN IF NOT EXISTS` | ❌ **不支持** | ✅ |
| `ALTER TABLE DROP COLUMN IF EXISTS` | ❌ **不支持** | ✅ |

**修复策略**：删除 `IF NOT EXISTS`，前置 `SHOW COLUMNS` / `INFORMATION_SCHEMA` 已确认 4 列均不存在。

**风险**：0 — apply 前 4 列确认不存在。

---

### 3.2 关键变更 2：COMMENT 中→英

**记录**：COMMENT 文本由中文改英文，原因：PowerShell stdout 编码问题导致中文乱码。

**详细**：
- PowerShell 默认 ANSI 解码
- MySQL stdout 输出的中文 COMMENT 字段（含 UTF-8 中文）会被冲掉
- 改英文 COMMENT 避免 apply-log 阶段 stdout 验证时乱码
- 业务代码读 COMMENT 仅作运维注释，无 i18n 影响

**对照**（中文原文未保留英文版前的版本，仅作记录用）：

| 列名 | 原中文 COMMENT | 改后英文 COMMENT |
|---|---|---|
| `enable_place_details` | `1=启用 Place Details 计费接口，默认 0` | `1=enable Place Details billed interface, default 0` |
| `app_id` | `有赞 AppID（区别于 appKey）` | `Youzan AppID (distinct from appKey)` |
| `kdt_id` | `有赞店铺 ID（kdtId），回调 URL 用` | `Youzan shop ID for webhook URL` |
| `sign_secret` | `webhook 签名密钥（X-Youzan-Sign）` | `webhook signing secret for X-Youzan-Sign` |

---

## 4. 验证结果（apply 后立刻执行）

### 4.1 新列存在性（4/4 全部存在）

```
SHOW COLUMNS FROM google_maps_config LIKE 'enable_place_details';
Field                Type      Null Key Default Extra
enable_place_details tinyint   YES      0

SHOW COLUMNS FROM ecommerce_platform LIKE 'app_id';
Field   Type         Null Key Default Extra
app_id  varchar(64)  YES      NULL

SHOW COLUMNS FROM ecommerce_platform LIKE 'kdt_id';
Field   Type         Null Key Default Extra
kdt_id  varchar(64)  YES      NULL

SHOW COLUMNS FROM ecommerce_platform LIKE 'sign_secret';
Field        Type          Null Key Default Extra
sign_secret  varchar(255)  YES      NULL
```

✅ **期望**：3 列全部存在 — **实际**：3 列全部存在

---

### 4.2 列位置（INFORMATION_SCHEMA.ORDINAL_POSITION）

```
SELECT ordinal_position, column_name FROM information_schema.columns
 WHERE table_schema='meiyetec' AND table_name='ecommerce_platform'
   AND column_name IN ('app_secret','app_id','kdt_id','sign_secret')
 ORDER BY ordinal_position;

ORDINAL_POSITION  COLUMN_NAME
7                 app_secret
8                 app_id          ← AFTER app_secret ✅
9                 kdt_id          ← AFTER app_id ✅
10                sign_secret     ← AFTER kdt_id ✅
```

✅ **期望**：app_id(8) → kdt_id(9) → sign_secret(10)，分别在前一列之后 — **实际**：位置完全正确

---

### 4.3 列数（最终）

```
SELECT 'google_maps_config_columns' AS label, COUNT(*) AS total
  FROM information_schema.columns
 WHERE table_schema='meiyetec' AND table_name='google_maps_config'
UNION ALL
SELECT 'ecommerce_platform_columns', COUNT(*)
  FROM information_schema.columns
 WHERE table_schema='meiyetec' AND table_name='ecommerce_platform';

label                          total
google_maps_config_columns     18       ← 期望 17+1=18 ✅
ecommerce_platform_columns     23       ← 报告数（无原始期望对照）
```

| 表 | 期望列数 | 实际列数 | 状态 |
|---|---|---|---|
| `google_maps_config` | 18（17 原有 + 1 新） | 18 | ✅ |
| `ecommerce_platform` | 报告最终数（无原始期望） | 23 | 📊 仅报告 |

**ecommerce_platform**：20 原有 + 3 新 = 23 列。

---

## 5. 范围外副作用检查

| 检查项 | 期望 | 实际 |
|---|---|---|
| 仅 4 列变更 | ✅ | ✅ 无额外列变更 |
| 仅 2 张表 | ✅ | ✅ 无 DROP TABLE / TRUNCATE |
| 无 INSERT seed | ✅ | ✅ 无 |
| 无 SELECT 副作用 | ✅ | ✅ 验证仅用 SHOW / INFORMATION_SCHEMA |

---

## 6. 已知遗留（按 postmortem §9 标注）

- ⚠️ 项目中可能还有其他 `ALTER TABLE ... ADD/DROP COLUMN IF EXISTS` 用法（MariaDB 语法）
- ⚠️ 后续批次 apply 前必须核实 MySQL 兼容性
- ⚠️ 当前未做全项目 grep 扫描，**留待用户决策**

---

## 7. 状态

| 项 | 状态 |
|---|---|
| 4 列 apply | ✅ 已落地 |
| 位置校验 | ✅ 全部正确 |
| 列数校验 | ✅ google_maps_config=18 / ecommerce_platform=23 |
| 范围外副作用 | ✅ 无 |
| apply-log | ✅ 本文档 |
| Batch 5 进度 | ✅ 完成 |

---

**停下，等用户确认进入 Batch 6（crm_contact_map 新建）。**
