# I128-b — 字符集乱码遗留问题清单

> **状态**：分类 D 的**业务表乱码已修复**（101 行）；分类 A/B/C/E 维持原裁决
> **来源**：I128 战役只读调查（2026-11-05）+ I128 主修复执行后的 verify 残留扫描 + I128-b 收口批（2026-11-07）
> **裁决人**：用户（2026-11-05 拍板；2026-11-07 批准分类 D 全部修复 + 根因处理）
> **本批已处理**：分类 D 的 4 表 5 列共 101 行（修复方式与依据见 §8）
> **仍不处理**：分类 A / B / C / E（见各节裁决）
> **根因批量加固（2026-11-08）**：`db/sql` 全量 176 个 `.sql` 已补齐 `SET NAMES utf8mb4;`（165 个新增 / 11 个原有跳过），详见 **§10**

---

## 0. 背景

I128 主批次已修复 **9 张核心字典/主数据表**的乱码，详见 `backup/i128/fix-i128-charset.sql` 与 `backup/i128/fix-i128-verify.sql`。

主批次覆盖：

| # | 表 | 修复行数 | 状态 |
|---|---|---|---|
| 1 | `sys_user` | 4 | ✅ 已修复 |
| 2 | `sys_role` | 5 | ✅ 已修复 |
| 3 | `sys_role_data_scope` | 4 | ✅ 已修复 |
| 4 | `sys_permission` | 11 | ✅ 已修复 |
| 5 | `pos_refund_audit_rule` | 21 | ✅ 已修复 |
| 6 | `complaint_category` | 8 | ✅ 已修复 |
| 7 | `payment_channel` | 8 | ✅ 已修复 |
| 8 | `hq_dashboard_widget` | 6 | ✅ 已修复 |
| 9 | `attendance_config` | 1 | ✅ 已修复 |

verify 脚本的 **B 段（残留字面 `?` 扫描）** 与 **C 段（残留 GBK 误解码扫描）** 对这 9 张表均**无任何输出**，即 9 表乱码已彻底清空。

本文件登记**范围外**的遗留项。

---

## 1. 分类 A：测试脏数据（低优先级 — 本批次不处理）

> **裁决**：本批次不处理。这些是自动化测试产生的脏数据，等以后有**统一的数据清理批次**时一并处理（届时大概率是 DELETE 而非 UPDATE）。

| 表.列 | 行数 | 样例 | 备注 |
|---|---|---|---|
| `crm_member.name` | 2 | `????` / `????001` | 会员名被替换成 `?` |
| `mkt_coupon_batch.coupon_name` | 1 | `5A-F131 ?????` | 测试批次名 |
| `mkt_invitation_rule.rule_name` | 1 | `鑰佸甫鏂伴?鐢ㄨ?鍒` | GBK 误解码 |
| `mkt_journey.journey_name` | 1 | `5A F124 ??-???` | 测试旅程 |
| `marketing_touch_task.campaign_name` | 1 | `5A-F129 ????` | 测试活动 |
| `pos_order_item.employee_name` | 1 | `???` | 员工名 |
| `report_export_log.name` | 1 | `P0??-????` | 导出任务名 |
| `report_export_task.report_name` | 2 | `??????` / `????` | 导出任务名 |
| `pos_order.remark` | 5 | `E2E ????` / `Fix-????` | 订单备注 |
| `sys_login_log.username` | 2 | `<?ALL??>` / `<????>` | 历史登录名 |
| `crm_wallet_transaction.pay_method` | 1 | — | 支付方式 |
| `v_wallet_drift.name` | 2 | — | 视图字段 |
| `mkt_channel_template.subject` / `content` / `audit_remark` | 1 / 2 / 1 | — | 营销文案模板 |

**后续处理建议**：统一数据清理批次中，按 `?` 密度 + 命名模式（如 `5A-F1xx`、`E2E`、`Fix-`）识别测试数据并 DELETE，而非 UPDATE 成中文。

---

## 2. 分类 B：指标口径 SQL 片段（**绝对不改** — 高风险）

> **裁决**：**绝对不改**。这是指标口径，改了可能破坏业务查询结果。留待**人工逐条比对**后再决定。

| 表.列 | 行数 | 风险说明 |
|---|---|---|
| `report_metric_caliber.sql_template` | 8 | SQL 模板片段，乱码若在标识符/中文注释中，盲目替换会改变查询语义 |
| `report_metric_caliber.formula` | 3 | 计算公式，同上 |
| `report_metric_caliber.unit` | 3 | 指标单位（部分为 GBK 误解码假阳性，见 §5） |

**后续处理建议**：必须由业务负责人逐条阅读原始 SQL 片段，人工确认乱码位置是否影响语义后再手工修复。**禁止任何自动化批量替换**。

---

## 3. 分类 C：历史审计日志（**绝对不改** — 破坏审计链）

> **裁决**：**绝对不改**。历史审计日志是事实记录，改了破坏审计链。**维持现状**。

| 表.列 | 行数 | 说明 |
|---|---|---|
| `sys_log.error_msg` | 192 | 异常堆栈中的中文提示 |
| `sys_log.request_params` | 35 | 请求参数 JSON 中的中文 |
| `sys_log.module` / `sys_log.description` | 12949 | GBK 误解码（`鏈嶅姟` 类），但属历史操作记录 |
| `sys_log` 全表行数 | — | 建议由运维按**日志保留策略**定期清理，而非逐行修字符 |

**后续处理建议**：不修字符，走日志保留期滚动删除（`sys_config` 中 `log.retention-days = 90`）。

---

## 4. 分类 D：其他业务表乱码 —— ✅ 业务表乱码已于本批（I128-b 收口批）修复

> **本批结论（2026-11-07）**：分类 D 中的**业务表乱码已全部修复**（共 **101 行**，5 列 4 表）。
> 关键突破是**逆向解码可证明 100% 可逆**（见 §8.2），无需外部权威源即可字节级还原原文。
> 其余分类 D 条目（`stock_product.unit` / `complication_sop.responsible_role` /
> `biz_spa_scene_mode.*` / `industry_template.*` / `mh_drug_device.specification`）
> **仍未修复**，不在本批范围。

### 4.1 本批已修复（101 行）

| 表.列 | 修复行数 | 修复方式 | 状态 |
|---|---|---|---|
| `biz_service_item.service_name` | 22 | 逆向解码还原（字节级精确） | ✅ 已修复 |
| `biz_service_item.unit` | 22 | 逆向解码还原（全部为「次」） | ✅ 已修复 |
| `biz_service_category.category_name` | 5 | 逆向解码还原 | ✅ 已修复 |
| `biz_treatment_plan.plan_name` | 26 | 逆向解码还原 | ✅ 已修复 |
| `biz_treatment_plan.description` | 26 | 逆向解码还原 | ✅ 已修复 |
| **合计** | **101** | — | ✅ |

修复脚本：`backup/i128-b/fix-i128-b-business-charset.sql`（已执行）
预览脚本：`backup/i128-b/fix-i128-b-preview.sql`（只读）
验证脚本：`backup/i128-b/fix-i128-b-verify.sql`
证据：`audit/evidence/F144-i128-b-charset-repair/`

### 4.2 本批明确排除（**非乱码，禁止改动**）

| 表.列 | 命中行数 | 真实原因 | 裁决 |
|---|---|---|---|
| `sys_permission.permission_name` | 51 | **全部 51 行**都是 `PermissionAutoRegistrar` 自动注册的**纯英文** `module·action`（如 `bnpl·cancel`、`commission·settlement:batch-confirm`），**0 行含中文**。判定用的 HEX 正则 `C[23][89AB][0-9A-F]` 命中的是 `·` 的编码 `C2B7`（`B` 落在 `[89AB]`）—— **假阳性** | **不按乱码修复**（用户 2026-11-07 裁决：跳过）。这 51 行属**本地化缺失**（非编码问题），需另立命名/中文标签批次处理 |

> **勘误（2026-11-07 实测更正）**：本节初版写作「其中 26 行是『合法中文名 + `·` 分隔符』，另 25 行是纯英文」，
> 与实测不符。重新核验（`HEX(permission_name) REGEXP 'E[4-9][0-9A-F][89AB][0-9A-F]'` 判 CJK）：
> **51 行中含真中文的为 0 行**，`sys_permission` 全表 476 行里 470 行含中文、51 行含 `·`。
> 另：`backup_sys_permission_bak_20261106` / `_b` 两张备份表中这 51 行的名称与当前完全一致，
> 说明 `·` 命名自 2026-09-08 写入起就是如此，**与编码事故无关**，仓库内也不存在这 51 个码的中文名权威源。

### 4.3 仍未修复（不在本批范围）

| 表.列 | 命中行数 | 处置建议 |
|---|---|---|
| `stock_product.unit` | 17 | 需权威源（单位字典） |
| `complication_sop.responsible_role` | 5 | 需权威源 |
| `biz_spa_scene_mode.scene_icon` / `scene_name` | 4 / 4 | 需权威源 |
| `industry_template.icon` / `industry_name` | 5 / 5 | 需权威源 |
| `mh_drug_device.specification` | 2 | 需权威源 |
| `booking_resource.location` | 1 | **假阳性**（见 §5） |

> **后续处理建议**：按「表为单位」逐个推进，每张表先定位权威初始化脚本，再逐行核对后生成精确 `WHERE` 的 UPDATE。

---

## 5. 分类 E：合法 `?`（假阳性 — 不可改）

> **裁决**：这些 `?` 是**合法内容**，不是乱码。禁止改动。

| 表.列 | 行数 | `?` 的真实含义 |
|---|---|---|
| `mh_visual_archive.image_url` | 3 | URL 查询串参数分隔符 |
| `mh_visual_archive.thumbnail_url` | 3 | 同上 |
| `rfm_batch_log.remark` | 2 | SQL 报错文本中的 JDBC 占位符 `member_id = ?` |
| `rfm_batch_task.remark` | 2 | 同上 |
| `report_export_task.error_msg` | 1 | 同上 |
| `booking_resource.location` | 1 | **GBK↔UTF-8 往返假阳性**：值 `3楼 VIP1`（hex `33E6A5BC2056495031`）正确；「楼」的 GBK 字节 `C2A5` 恰为 UTF-8 的 `¥`，导致自动判据误报 |

---

## 6. 技术备忘：本次踩过的坑（供后续批次参考）

### 6.1 三类乱码的判别方法

| 类型 | 特征 | 判别 SQL | 可逆性 |
|---|---|---|---|
| **B 类 · 字面 `?`** | 字节丢失成 `0x3F` | `col LIKE '%?%'` | ❌ 不可逆，必须权威源重填 |
| **A 类 · GBK 误解码** | 字节完整，仅解读错 | `CONVERT(CAST(CONVERT(col USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL AND BINARY col <> BINARY CONVERT(...)` | ✅ 可逆 |
| **假阳性** | 正常中文往返伪影 | 同上，但反转结果为 `¥`/`ƿ` 等非中文 | ❌ 禁止改动 |

### 6.2 ⚠️ 只用 `LIKE '%?%'` 会漏掉纯 GBK 误解码

本次主批次首轮扫描只用了 `LIKE '%?%'`，**漏掉了 5 行**纯 GBK 误解码：

| id | permission_code | 坏值（不含 `?`） | 正确值 |
|---|---|---|---|
| 1313 | `employee:level:benefit` | `绛夌骇鏉冪泭` | 等级权益 |
| 1314 | `employee:level:eval` | `绛夌骇璇勫畾` | 等级评定 |
| 1318 | `material:binding` | `椤圭洰鑰楁潗閰嶇疆` | 项目耗材配置 |
| 1320 | `material:binding:edit` | `鑰楁潗缁戝畾-缂栬緫` | 耗材绑定-编辑 |
| 1321 | `material:binding:del` | `鑰楁潗缁戝畾-鍒犻櫎` | 耗材绑定-删除 |

这 5 行在 verify 的 C 段（全库 CONVERT 比对兜底扫描）中被发现并已补修。

**教训**：后续任何乱码盘点，**必须同时跑 `?` 扫描 + CONVERT 往返比对扫描**，缺一不可。

### 6.3 中文值反转后须校验是「纯中文」

`REGEXP '^[一-鿿]+$'` 过滤可排除大部分假阳性（如「瓶」反出 `ƿ`、「3楼」反出 `3¥`）。但**带连字符/数字的值会被误排**（如 `耗材绑定-编辑` 不匹配纯中文正则），因此最终以**权威源逐条比对**为准。

### 6.4 PowerShell 执行 MySQL 的编码坑

| 坑 | 现象 | 正确做法 |
|---|---|---|
| `Set-Content` 默认编码 | 中文变 `?` | `[System.IO.File]::WriteAllText($p, $c, (New-Object System.Text.UTF8Encoding($false)))` |
| 文件带 BOM | `ERROR 1064` / `Unknown command '\U'` | 必须 UTF-8 **无 BOM** |
| `\|` 管道送 mysql | 中文被重编码 | 用 `cmd /c "mysql ... < file.sql"` |
| PowerShell `<` 重定向 | 语法错误（不支持） | 同上，走 `cmd` |
| `Get-Content` 不指定编码 | 中文乱码 | 必须 `-Encoding UTF8` |

---

## 7. 汇总：后续批次建议拆分

| 批次 | 内容 | 优先级 | 风险 |
|---|---|---|---|
| **I128-c** | 测试脏数据清理（分类 A） | 低 | 低（DELETE，需先确认无业务依赖） |
| **I128-d** | 业务表乱码逐表甄别修复（分类 D） | 中 | 中（需逐表权威源）—— **I128-b 收口批已修复其中 101 行（4 表 5 列），其余见 §4.3** |
| **I128-e** | `report_metric_caliber` 人工逐条比对（分类 B） | 低 | **高**（需业务负责人参与） |
| **不修复** | `sys_log` 历史日志（分类 C） | — | — |
| **不修复** | 合法 `?`（分类 E） | — | — |

---

## 8. I128-b 收口批（2026-11-07）：根因与还原映射依据

### 8.1 根因：种子 / 迁移 SQL 缺失 `SET NAMES utf8mb4`

#### 8.1.1 全库扫描证据

`backend/src/main/resources/db/sql/` 共 **157** 个 `.sql` 文件，其中 **147 个**没有 `SET NAMES`：

| 已有 `SET NAMES utf8mb4`（10 个） | 缺失（147 个，覆盖全部 `init_*` / `migrate_*` / `seed_*`） |
|---|---|
| `fix_menu_chinese_encoding.sql`、`fix_menu_tree_restructure.sql`、`fix_cross_store_redemption_amount.sql`、`init_m05_package_part2.sql`、`init_m05_seed.sql`、`migrate_m01_04_booking_store_id.sql`、`migrate_m04_04_package_suspend_status_log.sql`、`migrate_p4_02_member_lifecycle.sql`、`migrate_qpm_006_rfm_batch_log.sql`、`migrate_qpm_007_rfm_batch_task.sql` | 其余全部，包括 `init_m01_auth.sql` ~ `init_rbac_v3_0_p3_06.sql` 全系列 |

> 规律很清楚：**凡是被人工修过一次的脚本都补上了 `SET NAMES`，没被修过的全都没有。**

> **⚠️ 计数更正（2026-11-08）**：本节的「157 个 .sql / 147 个缺失」是 **2026-11-05 调查快照口径**，
> 与目录**全量**不等价。本次（2026-11-08）实测全量为 **176 个**（顶层 173 + `audit-subset/` 3），
> `git ls-tree` 在 HEAD 与本次改动前的多个 ref 上均显示 176 —— 即**目录内容未变，是当时的扫描口径少计**。
> 本次批量加固**以全量 176 为准**，详见 §10。§8.1.1 的历史数字保留原样以备追溯。

#### 8.1.2 时间线证据

| 事实 | 取证 |
|---|---|
| 全部乱码行的 `create_time` **完全相同** | `biz_service_item` 22 行 = `2026-08-13 01:12:45`；`biz_treatment_plan` 26 行 = `2026-08-13 01:13:05` |
| 同批次写入的 `biz_service`（10 行）**中文正常** | 该表由另一条路径写入，未经过 latin1 客户端 |
| 2026-11-06 之后由**应用**写入的行**全部正常** | `ZZ001` / `ZZ002` / `JSONREG001` / `F143SV001`…（含中文「面部护理基础版」「F143 JSON大报文回归2」） |
| 判定用正则存在**假阳性** | `HEX(x) REGEXP 'C[23][89AB][0-9A-F]'` 会命中 `·`（U+00B7 = `C2B7`，`B` 落在 `[89AB]`） |

#### 8.1.3 成灾机制

1. `docker-compose.yml` 把 `./backend/src/main/resources/db/sql` 挂载为 `/docker-entrypoint-initdb.d`（只读）。
2. 首次初始化空卷时，MySQL 8.0 官方镜像的 entrypoint 逐个执行目录下的 `.sql`。
3. 客户端**未指定 `--default-character-set`**，也**文件内无 `SET NAMES`** → 客户端按自身默认字符集解释文件字节。
   - 容器内 locale 为 POSIX/C → 客户端默认 **latin1**；
   - Windows 本地 `mysql < file.sql` → 可能按 **GBK**（cp936）解释。
4. UTF-8 字节被逐字节误解码 → 服务端再以 UTF-8 编码入库 → **乱码**。

> **同源历史事故**：`fix_menu_chinese_encoding.sql` 开头第 4-6 行自述「之前 `fix_menu_permission_complete.sql` 写入的 118 条 `permission_name` 是 `'????'`（HEX = `3F3F3F3F`），因为 mysql 客户端按 GBK 解析了 UTF-8 文件」。**与本批 101 行是同一个根因、两条不同表现路径。**

#### 8.1.4 本批已做的根因加固

| 文件 | 动作 | 效果 |
|---|---|---|
| `backend/src/main/resources/db/sql/init_rbac_v2_1_a.sql` | 文件头补 `SET NAMES utf8mb4;` | 该种子文件今后无论以何种客户端字符集加载都不会再污染。**原第 195 行经字节级校验本就是合法 UTF-8（`'美白亮肤 8 次套餐'`），未作任何改动** |
| 其余 146 个 `init_*` / `migrate_*` / `seed_*` | **未改**（当时超出本批授权范围） | ✅ **已于 2026-11-08 全部补齐**（含实际全量 176 个中的 165 个，详见 §10） |

### 8.2 还原映射依据：逆向解码的可逆性证明

#### 8.2.1 乱码的生成模型（可逆的前提）

```
原始文本  ──UTF-8 编码──▶  B = [b0, b1, ... bn]      (每字节 0x00-0xFF)
                                │
                                ▼  被 latin1 / cp1252 逐字节误解码
                          C = [c0, c1, ... cn]          (每个 ci 是 0x80-0xFF 的"字符")
                                │
                                ▼  服务端按 UTF-8 编码入库
                          落库值 = UTF8(C)
```

**关键**：`C` 与 `B` 是一一映射（latin1 与 cp1252 的高位槽位都能反查回原字节），
且 `B` 未丢失任何字节 → **逆运算唯一确定**：

```
逆向：落库值 = UTF8(C)  ⇒  C = decode_utf8(落库值)  ⇒  B = map_cp1252_or_latin1(C)  ⇒  原文 = decode_utf8(B)
```

#### 8.2.2 判定为「真乱码且可逆」的三个充要条件

一条记录只有在**同时满足**下列三条时，才允许用十六进制字面量改写：

| # | 条件 | 实现 |
|---|---|---|
| 1 | 逆向映射后得到**合法 UTF-8 字节序列** | `Buffer.from(text,'utf8')` 与原字节**逐字节相等**（往返一致） |
| 2 | 逆向结果**含 CJK**（`U+4E00-U+9FFF`） | 排除「还原出来是英文/符号」的伪还原 |
| 3 | 逆向结果 **≠ 原值** | 排除「本来就没坏」的值 |

#### 8.2.3 实测结果

| 表.列 | 命中行数 | 满足条件 1 | 满足条件 2 | 满足条件 3 | 结论 |
|---|---|---|---|---|---|
| `biz_service_item.service_name` | 22 | 22 | 22 | 22 | ✅ **100% 可逆** |
| `biz_service_item.unit` | 22 | 22 | 22 | 22 | ✅ **100% 可逆** |
| `biz_service_category.category_name` | 5 | 5 | 5 | 5 | ✅ **100% 可逆** |
| `biz_treatment_plan.plan_name` | 26 | 26 | 26 | 26 | ✅ **100% 可逆** |
| `biz_treatment_plan.description` | 26 | 26 | 26 | 26 | ✅ **100% 可逆** |
| `sys_permission.permission_name` | 51 | **0** | — | — | ❌ **全部不满足条件 1** |

**`sys_permission` 为何 0 行可逆**：该表命中的 51 行里，唯一的高位字节是 `·`（U+00B7，UTF-8 = `C2 B7`）。
逆向时 `C2` → 字节 `0xC2`，`B7` → 字节 `0xB7`；但 `0xB7` 是**孤立的 UTF-8 后续字节**（前面没有起始字节），
重新按 UTF-8 解码必然失败 → `not-utf8-after-reverse`。
**这 51 行不是乱码**：26 行是「合法中文名 + `·` 分隔符」，25 行是 `PermissionAutoRegistrar` 自动注册的纯英文 `module·action`。
把它们"还原"成中文既**无字节依据**，也**无权威源**，属于禁止猜测的范畴。

#### 8.2.4 手工推导抽样（`biz_service_item.id = 1`）

```
原始        基础面部清洁
UTF-8       E5 9F BA | E7 A1 80 | E9 9D A2 | E9 83 A8 | E6 B8 85 | E6 B4 81
latin1误解  å Ÿ º     ç ¡ €     é Ý ¢     é ƒ ¨     æ ¸ …     æ ´ \x81
落库UTF-8   C3A5 C5B8 C2BA C3A7 C2A1 E282AC C3A9 C29D C2A2 C3A9 C692 C2A8
            C3A6 C2B8 E280A6 C3A6 C2B4 C281     ← 与库中 HEX 完全一致
逆向        逐码点映射回单字节 → E59FBAE7A180E99DA2E983A8E6B885E6B481 → 基础面部清洁 ✅
```

#### 8.2.5 与 §6.1「需权威源」路线的区别

本批**不需要外部权威源**，原因是可逆性是**字节级数学性质**而非语义猜测：
还原值 = 原始字节的**唯一**逆运算结果，唯一的风险是「原文本身就是乱码」（双层编码）。
该风险已通过三重交叉验证排除：

| 验证 | 结果 |
|---|---|
| ① 事务内 ROLLBACK 干跑 | 101 条 UPDATE 全部命中 1 行；5 列残留计数**全为 0**；CJK 覆盖 **101/101**；`HEX()` 回读字节全部合法 UTF-8 |
| ② 语义抽查 | 还原值全部为通顺中文（如 `基础面部清洁` / `焕颜亮白 10 次套餐` / `针对色斑、暗沉，含美白淡斑 10 次`），无「空/短/乱码」异常 |
| ③ 与同源数据比对 | 同批次的 `biz_service`（10 行，`SV0001`-`SV0005`）中文正常，其 `service_name`（`基础面部护理`/`深层补水护理`…）与 `biz_service_item` 的还原值**语义同族但不相同**，说明两表是**独立数据集**，还原未串表 |

### 8.3 修复脚本形态（与 §6.4 的坑对应）

| 要点 | 做法 | 防的是什么 |
|---|---|---|
| 字符集声明 | 文件头 `SET NAMES utf8mb4;` + `USE meiyetec;` | 客户端默认字符集（latin1/GBK）二次污染 |
| 中文写入 | 一律 `CONVERT(0x<HEX> USING utf8mb4)` | 让客户端字符集**完全不参与**解释 |
| 匹配条件 | `WHERE id = N AND BINARY col = BINARY 0x<原HEX>` | 双重匹配：既锁定主键，又锁定旧字节值 → 误伤正常行；且**幂等**（重跑不命中） |
| 事务 | `START TRANSACTION;` … `COMMIT;` | 可整体回滚 |
| 验证 | 独立 `-verify.sql`：残留计数 + `HEX()` 回读 + CJK 覆盖 + 行数守恒 | 修复前后可比对 |
| 文件编码 | UTF-8 **无 BOM** | `ERROR 1064` / `Unknown command '\U'` |

---

## 9. I128-b 收口批执行记录（2026-11-07）

> **状态**：分类 D 业务表乱码 **101 行已修复并验证通过**（VERIFIED）。
> `sys_permission` 51 行经实测为假阳性，**本批不动**（用户裁决：跳过）。

### 9.1 落盘文件

| 文件 | 用途 |
|---|---|
| `backup/i128-b/fix-i128-b-preview.sql` | 只读预览：逐行「当前乱码值 / 还原目标值」对照（127 行） |
| `backup/i128-b/fix-i128-b-business-charset.sql` | 正式修复：101 条 `UPDATE`，事务包裹（332 行） |
| `backup/i128-b/fix-i128-b-verify.sql` | 修复后验证：残留计数 + `HEX()` 回读 + 中文抽查 + 行数守恒（35 行） |
| `audit/evidence/F144-i128-b-charset-repair/` | 执行前后证据 |

### 9.2 执行时序

| 步骤 | 动作 | 结果 |
|---|---|---|
| ① | 跑 `fix-i128-b-preview.sql`（只读） | A 段基线 `22 / 22 / 5 / 26 / 26`；B 段 101 行逐行对照输出正常 |
| ② | 事务级干跑（`COMMIT` 替换为 `ROLLBACK`） | 事务内残留计数全 0、中文回读正确；`ROLLBACK` 后恢复 `22/22/5/26/26` → **可逆性 + 无误伤** |
| ③ | 跑 `fix-i128-b-business-charset.sql`（正式 `COMMIT`） | 执行无报错 |
| ④ | 跑 `fix-i128-b-verify.sql` | A 段 5 列 `should_be_0` **全部为 0**；B 段 `HEX()` 回读字节合法；C 段中文全部正常；D 段行数守恒 `32 / 5 / 26`（与修复前一致） |
| ⑤ | **幂等复跑** fix 脚本 | 无报错、数据不变、残留计数仍为 0 → 脚本可重复执行 |
| ⑥ | 应用层（HTTP）复验 | **未执行** —— 本次会话无可用有效登录凭据（`/auth/login` 返回 `5810 密码错误`，本地历史 token 已失效），因此不声称应用层结论；数据源与应用读取表一致，且本次提交不含任何 Java 改动 |

### 9.3 修复后抽样（DB 回读）

```
id  service_code  service_name      unit
1   SV001         基础面部清洁       次
9   SV009         身体按摩 60 分钟   次
22  SV022         测试待上线服务     次

id  category_code  category_name
1   FACE           面部护理
5   OTHER          其他

id  plan_code  plan_name              description
1   TP001      焕颜亮白 10 次套餐     针对色斑、暗沉，含美白淡斑 10 次
25  TP025      乳腺疏通 6 次（停用）   暂时下架
26  TP026      测试方案（停用）       占位方案
```

### 9.4 本批根因处理

| 项 | 动作 |
|---|---|
| `backend/src/main/resources/db/sql/init_rbac_v2_1_a.sql` | 文件头新增 `SET NAMES utf8mb4;` + 说明注释。**未做字符替换** —— 全文件字节级往返校验 `13536 → 13536`（纯 UTF-8），第 195 行 `'抗皱紧致疗程'` 码点 `U+6297 U+76B1 U+7D27 U+81F4 U+7597 U+7A0B` 全部正确，**不存在 GBK 残留** |
| `audit/issues/I140-ai-enhance-blood-line-drift.md` | 新建：`AiEnhanceService.java:129` JOIN `biz_service_item` 而 `pos_order_item.service_id` 实指 `biz_service`（静默错数据）。**未改业务代码**，状态 Open / UNVERIFIED |
| 遗留根因面 | `backend/src/main/resources/db/sql/` 全量 **176** 个 `.sql` 中 **165 个缺 `SET NAMES`**。本批只加固了根因文件的 `init_rbac_v2_1_a.sql` → ✅ **已于 2026-11-08 批量补齐（165/165），见 §10** |

### 9.5 尚未收口项

| 项 | 状态 |
|---|---|
| `sys_permission` 51 行 `·` 命名 | 本批**跳过**，待另立本地化批次（无中文权威源，不猜测） |
| `stock_product.unit`(17) / `complication_sop.responsible_role`(5) / `biz_spa_scene_mode.*`(4+4) / `industry_template.icon`/`industry_name`(5+5) / `mh_drug_device.specification`(2) | 需权威源，未修复 |
| `db/sql` 剩余脚本补 `SET NAMES utf8mb4` | ✅ **已收口**（2026-11-08，165 个全部补齐，见 §10） |
| I140 `AiEnhanceService` 血缘漂移 | ✅ **已修复并 VERIFIED**（2026-11-07，见 `audit/evidence/F145-i140-ai-enhance-fix/`）；衍生遗留已拆分为 I141 / I142 / I143 |

---

## 10. I128-b 根因批量加固（2026-11-08）：`db/sql` 全量补 `SET NAMES utf8mb4`

> **状态**：✅ **VERIFIED**（落盘重扫 + 临时库实跑 + 字节级反证三项均通过）
> **授权**：用户 2026-11-08 批准（DryRun 报告 176/176 通过后批准 `-Apply`）
> **范围**：`backend/src/main/resources/db/sql/`（不含 Flyway `db/migration/`）

### 10.1 目标

§8.1.3 认定的成灾机制是「客户端字符集由客户端默认值决定」（容器 POSIX locale → latin1；Windows 本地 → cp936）。
既然文件端无法控制客户端，就必须在**文件内部显式声明**客户端字符集 ——
即在每个 `.sql` 的语句区最前面加 `SET NAMES utf8mb4;`，使客户端无论以何种字符集加载都按 UTF-8 解释字节。

### 10.2 工具：`scripts/add-set-names.ps1`（一次性迁移工具，非 CI 守门脚本）

| 项 | 内容 |
|---|---|
| 用途与场景 | 为 `db/sql` 目录下的全部 `.sql` 补齐 `SET NAMES utf8mb4`；本批一次性执行完毕 |
| 参数 | `-Root`（默认 `backend/src/main/resources/db/sql`）、`-DryRun`（默认）、`-Apply`、`-IncludeSubDir`（默认 `$true`）、`-ReportPath` |
| 退出码 | `0` = 全部成功；`1` = 有文件处理失败；`2` = 参数错误（如 `-DryRun` 与 `-Apply` 同给、Root 不存在） |
| 是否接入 CI / PR 必跑清单 | **否**。一次性迁移工具，已执行完毕即失效，不进 CI |
| 失败时处理方式 | 脚本**不阻断**：单文件失败仅计入 `ERROR` 并写报告，其余文件继续；退出码非 0 由人工判读 |
| 关键约束 | 幂等（已含 `SET NAMES` 直接 SKIP）、保行尾（沿用文件自身主导行尾）、UTF-8 无 BOM 写回、严格 UTF-8 解码（失败即跳过不猜） |
| 排除规则 | Flyway `backend/src/main/resources/db/migration/V1__init_baseline.sql`（改动会破坏 Flyway checksum）+ `backend/target/*`（编译产物） |
| 文件编码 | UTF-8 **with BOM**（与仓库既有 `scripts/backup-db-docker.ps1` 一致；PowerShell 5.1 解析 UTF-8 脚本必须有 BOM，否则中文注释导致语法错误） |
| 仓库入库状态 | ⚠️ **未入库** —— `.gitignore` L143 的全局 `*.ps1` 规则拦截 `scripts/*.ps1`；`.gitignore` 属治理保护文件（未经用户单独批准不得修改），本批**未**改 `.gitignore`，故原始工具留在工作区不入库。证据目录内已归档**只读副本** `audit/evidence/F146-i128-b-set-names-db-sql/add-set-names.ps1`（文件头已标注「非正式工具」） |

### 10.3 执行统计

| 阶段 | 候选 | SKIP | INSERT | EXCLUDE | ERROR | 退出码 |
|---|---|---|---|---|---|---|
| DryRun（审核前） | 176 | 11 | 165 | 0 | 0 | 0 |
| Apply（落盘） | 176 | 11 | 165 | 0 | 0 | 0 |

> 176 = 顶层 173 + `audit-subset/` 3。
> SKIP 的 11 个是**原本已含** `SET NAMES` 的文件（含 I128-b 批加固的 `init_rbac_v2_1_a.sql`），**逐字节未被触碰**。

### 10.4 落盘后重扫验证（独立于脚本自身的断言）

| 检查项 | 结果 | 期望 |
|---|---|---|
| 缺 `SET NAMES` 的文件数 | **0** | 0 |
| UTF-8 BOM 文件数 | **0** | 0 |
| 非法 UTF-8 文件数 | **0** | 0 |
| 行尾分布 | **CRLF=164 / LF=12** | 与改前完全一致（164/12） |
| `git diff --numstat` | 165 个文件，**全部恰好 `+2 / -0`** | 每文件仅增 `SET NAMES utf8mb4;` + 空行 |

### 10.5 临时库试跑（真实 MySQL 8.0.46 容器）

在 `meiyetec-mysql` 容器内建 3 个**一次性**临时库（`tmp_setnames_test` / `tmp_setnames_ref` / `tmp_setnames_nofix`），
对 6 个文件做实跑；测试副本仅把 `USE meiyetec` / `CREATE DATABASE ... meiyetec` **重定向到临时库**，
`SET NAMES` 行与正文**逐字节未改**（`docker cp` 传输，保留原始字节）。

| # | 文件 | 角色 | 结果 |
|---|---|---|---|
| 1 | `init_m01_auth.sql` | 顶层建库 + 用户/角色/权限种子 | ✅ exit 0，中文正常（`系统管理员` / `超级管理员` / `首页` / `收银结算` …） |
| 2 | `create_missing_tables_b4b5.sql` | 纯 DDL（23 张表） | ✅ exit 0 |
| 3 | `init_rbac_v2.sql` | 原含 `SET NAMES` 的 SKIP 文件（依赖补齐） | ✅ exit 0 |
| 4 | `audit-subset/migrate_ext_22_subset.sql` | ALTER（`google_maps_config` / `ecommerce_platform`） | ✅ exit 0，4 列到位 |
| 5 | `migrate_p3_pkg_order_version.sql` | ALTER + `SHOW COLUMNS` | ✅ exit 0，`version int NO 0` 到位 |
| 6 | `expire_test.sql` | UPDATE + SELECT | ✅ exit 0，`expire_time` 更新为 `2025-01-01 00:00:00` |

**字符集有效性验证（两库对照）**

| 验证 | 方法 | 结果 |
|---|---|---|
| 字节级对照 | 同一批文件分别用 `--default-character-set=latin1`（跑进 `tmp_setnames_test`）与 `--default-character-set=utf8mb4`（跑进 `tmp_setnames_ref`）执行，再对 `sys_permission.permission_name` / `sys_role.role_name` / `sys_role.description` 取 `HEX()` 比对 | **148 行逐字节全等** |
| 语义抽样 | `HEX('首页')` = `E9A696E9A1B5`、`HEX('会员管理')` = `E4BC9AE59198E7AEA1E79086` | 与源码字面量的 UTF-8 编码**逐字节一致** |
| 新增菜单抽样 | `add_6_missing_menus_20260819.sql` 写入的 6 条中文菜单（`回访任务规则` / `国际支付` / `国际预约` / `AI 智能排班` / `资金风控` / `Google Maps`） | 6/6 与源码字面量的 UTF-8 编码**逐字节一致** |
| **反证（决定性）** | 取**改动前**的原文件（`git show HEAD:...init_m01_auth.sql`，8484 字节，无 `SET NAMES`），用**同样的 latin1 客户端**跑进 `tmp_setnames_nofix` | **143/143 行**中文全部双重编码乱码（`首页` → `é¦–é¡µ`，HEX `C3A9C2A6E28093C3A9C2A1C2B5`）→ 证明 `SET NAMES` 缺失即成灾，补上即根治 |

**测试环境清理**：3 个临时库已 `DROP`，容器内 `/tmp/st_test` `/tmp/st_ref` `/tmp/st_orig_init.sql` 已删除，
本地临时目录 `tmp-setnames-run/` 已删除。**真实 `meiyetec` 库未被本次测试写入**（测试副本的库名已重定向）。

### 10.6 顺带发现（**未修，超出本批授权范围**）

`add_6_missing_menus_20260819.sql` **L40** 在两个临时库上**报同一错**：

```
ERROR 1136 (21S01) at line 40: Column count doesn't match value count at row 1
```

原因：该行 `INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)`
声明 **6 列**，而 `SELECT` 只提供 **4 个值**（`id, '/crm/care/follow-rule', 'SetUp', 99`），缺 `hidden` / `keep_alive`。
**该缺陷在改动前的原文件中即存在，与 `SET NAMES` 无关**（两库同错可证）。
§10.5 中该文件的**字符集部分仍然通过**（A 段 6 条中文菜单已正确入库并通过 HEX 校验），仅 B 段失败。
→ **待另立 issue 处理**，本批不改。

### 10.7 证据

| 文件 | 内容 |
|---|---|
| `audit/evidence/F146-i128-b-set-names-db-sql/` | 本批完整证据（DryRun 报告 / Apply 报告 / 重扫输出 / 试跑输出 / 字节比对 / 反证） |
