# I181 — 渠道发送路径配额绕过（3 条真实发送路径不查配额 + 失败不计配额 + 配置缺失静默放行）

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码**；闸门下沉属架构级改造，需单独窗口）
> **严重度**：**P2**（不造成数据越权；但让「已配置的每分钟/每日限额」在 3/4 的真实发送路径上完全失效，并可能打爆真实计费渠道）
> **发现日期**：2026-11-15（batch2-C 只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `cfa7909cdd8cea1e5e5d57c9259a385669507c9b`
> **验证等级**：静态代码检索 + 本机开发库**只读**查询 + 容器运行期日志 → F1 / F3 / F7 **VERIFIED（运行期）**；F2 / F4 / F5 / F8 **VERIFIED（静态）**；F6 **UNVERIFIED（库内无样本）**
> **证据**：`audit/evidence/F170-mkt-send-path-quota-coverage/`
> **关联**：I178（闸门覆盖缺口与口径错配，§1.3 即本 Issue）、I180（`brand_id = NULL` 共享池口径，**本 Issue 的前置**）、I169（通用限流基建）、I177（EXT-19 预约确认幂等）、**I182**（`InAppSender` 把手机号当 memberId）

---

## 0. 一句话描述

`ChannelDispatcher.rateLimitCheck` 是仓库里唯一的渠道配额闸门，**只有一个调用点**；
**EXT-19 预约确认 / 生日通知 / 设备离线告警**这 3 条真实发送路径**直接调用 `sender.send(...)`，完全不查配额**，
且**不写 `marketing_touch_log`** → 配额对它们**既不设限、也不可见**；
再叠加「失败不计配额」「配置缺失静默放行」「取配置无序」三条口径缺陷，
实际效果是：**渠道账号的调用量基本不受任何闸门约束**。

---

## 1. 事实核实（2026-11-15，静态检索 + 只读查询 + 运行期日志）

### 1.1 闸门只有一个调用点

全仓检索 `rateLimitCheck` → **仅 1 处**：
`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:258`（`startTask` 内）。

### 1.2 三条绕过路径明细

| # | 发送路径 | 发送调用点 | 配额闸门 | 可观测落库 |
|---|---|---|---|---|
| 1 | **EXT-19 预约确认**（SMS / EMAIL / MINI_PROGRAM） | `booking/BookingConfirmationService.java:141`（`sendViaChannel`）、`L157`（`sendMiniProgram`） | ❌ 无 | ❌ 无（仅返回体 `BookingConfirmationResult`） |
| 2 | **生日通知**（`scheduledScan` 排程 + `sendGift` 手动） | `service/BirthdayNotificationService.java:194`（`getSender`）→ `L221`（`sender.send`） | ❌ 无 | ⚠️ 有 `birthday_send_log`，但**不含 channel_type / brand_id**，且不参与配额计数 |
| 3 | **设备离线告警** | `service/KioskOfflineAlertService.java:147`（`getSender`）→ `L191`（`sender.send`） | ❌ 无 | ❌ 无（仅内存 `recentLogs` 100 条，重启丢失、多实例不可见） |

> 这 3 条路径与触达任务共用同一批 `ChannelSender` 实现、同一套阿里云 / SMTP 账号 → **配额是共享资源，但只有 1/4 的路径看配额**。

### 1.3 失败与重试不进入配额口径

| 事实 | 位置 |
|---|---|
| 发送成功才写 `sent_at = NOW()` | `MarketingAutomationService.java:301-303` |
| 失败只写 `status = 3` + `error_msg`，**不写 `sent_at`** | `MarketingAutomationService.java:306` |
| 失败后最多重试 3 次（每次都真实调用外部渠道） | `MarketingAutomationService.java:289-296` |

→ 单条明细最多产生 **1 + 3 = 4 次真实外部调用**，但配额消耗为 **1（成功）或 0（失败）**。
⚠️ 阿里云侧失败是否计费取决于渠道商规则，**未与渠道商确认**。

**运行期样本**：本机库 `marketing_touch_log` 中 `status = 3` 的行 **0** 条、`retry_count` 全为 0
→ 该分支**无运行期样本**，结论为静态。

### 1.4 配置缺失静默放行 + 取配置无序

| 事实 | 位置 |
|---|---|
| `cfg == null`（无配置 / 限额为 null）→ 两个 `if` 全跳过 → **直接放行**，且**无任何日志** | `ChannelDispatcher.java:111-128` |
| 取配置用 `.last("LIMIT 1")` 且**无 `ORDER BY`** | `ChannelDispatcher.java:116` |

→ 同渠道存在多条有效配置时，**命中哪一条不确定** → 限额可能随机漂移（I180 §1.1 已实证同渠道存在 2–3 条 `brand_id = NULL` 的历史行）。

### 1.5 告警路径运行期取证（本 Issue 最强证据）

| 事实 | 值 |
|---|---|
| `kiosk_device` | 10 行，`tenant_id=1`，`status=ACTIVE`，`last_heartbeat` **全部为 NULL（从未心跳）** |
| 严重度映射 | `KioskAlertProperties.severityOf(-1)` → `"NEVER"`（**不是** `"OK"`）→ `handleOffline` 不跳过 |
| 运行期日志 | 23:04:36 连续 10 条 `[KioskAlert] sent device=... channel=IN_APP extId=MSG-SKIPPED-NO-MEMBER` |
| 实际落库 | **无** —— `InAppSender.java:59-62` 判定 receiver `"ops"` 不可解析为 memberId → 跳过持久化 |
| 上层判定 | `KioskOfflineAlertService.java:196` 依据 `r.success` 记为 **SENT** → **假成功** |
| 量级 | 冷却 30 分钟 → 10 台 × 48 次/天 = **480 次/天** |
| 计费影响 | 当前 `push-channel = IN_APP` 不产生外部计费；**若改为 SMS，即为 480 条/天真实短信且不受任何配额约束** |

> ⚠️ **假成功 / 契约漂移标记**：告警看板与冷却状态建立在「没有落库但记为 SENT」之上。

### 1.6 `getSender` 永不返回 null → 3 处 null 检查是死代码

| 事实 | 位置 |
|---|---|
| `getSender` 对未知渠道**静默回退** `defaultSender`（= `InAppSender`），只打一条 `warn` | `ChannelDispatcher.java:48-60` |
| 因此 `sender == null` 恒为 false → 「no sender」失败分支不可达 | `MarketingAutomationService.java:264-266`、`BirthdayNotificationService.java:195-197`、`KioskOfflineAlertService.java:148-150` |

→ 未知渠道（如 `PUSH` 配置了但 sender 未实现 / 拼写错误）→ **静默降级为站内信**：
营销任务会被记为「no sender」失败但实际已发出站内信；告警会被记为 SENT。

### 1.7 品牌上下文可获得性（决定下沉方案的可行形态）

| 发送路径 | 品牌来源 | 可获得性 |
|---|---|---|
| 触达任务 | `marketing_touch_task.brand_id` | ✅ 直取 |
| 生日通知 | `crm_member.brand_id`（经 `memberId`） | ✅ 一次关联 |
| EXT-19 预约确认 | `overseas_booking.store_id` → `pos_store.brand_id`（`overseas_booking` **无** `brand_id` 列） | ⚠️ 两跳 |
| 设备离线告警 | `kiosk_device.store_id` → `pos_store.brand_id` | ❌ **断链**：`kiosk_device` 10 行 `store_id` **全为 NULL** |

> ⚠️ 告警路径即使下沉闸门，也只能拿到 **tenant + channel** 维度 → 直接撞上 I180 的 `brand_id = NULL` 共享池口径问题 → **I180 的业务拍板是本 Issue 落地的前置**。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | 3 条路径绕过配额 | 任一路径被批量调用（生日排程、告警风暴、预约确认） | 渠道账号被风控 / 欠费；配置里的限额形同虚设 |
| R2 | 失败与重试不计入配额 | 外部渠道批量失败 | 真实调用量可达配额数的 4 倍，运营侧完全无感 |
| R3 | 配置缺失静默放行 | 渠道未配限额 / 品牌无专属配置 | 「配了没生效」与「没配」无法区分（I180 已实证） |
| R4 | 取配置无序 | 同渠道多条有效配置 | 限额随机漂移，行为不可复现 |
| R5 | 告警假成功 | IN_APP 通道 + receiver 非 memberId | 运维看板显示已告警，实际无任何站内信 |
| R6 | 未知渠道静默降级 | 渠道类型拼错 / sender 未实现 | 消息发到错误通道且无强报错 |
| R7 | 告警路径无品牌上下文 | 下沉闸门时 | 只能 tenant+channel 维度 → 与 I180 口径强耦合 |

---

## 3. 建议方案（需拍板，本批不实施）

| # | 步骤 | 内容 | 前置 |
|---|---|---|---|
| 1 | **确认配额口径** | 是否按品牌、tenant+channel 共享池如何计数 | **I180 业务拍板** |
| 2 | **闸门下沉到统一发送入口** | 在 `ChannelDispatcher` 增加 `send(tenantId, brandId, channelType, tpl, receiver, vars)` 统一入口，4 条路径全部改走该入口，闸门内聚到入口内 | 步骤 1 |
| 3 | **失败计数口径** | 失败是否消耗配额（取决于渠道商计费规则） | 业务 + 渠道商确认 |
| 4 | **配置缺失显式化** | 无配置时明确 `放行 + warn 日志` 或 `拒绝`，不再静默 | 步骤 1 |
| 5 | **取配置确定性** | `.last("LIMIT 1")` → `ORDER BY id DESC LIMIT 1` 或改唯一约束 | 步骤 4 |
| 6 | **告警路径品牌上下文** | `kiosk_device` 补 `store_id` / `brand_id`，或明确告警按租户维度计量 | 步骤 1 |
| 7 | **清理死代码与静默降级** | `getSender` 未知渠道改为显式异常或显式 opt-in fallback；3 处 null 检查同步 | 步骤 2 |
| 8 | **与 I169 合并观测** | 统一「配额拦截次数 / 通道调用量」埋点与看板 | I169 落地 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 生日通知批量发送 | 达到配额后被拦截并**留痕**（写 `marketing_touch_log` 或等价计数表） |
| 2 | 设备离线告警风暴 | 达到配额后被拦截并留痕 |
| 3 | EXT-19 预约确认（I177 幂等之外） | 达到配额后被拦截并留痕 |
| 4 | 失败发送 | 按选定口径计数（计 / 不计），且行为一致可复现 |
| 5 | 无配额配置 | 显式放行或显式拒绝，**且有日志** |
| 6 | 同渠道多条有效配置 | 取值确定（同一 id 恒定命中） |
| 7 | 未知渠道类型 | 不再静默降级为站内信（或降级时有明确 warn + 计数） |
| 8 | 告警落库 | 告警记录可跨重启查询（不再是内存 100 条） |

---

## 5. 边界声明

- 本 Issue **仅登记**，**未修改任何业务代码**、**未执行任何 SQL 写入**、**未执行任何 DDL**、**未启停容器**。
- 证据来源：静态代码检索 + 本机开发库**只读**查询 + `meiyetec-api` 容器运行期日志
  （见 `audit/evidence/F170-mkt-send-path-quota-coverage/`）。
- F1（配置从未生效）/ F3（告警活跃触发）/ F7（品牌上下文）有**运行期证据**；
  F2 / F4 / F5 / F8 为**静态结论**；F6（生日 / 预约确认路径）因库内无样本为 **UNVERIFIED**。
- 全部数据来自**本机开发库**，不代表生产分布；生产环境 `mkt_channel_config` / `kiosk_device` 分布**未核实**。
- 「失败是否计入配额」**未与渠道商确认**。
- **未打开浏览器、未做前端端到端验证、未替代人工验收**。

---

**下一步**：① I180 业务拍板共享池口径；② 与 I178 §1.3 合并为一次「闸门下沉」改造（需单独窗口 + 业务确认计数口径）；③ R3/R5 与渠道商 / 运维确认后单独排期。
