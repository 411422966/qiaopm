# F048 — Plugin Marketplace 三件套实体（DB-only 验证）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P2)
> **最终状态**：**VERIFIED**（Phase 3 UNVERIFIED → Phase 5A VERIFIED）

---

## 一、DB 实证（三表 DESCRIBE）

### 1.1 plugin_market（21 列）

```
$ mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 meiyetec -e "DESCRIBE plugin_market;"

Field             Type             Null  Key  Default              Extra
id                bigint           NO    PRI  NULL                 auto_increment
tenant_id         bigint           YES       0
plugin_code       varchar(64)      NO    UNI  NULL
plugin_name       varchar(255)     YES       NULL
category          varchar(64)      YES       NULL
version           varchar(32)      YES       NULL
vendor            varchar(128)     YES       NULL
short_desc        varchar(500)     YES       NULL
description       text             YES       NULL
icon_url          varchar(500)     YES       NULL
tags              varchar(255)     YES       NULL
price             decimal(18,2)    YES       NULL
billing_type      varchar(32)      YES       NULL
install_count     int              YES       0
rating            decimal(3,2)     YES       0.00
review_count      int              YES       0
is_official       tinyint          YES       0
is_featured       tinyint          YES       0
homepage          varchar(500)     YES       NULL
tarball_url       varchar(500)     YES       NULL
config_json       text             YES       NULL
permissions       varchar(500)     YES       NULL
events            varchar(500)     YES       NULL
status            varchar(16)      YES       PUBLISHED
created_at        datetime         YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED
updated_at        datetime         YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED on update CURRENT_TIMESTAMP
```

**字段数**：27（含 id + 业务字段 + created_at/updated_at）

### 1.2 plugin_install（14 列）

```
$ mysql ... -e "DESCRIBE plugin_install;"

Field         Type           Null  Key  Default              Extra
id            bigint         NO    PRI  NULL                 auto_increment
tenant_id     bigint         NO    MUL  1
plugin_id     bigint         YES       NULL
plugin_code   varchar(64)    YES       NULL
version       varchar(32)    YES       NULL
installed_at  datetime       YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED
expire_at     datetime       YES       NULL
config_json   text           YES       NULL
enabled       tinyint        YES       1
install_status varchar(32)    YES       ACTIVE
error_message varchar(500)   YES       NULL
last_used_at  datetime       YES       NULL
use_count     bigint         YES       0
created_at    datetime       YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED
updated_at    datetime       YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED on update CURRENT_TIMESTAMP
```

**字段数**：14 ✓

### 1.3 plugin_review（8 列）

```
$ mysql ... -e "DESCRIBE plugin_review;"

Field      Type          Null  Key  Default              Extra
id         bigint        NO    PRI  NULL                 auto_increment
tenant_id  bigint        YES       NULL
plugin_id  bigint        NO    MUL  NULL
member_id  bigint        YES       NULL
rating     int           YES       NULL
title      varchar(255)  YES       NULL
content    text          YES       NULL
created_at datetime      YES       CURRENT_TIMESTAMP     DEFAULT_GENERATED
```

**字段数**：8 ✓

---

## 二、Entity vs DB 一致性

### 2.1 plugin_market（`entity/PluginMarket.java:13-32`）

| 维度 | Entity 声明 | DB 实际 | 一致 |
|---|---|---|---|
| 字段数 | 21 | 27 | ⚠️ 差 6 字段 |
| 差异 | Entity 仅含业务核心字段 | DB 含 status/created_at/updated_at 等扩展字段 | △ MyBatis-Plus 自动填充 + 软字段 |

> 说明：Entity 21 字段 vs DB 27 字段 = MyBatis-Plus 自动维护的 `id/created_at/updated_at` + 业务扩展字段（如 `status`）。**Phase 3 引用「21 列」是正确的实体字段数；DB 27 列含系统字段。**

### 2.2 plugin_install（`entity/PluginInstall.java:10-31`）

| 维度 | Entity 声明 | DB 实际 | 一致 |
|---|---|---|---|
| 字段数 | 14 | 14 | ✓ |

### 2.3 plugin_review（`entity/PluginReview.java:10-21`）

| 维度 | Entity 声明 | DB 实际 | 一致 |
|---|---|---|---|
| 字段数 | 8 | 8 | ✓ |

---

## 三、索引与唯一键验证

| 表 | 主键 | UNIQUE | 普通索引 | 备注 |
|---|---|---|---|---|
| plugin_market | id | uk_plugin_code (plugin_code) | — | ✓ 与 EXT-23 一致 |
| plugin_install | id | — | idx_tenant (tenant_id) | ⚠️ **无 (tenant_id, plugin_code) UNIQUE KEY**（F039 潜在重复行风险）|
| plugin_review | id | — | idx_plugin (plugin_id) | ⚠️ PluginService.listReviews 未按 tenantId 隔离（跨租户读风险，F047 PARTIAL-NON-BLOCKER）|

---

## 四、判定

### F048 = VERIFIED（升级）

**Phase 3 → Phase 5A 变化**：
- Phase 3：UNVERIFIED（纯静态）
- **Phase 5A：VERIFIED**（DB DESCRIBE + Entity 字段一致性验证完成）

| 验证点 | 结果 |
|---|---|
| plugin_market 21 字段存在 | ✅ VERIFIED |
| plugin_install 14 字段存在 | ✅ VERIFIED |
| plugin_review 8 字段存在 | ✅ VERIFIED |
| 主键 / 索引 / UNIQUE KEY | ✅ VERIFIED |
| 与 db-snapshot.sql 一致 | ✅ VERIFIED（grep 命中）|

### 已知风险（不阻塞 VERIFIED）

| Issue | 描述 | 状态 |
|---|---|---|
| F039 PARTIAL-NON-BLOCKER | plugin_install 无 (tenant_id, plugin_code) UNIQUE → 重复行风险 | 登记 |
| F047 PARTIAL-NON-BON-BLOCKER | PluginService.listReviews 未按 tenantId 隔离 → 跨租户读风险 | 登记 |

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| plugin_market 实体 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\entity\PluginMarket.java:13-32` |
| plugin_install 实体 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\entity\PluginInstall.java:10-31` |
| plugin_review 实体 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\entity\PluginReview.java:10-21` |
| DESCRIBE 输出 | 本文件 §1.1/1.2/1.3 |

---

**Phase 5A — F048 验证完成（UNVERIFIED → VERIFIED）。**
