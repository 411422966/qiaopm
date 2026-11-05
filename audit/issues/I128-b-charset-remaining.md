# I128-b — 字符集乱码遗留问题清单

> **状态**：已确认存在，但属于**高风险 / 低优先级 / 假阳性**，留待后续单独处理
> **来源**：I128 战役只读调查（2026-11-05）+ I128 主修复执行后的 verify 残留扫描
> **裁决人**：用户（2026-11-05 拍板）
> **本批次明确不处理** — 修复需等统一的数据清理批次

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

## 4. 分类 D：其他业务表乱码（50+ 行 — 需逐表甄别，低优先级）

> 以下均为**本批次 9 表之外**的业务表。多数命中来自自动判据的**假阳性**（见 §5），需先拿到权威源才能下 UPDATE。

| 表.列 | 命中行数 | 处置建议 |
|---|---|---|
| `biz_service_item.unit` | 22 | 需权威源（单位字典） |
| `stock_product.unit` | 17 | 需权威源 |
| `biz_service_item.service_name` | 22 | 需权威源 |
| `biz_treatment_plan.plan_name` / `description` | 25 / 26 | 需权威源 |
| `complication_sop.responsible_role` | 5 | 需权威源 |
| `biz_spa_scene_mode.scene_icon` / `scene_name` | 4 / 4 | 需权威源 |
| `industry_template.icon` / `industry_name` | 5 / 5 | 需权威源 |
| `mh_drug_device.specification` | 2 | 需权威源 |
| `biz_service_category.category_name` | 1 | 需权威源 |
| `biz_treatment_plan.plan_name` | 1 | 需权威源 |
| `booking_resource.location` | 1 | **假阳性**（见 §5） |
| `attendance_config.config_name` | — | ✅ 已在本批次修复 |

**后续处理建议**：按「表为单位」逐个推进，每张表先定位权威初始化脚本，再逐行核对后生成精确 `WHERE` 的 UPDATE。

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
| **I128-d** | 业务表乱码逐表甄别修复（分类 D） | 中 | 中（需逐表权威源） |
| **I128-e** | `report_metric_caliber` 人工逐条比对（分类 B） | 低 | **高**（需业务负责人参与） |
| **不修复** | `sys_log` 历史日志（分类 C） | — | — |
| **不修复** | 合法 `?`（分类 E） | — | — |
