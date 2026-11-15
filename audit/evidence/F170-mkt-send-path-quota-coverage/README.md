# F170 — 渠道发送路径配额覆盖只读调查（batch2-C）

> **性质**：**只读调查**（未修改任何业务代码、未执行任何写操作、未执行任何 DDL）
> **调查日期**：2026-11-15
> **执行者**：Cursor
> **绑定基线**：HEAD `cfa7909cdd8cea1e5e5d57c9259a385669507c9b`（工作树 dirty，仅新增本 evidence 目录）
> **DB 快照**：`backup/meiyetec_20261110_020004.sql`（2026-11-10 02:00，23.3 MB）；本次调查全程只读，未产生新快照
> **环境**：`audit/evidence/env.md`
> **运行栈**：docker `meiyetec-mysql`（MySQL 8.0，库 `meiyetec`）/ `meiyetec-redis` / `meiyetec-api`（8080）/ `meiyetec-web`
> **关联**：I178（闸门覆盖缺口）、I180（`brand_id = NULL` 死配置）、I181（本次新建）、I182（本次新建）

---

## 0. 调查目标与边界

| 项 | 内容 |
|---|---|
| 目标 | 把 I178 §1.3「闸门覆盖缺口」从**静态推断**推进到**运行期可观测**，并量化各发送路径与配额体系的关系 |
| 覆盖的发送路径 | ① 触达任务批量发送 ② EXT-19 预约确认 ③ 生日通知 ④ 设备离线告警 |
| 不在范围 | 不改代码、不改配置、不做架构方案落地、不做生产库核实 |
| 产出 | 8 条编号发现（F1–F8）+ 1 个新 Issue（I181）+ 1 个新 Issue（I182）+ I180 运行期佐证 |

---

## 1. 方法

| 步骤 | 手段 | 产物 |
|---|---|---|
| 1 | 全仓静态检索：`rateLimitCheck` / `sender.send(` / `marketing_touch_log` / `getSender(` | 覆盖缺口与死代码清单 |
| 2 | 本机开发库**只读**查询（13 组 SELECT / SHOW），见 `raw/00-readonly-sql-batch.sql` | `raw/01`–`raw/04` |
| 3 | 容器运行期日志采集（`docker logs meiyetec-api`），见 `raw/05`–`raw/06` | 告警路径运行期证据 |
| 4 | 结论分级（VERIFIED / UNVERIFIED）并落 Issue | I181 / I182 |

> ✅ **写操作**：**无**。本目录不含任何夹具写入、任何 `INSERT/UPDATE/DELETE/DDL`，无需清理。
> ✅ **可执行脚本**：**无**。`raw/00-readonly-sql-batch.sql` 为**只读查询合集**，文件头已标注「非正式工具」，不进入 `audit/scripts/` 正式守门序列，不接入 CI。

---

## 2. 发现汇总

| # | 发现 | 验证等级 | 关键证据 |
|---|---|---|---|
| F1 | **SMS 通道的现有配额配置从未生效**：SMS 唯一有效配置 `id=13`（`brand_id=NULL`，`per_min=3`，`per_day=5`），而历史 SMS 成功发送峰值 **489 条/分钟**、单日 **1975 条** | **VERIFIED（运行期）** | `raw/01`（配置全表）、`raw/02`（分钟/小时峰值）、`raw/02`（关联复核：这些成功行的任务 `brand_id` 全为 NULL） |
| F2 | **3 条真实发送路径完全绕过配额闸门**，且不写 `marketing_touch_log` → 配额对它们既不设限、也不可见 | VERIFIED（静态）+ 事实 | `rateLimitCheck` 全仓唯一调用点 `MarketingAutomationService.java:258`；绕过方 `BookingConfirmationService.java:141,157`、`BirthdayNotificationService.java:221`、`KioskOfflineAlertService.java:191` |
| F3 | **告警路径当前处于活跃触发状态**：10 台设备 `last_heartbeat` 全为 NULL → `severityOf(-1)="NEVER"` → 持续触发；每设备 30 分钟冷却 → **480 次/天**的「假成功」告警 | **VERIFIED（运行期）** | `raw/01`（`kiosk_device` 10 行 never-heartbeat）、`raw/06`（23:04:36 连续 10 条 `[KioskAlert] sent ... extId=MSG-SKIPPED-NO-MEMBER`） |
| F4 | **失败与重试不进入配额口径**：只有成功才写 `sent_at`，失败行 `status=3` 不计数；单条明细最多 1+3 次真实调用 | 静态 VERIFIED / 运行期**无样本** | `MarketingAutomationService.java:301`（成功写 `sent_at`）vs `L306`（失败只写 `status=3`）、`L289-296`（重试 3 次）；`raw/01` 显示 `status=3` 行 **0** 条、`retry_count` 全 0 |
| F5 | **配置缺失静默放行 + 取配置无序**：`cfg == null` 时两个限额判断全跳过且**无日志**；`.last("LIMIT 1")` 无 `ORDER BY` → 多行时命中不确定 | VERIFIED（静态） | `ChannelDispatcher.java:111-128`（`cfg` 查询 + 两个 `if`） |
| F6 | **生日 / 预约确认路径运行期无样本** → 相关结论只能停在静态层 | **UNVERIFIED（无数据）** | `raw/01`：`birthday_member` / `birthday_send_log` / `member_message` / `overseas_booking` 均 **0** 行 |
| F7 | **品牌上下文可获得性矩阵**（供后续「闸门下沉」选型） | **VERIFIED（运行期）** | 见 §3 |
| F8 | **`getSender` 永不返回 null** → 3 处 `sender == null` 是**死代码**；未知渠道静默降级为站内信 | VERIFIED（静态） | `ChannelDispatcher.java:48-60`（fallback `defaultSender`）；死代码点 `MarketingAutomationService.java:264`、`BirthdayNotificationService.java:195`、`KioskOfflineAlertService.java:148` |

---

## 3. F7 — 品牌上下文可获得性矩阵

| 发送路径 | 品牌来源 | 可获得性 | 证据 |
|---|---|---|---|
| 触达任务批量发送 | `marketing_touch_task.brand_id` | ✅ 直取 | `raw/04`（`SHOW COLUMNS FROM marketing_touch_task LIKE 'brand%'`） |
| 生日通知 | `crm_member.brand_id`（经 `memberId`） | ✅ 一次关联 | `raw/02`（`crm_member` 含 `brand_id` / `tenant_id`） |
| EXT-19 预约确认 | `overseas_booking.store_id` → `pos_store.brand_id` | ⚠️ 两跳，且 `overseas_booking` **无** `brand_id` 列 | `raw/03`（`overseas_booking` 结构）、`raw/04`（`pos_store` 含 `brand_id`） |
| 设备离线告警 | `kiosk_device.store_id` → `pos_store.brand_id` | ❌ **断链**：`kiosk_device` 10 行的 `store_id` **全为 NULL** | `raw/04`（`kiosk_device` store_id 分布） |

> ⚠️ 推论：即使把闸门下沉到统一发送入口，**告警路径也只能拿到 tenant + channel 维度**，会直接撞上 I180 的「`brand_id = NULL` 共享池口径」问题 → I180 的业务拍板是下沉方案的前置。

---

## 4. F1 详情 — 配额配置从未生效的运行期实证

| 事实 | 值 | 来源 |
|---|---|---|
| `mkt_channel_config` 有效行（`deleted=0`） | 仅 5 条：`id=2`（MINI_PROGRAM, brand=1）、`id=3`（MP, brand=1）、`id=4`（IN_APP, brand=NULL）、`id=10`（PUSH, brand=NULL）、`id=13`（SMS, brand=NULL） | `raw/01` |
| SMS 唯一有效额度 | `per_min=3`、`per_day=5` | `raw/01` |
| 历史 SMS 成功发送峰值 | **489 条/分钟**（4 个分钟达到该量级），单日合计 **1975 条** | `raw/02` |
| 这些成功行所属任务的 `brand_id` | **全部为 NULL**（关联复核 1971 行） | `raw/02` |

**推论链**：`ChannelDispatcher.java:111-117` 用 `eq(MktChannelConfig::getBrandId, brandId)` 取配置 →
`brandId` 为 NULL 时生成 `brand_id = NULL`，MySQL 中 `= NULL` 永不成立 → **取不到任何额度配置** →
`cfg == null` → `ChannelDispatcher.java:118` 与 `L126` 两个限额判断全部跳过 → **静默放行**。

> ✅ 该推论此前只是静态推断（登记在 I180），本次由**历史发送量与配置额度的量级差**（489 vs 3）升级为**运行期 VERIFIED**。
> ⚠️ 边界：上述发送量来自 2026-09-16 的历史数据（当时 `eq(brandId)` 逻辑与当前代码一致），**未回溯校正**。

---

## 5. F3 详情 — 告警路径运行期取证

| 事实 | 值 | 来源 |
|---|---|---|
| `kiosk_device` | 10 行，`tenant_id=1`，`status=ACTIVE`，`last_heartbeat` **全部 NULL** | `raw/01` |
| 严重度映射 | `KioskAlertProperties.severityOf(-1)` → `"NEVER"`（**不是** `"OK"`）→ 不被 `handleOffline` 跳过 | `backend/.../config/KioskAlertProperties.java:60-66` |
| 运行期日志 | 23:04:36 连续 10 条 `[KioskAlert] sent device=... channel=IN_APP extId=MSG-SKIPPED-NO-MEMBER` | `raw/06`（41 行） |
| 接收人回退 | `mkt_channel_config` 中 IN_APP 有效行 `id=4` 的 `config_json` 为 **NULL** → `resolveReceiver` 返回 null → 回退 `"ops"` | `raw/04` |
| 实际结果 | `InAppSender.java:59-62` 判定 `"ops"` 不可解析为 memberId → **跳过落库**并返回 `SendResult.ok("MSG-SKIPPED-NO-MEMBER")` → 上层 `KioskOfflineAlertService.java:196` 判为**发送成功** | `raw/06` |
| 量级 | 冷却 `cooldown-minutes=30` → 10 台 × 48 次/天 = **480 次/天**「假成功」 | `KioskAlertProperties` 默认值 |
| 计费影响 | 当前 `push-channel=IN_APP`（不产生外部计费）；若改为 SMS，即为 480 条/天真实短信，**且不受任何配额约束** | `raw/04` |

> ⚠️ **假成功标记**：告警被上层记为 `SENT`（`KioskOfflineAlertService.java:196-197` 的 `appendLog(... "SENT")`），但实际**没有任何站内信落库**，运维看板与冷却状态均建立在「假成功」之上。

---

## 6. 边界声明

- 本调查**未修改任何业务代码**、**未执行任何 SQL 写入**、**未执行任何 DDL**、**未启动/重启任何容器**。
- F1 / F3 / F7 有**运行期证据**（本机开发库 + 容器日志）；F2 / F4 / F5 / F8 为**静态代码结论**；F6 因库内无数据保持 **UNVERIFIED**。
- 全部数据来自**本机开发库**，**不代表生产环境**分布；生产库 `mkt_channel_config` 的 `brand_id` / 限额取值分布**未核实**。
- 「失败是否应计入配额」取决于各渠道商计费规则，**未与渠道商确认**（沿用 I178 R3 口径）。
- 未核实：是否存在多品牌租户的生产分布（I178 R2 的实际发生率）。
- **未打开浏览器、未做前端端到端验证、未替代人工验收**。

---

## 7. 文件清单

| 文件 | 内容 |
|---|---|
| `raw/00-readonly-sql-batch.sql` | 只读查询合集（非正式工具，不入 CI） |
| `raw/01-channel-config-and-touch-log.txt` | 渠道配置全表 + 触达明细状态分布 + 相关表行数 |
| `raw/02-send-rate-vs-quota.txt` | 发送速率 vs 配额额度、任务品牌分布、告警设备心跳、会员品牌列 |
| `raw/03-brand-context-part1.txt` | `overseas_booking` 结构、`birthday_member` 规模（`crm_store` 不存在，查询在此中断） |
| `raw/04-brand-context-part2.txt` | `pos_store` 结构、设备 store_id 分布、IN_APP 配置 `config_json`、单任务发送量 |
| `raw/05-api-runtime-log.txt` | `meiyetec-api` 近 60 分钟完整日志 |
| `raw/06-kiosk-alert-runtime.txt` | 其中 KioskAlert 相关 41 行 |

---

## 8. 复现方式

```powershell
# 只读查询（无写操作）
cmd /c 'docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec --default-character-set=utf8mb4 --table < audit\evidence\F170-mkt-send-path-quota-coverage\raw\00-readonly-sql-batch.sql'

# 告警路径运行期日志
cmd /c 'docker logs meiyetec-api --since 60m 2>&1 | findstr /C:"KioskAlert"'
```
