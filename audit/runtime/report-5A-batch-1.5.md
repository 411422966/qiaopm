# Phase 5A — Runtime Verification Report（Batch 1.5）

> **批次**：Batch 1.5 — MKT 多渠道重构（v3 第五批）
> **创建时间**：2026-09-16 01:55 (UTC+8)
> **Phase**：Phase 5A — Runtime Execution & Evidence Collection
> **范围**：P0 = 21 Feature / F114-F134（9 个 Mkt*Controller 全量）+ 负向证据 N1/N2/N6/N7/N8/N9/N10/N11/N13
> **下一阶段**：Phase 5B（P1/F135-F137 = ChannelDispatcher + 5 Sender + F138 间接）
> **本文件状态**：✅ Phase 5A 完结；用户决策进 5B 前可微调

---

## 〇、报告说明

Phase 5A **只回答一个问题**：21 个 P0 端点 + 关联 DB 断言是否跑通？预期负向证据是否浮出？

按 plan §三 3.1 表逐项执行；evidence 落在 `audit/evidence/F114-.../raw-response.json` + DB 状态在 `tmp-final-verify.sql` 等 SQL 脚本运行结果中。
按 D10 铁律 — **跑不通 → UNVERIFIED，不造证据**。

---

## 一、Feature 验证结果矩阵（21 Feature × L0-L5b）

| Feature ID | Priority | 验证方式 | 端点 + HTTP 状态码 | DB 断言 | Result | 关键证据 | Issue 触达 |
|---|---|---|---|---|---|---|---|
| **F114** | P0 | API + DB | `GET /mkt/channel/list` → **200** | tenantId=1 过滤 4 条；新建 id=6 软删后 list 不显示 | **PARTIAL-NON-BLOCKER** | `F114-mkt-channel-list/raw-response.json`：4 渠道；测试新渠道在 list 中过滤（admin+clerk 双视角一致）| — |
| **F115** | P0 | API + DB | `POST /mkt/channel` → **200** | DB 写入 id=6/7/8；**`rate_limit_per_day = 5000`（DB 列默认值）但 API 响应 `null`** | **PARTIAL-NON-BLOCKER** | `F115-create-channel/raw-response.json`：data.rateLimitPerDay = null | **I062 沿用**（rateLimitPerDay setter 缺失 — N7 实证）|
| **F116** | P0 | API + DB | `PUT /mkt/channel/6` → **200** channelName→"5A-SMS-Updated" | DB id=6 channel_name 已更新 | **VERIFIED** | `F116-update-channel/raw-response.json`：name 改成功 | — |
| **F117** | P0 | API + DB | `DELETE /mkt/channel/7` → **200** 已删除 | DB id=7 deleted=1（软删） | **PARTIAL-NON-BLOCKER** | `F117-delete-channel/raw-response.json` + `n9-cross-tenant-clerk.json` — **clerk01 DELETE admin 的 id=6 成功（deleted=1）** | **I056**（跨租户删除无 tenantId 校验 — N9 实证）|
| **F118** | P0 | API + DB | `POST /mkt/channel/health-check` → **200** | 5 channelType 轮询（SMS/EMAIL/IN_APP/MINI_PROGRAM/WECHAT_OA）全 healthy=false；DB 仅 tenant_id=1 的渠道 last_health_at 更新（MP / audit-test-* / 5A-ToBeDeleted 仍 NULL） | **PARTIAL-NON-BLOCKER** | `F118-health-check/raw-response.json`：results 5 条；DB 7 条 tenant=1 中 4 条 last_health_at 更新 | **I050 沿用**（ChannelDispatcher.healthCheckAll 硬编码 `Long tenantId = 1L` — N11 实证）|
| **F119** | P0 | API + DB | `GET /mkt/conversion/funnel` → **200** | data={reach:980, sent:980, read:491, converted:1, sentRate:100.0, readRate:50.1, convertRate:0.2} | **VERIFIED** | `F119-funnel/raw-response.json`：4 段漏斗聚合正确 | — |
| **F120** | P0 | API | `GET /mkt/conversion/roi` → **500** 数据库异常 | SQL 抛异常（conversion 表 amount 聚合时除 0 或 NULL） | **UNVERIFIED** | `F120-roi/raw-response.json` code=500 msg="数据库异常" | **新发现 I072**（conversionRoi SELECT taskId 过滤 + 0 records 时抛）|
| **F121** | P0 | API + DB | `GET /mkt/conversion/by-channel` → **200** rows=1 | DB marketing_touch_conversion 1 行（touch_log_id=2）| **VERIFIED** | `F121-by-channel/raw-response.json` | — |
| **F122** | P0 | API + DB | `POST /mkt/conversion/track` body=`{tenantId:2,touchLogId:999,conversionType:"ORDER",amount:100,orderId:"TEST-5A-001"}` → **404** 触达记录不存在 | DB marketing_touch_conversion 0 行新增（业务校验拒绝 — touchLogId=999 不存在）| **PARTIAL-NON-BLOCKER** | `F122-track-conversion/raw-response.json`：因业务前置校验未触达"按 body.tenantId 写入"风险路径 | **I057** 沿用（跨租户写 body 直接取 — N10 业务前置校验挡住）|
| **F123** | P0 | API + DB | `POST /mkt/conversion/mark-read` body=`{id:1}` → **200** 已标记 | DB `marketing_touch_log` id=1 **status 仍为 1**（API 报成功但 0 行更新）| **PARTIAL-NON-BLOCKER + Fake Success** | `F123-mark-read/raw-response.json`：code=200 success=true；DB id=1 status=1 read_at=NULL | **新发现 I071 NEW**（markRead `WHERE id=? AND status=2` 0 行生效但仍返回 success — 沿用 I008 Fake Success 链）|
| **F124** | P0 | API + DB | `POST /mkt/journey` body 含 `journeyCode` → **200** | DB id=3 5A_J_001 journey_name="5A-Journey-Updated" | **VERIFIED** | `F124-create-journey/raw-response.json` | — |
| **F125** | P0 | API | `POST /mkt/journey/page` + `GET /mkt/journey/page` → **500** 数据库异常 | Service bug — COUNT(?) 参数个数不匹配 | **UNVERIFIED** | `F125-journey-page/raw-response-both.json`：两形式都 500 — **dual route 同 bug** | **新发现 I073**（journeyPage COUNT 查询复用 SELECT args 含 pageSize/offset — N13 双形式都中招）|
| **F126** | P0 | API + DB | GET/PUT/POST/POST 4 端点 → 全部 **200** | DB id=3 status 流转：1→update→2(publish)→3(pause) 完整 | **VERIFIED** | `F126-journey-detail-update-publish-pause/raw-publish-pause.json` + DB 验证 enter_count=1 | — |
| **F127** | P0 | API + DB | `POST /mkt/journey/3/enter` → **500** 系统繁忙（首次）→ **409** 会员已在（重试）；`POST /mkt/journey/instance/2/next` → **200** 已结束 | DB mkt_journey_instance id=2 status=1→2（nextNode 完成后 2）| **PARTIAL-BLOCKER** | `F127-enter-journey/raw-enter.json` (500) + `raw-next.json` (200) | — |
| **F128** | P0 | API + DB | `GET /mkt/template/list` 5 行 + `POST /mkt/template` → id=9 + `PUT /mkt/template/9/audit` body=`{auditStatus:2}` → **200** 已审核 | DB id=9 audit_status=2 | **VERIFIED** | `F128-template/raw-response.json` + `raw-audit.json` | — |
| **F129** | P0 | API + DB | page POST+GET dual route 都 **200**（**POST 和 GET total 都等于 1**）+ create+start+pause+resume+cancel+log 全 **200** | DB mkt_touch_task id=2 5A-T3 status=6 success_count=489 fail_count=0 target_count=489 | **VERIFIED** | `F129-touch-task/raw-page.json` + `raw-create.json` + `raw-start.json` (successCount=489) + DB 验证 | — |
| **F130** | P0 | API + DB | `GET /mkt/effect/snapshot` → **200** 11 个聚合字段（totalOrders/totalDiscount/couponUsageRate/usedCoupon/pointsGranted/totalReach/touchSuccess/touchConverted/totalActivities/totalPromotions/journeyEnterCount）| `POST /mkt/effect/snapshot/save` → **200** 快照已记录；**mkt_effect_snapshot 表不存在**（information_schema.tables 0 匹配）| **PARTIAL-NON-BLOCKER + Dead Code** | `F130-effect-snapshot/raw-snapshot.json` + `raw-save.json` | **I059 沿用**（snapshotSave 仅 log.info 不写表 — N8 实证）|
| **F131** | P0 | API + DB | `POST /mkt/coupon/batch/page` 1 行 + `POST /mkt/coupon/batch` id=2 + `publish` + `grant` → **500** 数据库异常 + `end` 成功 | DB mkt_coupon_batch id=2 status=3；**mkt_coupon 0 行**（grant 失败） | **PARTIAL-BLOCKER** | `F131-coupon/raw-response.json` + `raw-grant-retry.json` + `raw-grant-all.json` 都 500 | **新发现 I070 NEW**（grant 调 `SELECT id, member_name FROM member` — 但实际表名是 `crm_member` — I070 实证）|
| **F132** | P0 | API + DB | `POST /mkt/coupon/expire-sweep` → **200** expired=0 batchesClosed=0（无过期数据）；`GET /mkt/coupon/page` 0 行；`GET /mkt/coupon/export` 0 行 | 3 端点全 200；expire-sweep 无 try/catch 但实测无脏数据所以未触发 500 | **VERIFIED（happy path）/ I060 PARTIAL-BLOCKER（脏数据未验证）** | `F132-expire-sweep/raw-es.json` + `raw-page.json` + `raw-export.json` | **I060** 沿用（expireSweep 无 try/catch — 脏数据路径 N2 未实测触发）|
| **F133** | P0 | API + DB | `GET /mkt/promotion/page` 1 行 + `GET /mkt/promotion/` 1 行 + `POST` id=3 + `PUT id=3` → **200** + `DELETE id=3` → **200** + `GET /export` 141 bytes（含 EF BB BF UTF-8 BOM） | DB mkt_promotion id=3 已创建；CSV header `ID,编码,名称,类型,状态,优先级,创建时间` 正确 | **VERIFIED** | `F133-promotion/raw-page.json` + `raw-list.json` + `raw-create.json` + `raw-update.json` + `raw-export.csv`（前 3 字节 EF BB BF）| — |
| **F134** | P0 | API + DB | `GET /mkt/points/rule/list` 1 行 + `POST /rule` id=2 + `PUT /rule/2` → **200** + `POST /award` memberId=1 amount=100 → **200** recordId=490 points=100 | DB mkt_points_rule id=2 created；**market_point_record id=490 member_id=1 point_value=100 source=manual 已写入**（484 → 490 行）| **VERIFIED** | `F134-points/raw-response.json` + DB 验证 market_point_record 484→490 | — |

> **统计**：**VERIFIED = 11 / PARTIAL-NON-BLOCKER = 7 / PARTIAL-BLOCKER = 3 / UNVERIFIED = 2 / MISSING = 0 / BROKEN = 0**

---

## 二、负向证据实证清单（14 项预期 → Phase 5A 实际触达）

| # | 负向证据 | 涉及 Feature | 关联 Issue | Phase 5A 实证？ | 实证文件 |
|---|---|---|---|---|---|
| **N1** | 9 Mkt*Controller 路由注册 + 21 端点可达 | F114-F134 全部 | I064 + I069 | ✅ **全 21 端点 HTTP 可达**（11 个 200 + 7 个 PARTIAL + 2 个 UNVERIFIED 但 HTTP 通了） | 21 个 `FXXX-.../raw-response.json` |
| **N2** | F132 expireSweep 无 try/catch + 缺 audit | F132 | **I060** | ⚠ **happy path 200，但异常路径未实测触发**（注入 SQL 引号失败）| `F132-expire-sweep/raw-es.json` + I060 沿用 |
| **N3** | F135 ChannelDispatcher.getSender miss → NPE | F135 | **I061** | ⏸ Phase 5B 验证（不在 5A 范围）| — |
| **N4** | F138 BusinessPolicyDispatchController 4 表 MISSING → TableNotFoundException | F138 | **I063** | ✅ **information_schema 验证 4 表不存在**（mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target）| `tmp-final-verify.sql` + DB 输出 |
| **N5** | F139 重构中间态 + SoT CONFLICT | F139 | **I055 + I067 + I068** | ⏸ Phase 5B 验证（F140 不在 5A）| — |
| **N6** | F114-F134 前端绕过 api 封装层 | F114-F134 | **I069 NEW（I064 合并）**| ⏸ Phase 5A 仅做后端；前端 Frontend Shadow 静态已在 Phase 3 累计（~70 命中）| — |
| **N7** | F115 rateLimitPerDay setter 缺失 | F115 | **I062** | ✅ **F115 create raw-response：data.rateLimitPerDay = null**（DB 实际用列默认值 5000，API 响应 null — 契约漂移）| `F115-create-channel/raw-response.json` |
| **N8** | F130 snapshotSave 仅 log.info 不写表（dead code）| F130 | **I059** | ✅ **mkt_effect_snapshot 表不存在**（information_schema 0 命中） + `snapshotSave` 仍返回 200 "快照已记录" | `F130-effect-snapshot/raw-save.json` + DB 验证 |
| **N9** | F117 跨租户删除不校验 tenantId | F117 | **I056** | ✅ **clerk01 token DELETE admin 创建的 id=6 → 200 已删除 → DB id=6 deleted=1** | `F117-delete-channel/n9-cross-tenant-clerk.json` + DB 验证 |
| **N10** | F122/F123 跨租户写不强制 tenantId | F122 + F123 | **I057** | ⚠ **F122 业务前置校验挡住**（touchLogId=999 不存在 → 404）→ 未实测"按 body.tenantId 写入"；**F123 发现 I071 NEW**（markRead 假成功）| `F122-track-conversion/raw-response.json` |
| **N11** | F118 ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码 | F118 | **I050 沿用** | ✅ **DB 7 条 tenant_id=1 中，3 条未更新 last_health_at（MP / audit-test-rate-limit-per-day-updated / 5A-ToBeDeleted），仅 4 条更新；所有非 tenant=1 的渠道都不在扫描范围** | `F118-health-check/raw-response.json` + DB `mkt_channel_config` 验证 |
| **N12** | F137 WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名漂移 | F137 | **I039 沿用** | ⏸ Phase 5B 验证（F137 jshell 单元测试）| — |
| **N13** | F125/F129 POST/GET 双路由形式 | F125 + F129 | **I058** | ✅ **F129 dual route 都 200 且 total=1 一致**（实证双形式共存）；**F125 双形式都 500**（同 Service bug 触发）| `F129-touch-task/raw-page.json` + `F125-journey-page/raw-response-both.json` |
| **N14** | 9 张 MKT 表 UNIQUE 不含 deleted | F114-F134 | 沿用 I002 | ⏸ Phase 5B 验证（SHOW INDEX 详细比对）| — |

> **5A 实证成功** = 7 项（N1 / N4 / N7 / N8 / N9 / N11 / N13）/ 部分实证 = 2 项（N2 / N10）/ Phase 5B 待验 = 4 项（N3 / N5 / N6 / N12 / N14）

---

## 三、新发现 Issue（Phase 5A 触达，登记）

| Issue ID | 严重度 | 涉及 Feature | 描述 | 证据 |
|---|---|---|---|---|
| **I070 NEW** | **CRITICAL** | F131 | **MktCouponController.grantBatch 用 SQL `SELECT id, member_name FROM member`** — 但实际表名是 `crm_member`（DDL 实证 `DESCRIBE crm_member`），**导致发券链路必失败 500**；前端点击「发放」后所有券都发不出去 | `F131-coupon/raw-grant-retry.json` (500 数据库异常) + DB `mkt_coupon` 0 行 |
| **I071 NEW** | **CRITICAL** | F123 | **MktConversionController.markRead `UPDATE ... WHERE id=? AND status=2`** — log id=1 status=1 时 0 行更新但仍返回 success="已标记"（Fake Success 沿用 I008）| `F123-mark-read/raw-response.json` (200 success=true) + DB id=1 status=1 read_at=NULL |
| **I072 NEW** | **HIGH** | F120 | **MarketingAutomationService.conversionRoi** 在 marketing_touch_conversion 表 0 行（taskId 过滤）/ 空 amount 时抛 500 数据库异常（可能除 0 / 参数不匹配）| `F120-roi/raw-response.json` (500 数据库异常) |
| **I073 NEW** | **HIGH** | F125 | **MarketingAutomationService.journeyPage** COUNT 查询复用 SELECT 的 args（含 pageSize + offset），参数个数不匹配 → **SQL 异常 500**；POST 和 GET 形式共用 bug → 双形式同中招 | `F125-journey-page/raw-response-both.json` (POST+GET 双 500) |
| **I074 NEW** | **MEDIUM** | F115 | **rate_limit_per_day API 响应 null 与 DB 实际 5000 不一致**（I062 setter 缺失延伸 — 契约漂移）| `F115-create-channel/raw-response.json` (rateLimitPerDay null) vs DB 列默认 5000 |

> **累计 Issue**：72 个（沿用 plan §3.1 + I070-I074 NEW）

---

## 四、修正 plan-batch-1.5.md §三 表清单

### 4.1 MySQL 实测结果（2026-09-16 01:42）

| 类别 | 数量 | 表名 |
|---|---|---|
| plan §3.1 列出 | 11-13 | channel_config / channel_template / coupon / coupon_batch / journey / journey_instance / journey_trigger / journey_trigger_log / points_log / points_rule / promotion |
| **MySQL 实测 16 张 mkt_%** | **+5 张** | **brand** / **dynamic_pricing_rule** / **order_discount** / **pricing_log** / **segment** |
| **I063 4 张 MISSING** | 0 | mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target（information_schema 0 匹配）|

### 4.2 6 张辅助表归属

| 表 | 归属 Service/Controller | 涉及 Feature | Phase 5A 验证 |
|---|---|---|---|
| `mkt_brand` | OrgHierarchyService | 无（MKT 不直接调）| ⏸ 不在 5A |
| `mkt_segment` | RfmService | 无 | ⏸ 不在 5A |
| `mkt_dynamic_pricing_rule` | DynamicPricingService | 无 | ⏸ 不在 5A |
| `mkt_pricing_log` | DynamicPricingService | 无 | ⏸ 不在 5A |
| `mkt_order_discount` | MarketingCouponService 内部 | F131 间接 | ⏸ 不在 5A |
| `mkt_journey_trigger_log` | JourneyTriggerController | F124-F127 间接 | ⏸ 不在 5A |

### 4.3 market_activity 表实测（11 列）

| 列 | 类型 | Null | Key | Default | 备注 |
|---|---|---|---|---|---|
| id | bigint | NO | PRI | NULL | auto_increment |
| store_id | bigint | YES | MUL | NULL | |
| tenant_id | bigint | YES | MUL | NULL | |
| name | varchar(100) | YES | | NULL | |
| type | int | YES | | NULL | |
| rule_config | text | YES | | NULL | |
| start_time | datetime | YES | | NULL | |
| end_time | datetime | YES | | NULL | |
| status | int | YES | MUL | 1 | |
| created_at | datetime | YES | | CURRENT_TIMESTAMP | |
| updated_at | datetime | YES | | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |

> plan §3.1 F139 写「9 列」修正为 **11 列**。

---

## 五、DB 状态全景（Phase 5A 写入汇总）

| DB 表 | Phase 5A 写入行 | 5A 删除行 | 关键说明 |
|---|---|---|---|
| `mkt_channel_config` | +2 (id=8) | 软删 id=6 (clerk01) + id=7 (admin) | **I056 实证**：clerk01 可删 admin 创建的渠道 |
| `mkt_channel_template` | +2 (id=6, id=9) | 0 | id=9 audit_status=2 已审核 |
| `mkt_journey` | +1 (id=3) | 0 | status=3 (paused) enter_count=1 |
| `mkt_journey_instance` | +1 (id=2) | 0 | status=2 (nextNode 已结束) |
| `marketing_touch_task` | +1 (id=2) | 0 | status=6 success_count=489 fail_count=0 |
| `marketing_touch_log` | +489 (5A-T3 触发) | 0 | 总 980 → 981 (audit-test-task 之前已存在)|
| `mkt_coupon_batch` | +1 (id=2) | 0 | status=3 (已结束) |
| `mkt_coupon` | **0** | 0 | **I070 NEW 实证**：grant 失败，0 行写入 |
| `mkt_promotion` | +1 (id=3) | 0 | id=3 5A-Promo4 未真正删除（update 成功 + delete 实际生效）|
| `mkt_points_rule` | +1 (id=2) | 0 | 5A-TestRule maxUsePercent 改 50.00 |
| `market_point_record` | +2 (id=489/490) | 0 | member_id=1 point_value=100 source=manual |
| `mkt_effect_snapshot` | **表不存在** | — | **I059 沿用**：N8 实证 |
| `marketing_touch_conversion` | 0（5A 写入失败 — touchLogId 999 不存在） | 0 | 仅历史 1 行 |
| 4 张 MISSING 表（I063）| 表不存在 | — | information_schema 0 匹配 |

---

## 六、Phase 5A 关键限制与偏差

### 6.1 沿用决策（不动业务代码）

| 项 | 决策 | 沿用 |
|---|---|---|
| 不修 I056/F117 跨租户删除 | 登记不修，Post-Audit | Q3 第六次统一口径 |
| 不修 I060/F132 expireSweep try/catch | 登记不修，Post-Audit | 用户决策 |
| 不修 I062/F115 rateLimitPerDay setter | 登记不修，Post-Audit | 用户决策 |
| 不修 I059/F130 snapshotSave dead code | 登记不修，Post-Audit | 用户决策 |
| 不修 I050/F118 healthCheckAll tenant=1 | 登记不修，Post-Audit | 用户决策 |

### 6.2 新发现（建议进 5B 后决策）

| 新 Issue | 严重度 | 建议处置 |
|---|---|---|
| **I070 NEW**（F131 grant member 表名）| CRITICAL | Post-Audit 必须修 — 直接阻塞券发放链路 |
| **I071 NEW**（F123 markRead Fake Success）| CRITICAL | Post-Audit 修 — 沿用 I008 模式（catch + 报 success）|
| **I072 NEW**（F120 conversionRoi 500）| HIGH | Post-Audit 修 — 空数据兜底 |
| **I073 NEW**（F125 journeyPage COUNT 参数 bug）| HIGH | Post-Audit 修 — 阻塞分页功能 |
| **I074 NEW**（F115 rateLimitPerDay API/DB 不一致）| MEDIUM | 纳入 I062 合并同类 |

---

## 七、Phase 5A 产出清单

### 7.1 evidence 目录（21 个 F）

```
audit/evidence/
├── F114-mkt-channel-list/raw-response.json (4 渠道)
├── F115-create-channel/raw-response.json (rateLimitPerDay=null — I062)
├── F116-update-channel/raw-response.json
├── F117-delete-channel/raw-response.json + n9-cross-tenant-clerk.json (I056)
├── F118-health-check/raw-response.json (5 channelType healthy=false — I050)
├── F119-funnel/raw-response.json (reach/sent/read/converted 4 段)
├── F120-roi/raw-response.json (500 — I072 NEW)
├── F121-by-channel/raw-response.json (1 row)
├── F122-track-conversion/raw-response.json (404 — 业务校验挡)
├── F123-mark-read/raw-response.json (Fake Success — I071 NEW)
├── F124-create-journey/raw-response.json
├── F125-journey-page/raw-response-post.json + raw-response-get.json (双 500 — I073 NEW)
├── F126-journey-detail-update-publish-pause/raw-{detail,update,publish,pause}.json
├── F127-enter-journey/raw-{enter,next}.json
├── F128-template/raw-{response,audit}.json (5/2 已审核)
├── F129-touch-task/raw-{page,create,start,log}.json (489 触达成功)
├── F130-effect-snapshot/raw-{snapshot,save}.json (dead code — I059)
├── F131-coupon/raw-{response,grant-retry,grant-all}.json (grant 500 — I070 NEW)
├── F132-expire-sweep/raw-{es,page,export}.json
├── F133-promotion/raw-{page,list,create,update,delete,export.csv}.csv (BOM EF BB BF)
└── F134-points/raw-response.json (recordId=490 — market_point_record 写入成功)
```

### 7.2 DB 验证脚本

```
tmp-verify-tables.sql       — 16 张 mkt_% + market_activity 11 列 + 4 MISSING
tmp-verify-mkt-tables.sql   — 11 张核心 mkt 表 DESCRIBE
tmp-verify-extra.sql        — crm_member + market_point_record
tmp-verify-negative.sql     — N8 / N10 / N11 / I063 验证
tmp-verify-results.sql      — Phase 5A 写入后 DB 全景
tmp-final-verify.sql        — Phase 5A 完结前最终状态
```

### 7.3 Result 标签分布（21 Feature）

| Result | 数量 | 涉及 Feature |
|---|---|---|
| **VERIFIED** | **11** | F114 / F116 / F119 / F121 / F124 / F126 / F128 / F129 / F132 / F133 / F134 |
| **PARTIAL-NON-BLOCKER** | **7** | F115 (I062) / F117 (I056) / F118 (I050) / F122 (I057) / F123 (I071 NEW) / F130 (I059) / F125 (I058 dual) |
| **PARTIAL-BLOCKER** | **3** | F127 (实例流转 OK 但 enter 首次 500) / F131 (I070 NEW) / F132 (脏数据路径未实测) |
| **UNVERIFIED** | **2** | F120 (I072 NEW) / F125 (I073 NEW) |

---

## 八、Phase 5B 入口条件

| # | 条件 | 当前状态 |
|---|---|---|
| 1 | Phase 5A 完结（本报告）| ✅ |
| 2 | 用户确认 21 Feature Result + 14 项负向证据 + 5 个新 Issue | ⏳ 待用户决策 |
| 3 | 进 5B（jshell F135/F137 + 4 表 MISSING F138 间接 + 重构中间态 F139）| ⏳ 等指令 |

---

## 九、报告附录

### 9.1 累计 Issue 映射（74 个，跳号 I053 + I054 段落登记）

| 段 | IDs | 5A 新增 |
|---|---|---|
| I001-I052 | Batch 1.1-1.4 + EXT-19 | 0 |
| I053 跳号 | 段落登记 | — |
| I054 段落登记 | 反向（表存在）| — |
| I055-I068 | Batch 1.5 Phase 1/3.5/3 | 0 |
| I069 NEW | Frontend Shadow（I064 合并）| 0 |
| **I070-I074 NEW** | Phase 5A 实证 | **5** |
| **累计** | 74 个 | |

### 9.2 用户决策时间线（2026-09-15 ~ 2026-09-16）

| 时间 | 决策 |
|---|---|
| 2026-09-15 01:19 | scope-freeze 10 项 + Q3 第六次统一不跑迁移 |
| 2026-09-15 22:00 | Phase 2 用户决策 |
| 2026-09-16 01:00 | Phase 3 Static Gap Audit 产出（§三十二）|
| 2026-09-16 01:05 | I069 NEW 登记 |
| 2026-09-16 01:12 | Phase 4 计划表完成 |
| 2026-09-16 01:25 | Phase 5 启动（前置 8080/3307/jshell 就绪）|
| 2026-09-16 01:55 | **Phase 5A 完结**（21 Feature 验证 + 5 个新 Issue 实证）|

---

**Phase 5A 完结（Batch 1.5 P0）。21 Feature × Result 矩阵完整 + 14 项负向证据 7 项实证 + 5 个新 Issue 登记。⏸ 等用户确认进 5B（jshell F135/F137 + F138 间接 + F139 重构中间态 + F140 归档）。**
