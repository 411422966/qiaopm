# F167 — 预约确认发送幂等闸门（I177）

> **Feature ID**：F167
> **状态**：**VERIFIED**（L5a — 实际运行 + Redis TTL 对照 + DB 夹具对照，7/7 CHECK PASS）
> **对应 Issue**：`audit/issues/I177-intl-booking-send-confirmation-no-rate-limit.md`
> **基线 commit**：`4ccca57`（修复前 HEAD）
> **修复 commit**：`ecf509d` — `fix(intl-booking): send-confirmation 加 Redis 幂等，防重复触发外部计费（I177）`
> **DB 快照**：本目录 `raw/00-fixture-seed.sql`（夹具）+ `raw/01-verify-i177.txt`（实测输出）
> **环境基线**：`audit/baseline/env.md`
> **改动文件**：
> - `backend/src/main/java/com/meiyetec/booking/BookingConfirmationIdempotency.java`（**新增**）
> - `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` — `updateStatus` / `sendConfirmation`
> - `backend/src/main/resources/db/sql/migrate_i177_intl_booking_send_idempotent_window.sql`（**新增**）

---

## 1. 缺陷与修复

`POST /intl-booking/{id}/send-confirmation`（手动）与 `POST /intl-booking/{id}/status` 的
「非 CONFIRMED → CONFIRMED」自动触发，都会直接调用**外部计费渠道**
（阿里云短信 / SMTP 邮件 / 微信订阅消息），此前**无任何防重复触发机制**。

修复：新增 Redis 幂等闸门 `BookingConfirmationIdempotency`。

| 项 | 口径 |
|---|---|
| Redis key | `booking:confirm:{tenantId}:{bookingId}` |
| TTL | `sys_config` `intl.booking.send.idempotent.window`（默认 **300 秒**） |
| 占位时机 | **发送之前**；失败**不释放** |
| Redis 不可用 | **fail-open**（放行 + `log.warn`），不阻断业务 |
| 手动命中 | HTTP 200 + `code=400` +「确认消息已在发送中，请勿重复操作」 |
| 自动命中 | HTTP 200 + `data.confirmationSkipped=true` + `data.confirmationSkippedReason`，**不产生** `confirmation` 字段 |

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 运行时 | docker compose 栈：`meiyetec/api:dev`（重建自本分支）+ `meiyetec-redis` + `meiyetec-mysql` |
| 后端构建 | `mvn clean package "-Dmaven.test.skip=true"` → `docker compose build api` → `docker compose up -d api` |
| 令牌 | `operator01`（`userId=900006`，`tenant_id=1`） |
| 被测预约 | `overseas_booking.id` = `990101`（手动触发）、`990102`（自动触发），tenant 1 |
| 幂等窗口 | `sys_config.intl.booking.send.idempotent.window` = `300`（S3 场景临时改为 `5`，测完恢复） |
| 外部渠道 | **全部 DEMO 模式**（`mkt_channel_config` id=13 的 `config_json='{}'` → `SmsSender` demo；EMAIL 配置全部 `deleted=1` → `EmailSender` demo）→ **未产生任何真实扣费** |

---

## 3. 实测结果（`raw/01-verify-i177.txt`）

| # | 场景 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| S1 | `POST /intl-booking/990101/send-confirmation` 第 1 次 | 200 + `overallSuccess=true` + Redis key 建立、TTL=300 | `code=200`，`externalMsgId=SMS-DEMO-…` / `EMAIL-DEMO-…`，`TTL=300` | ✅ PASS |
| S2 | 同端点第 2 次（窗口内） | `code=400` +「请勿重复操作」+ TTL **未**刷新 | `code=400`，`message=确认消息已在发送中，请勿重复操作`，TTL 300 → 300 | ✅ PASS |
| S3 | 窗口长度由 `sys_config` 驱动 | 改成 5s → TTL=5；6s 后可再发 | 新 key `TTL=5`；窗口内第 2 次 `code=400`；6s 后第 3 次 `code=200` | ✅ PASS |
| S4 | `POST /intl-booking/990102/status {"status":"CONFIRMED"}`（首次迁移） | `data.confirmation` 存在、无 skip 标记 | `data keys = booking,confirmation`，`overallSuccess=true`，`TTL=299` | ✅ PASS |
| S5 | 再次 `NEW` → `CONFIRMED`（窗口内） | `data.confirmationSkipped=true`，**无** `confirmation` | `data keys = booking,confirmationSkipped,confirmationSkippedReason` | ✅ PASS |
| S6 | 自动触发后手动触发 | 同一闸门 → `code=400` | `code=400` | ✅ PASS |
| S7 | **Redis 宕机**（`docker stop meiyetec-redis`） | fail-open，仍放行发送 | `code=200`，`overallSuccess=true`；Redis 重启后 `PING=PONG` | ✅ PASS |

**OVERALL: ALL PASS（7/7）**

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| 手动触发幂等 | **L5a** | 实际运行 + Redis `TTL` 对照 |
| 自动触发幂等 | **L5a** | 实际运行 + 响应体 `data` 键集对照 |
| 窗口可配置 | **L5a** | 改 `sys_config` → TTL 随动 → 到期恢复 |
| Redis 故障降级 | **L5a** | 实际停容器验证 |
| 真实外部渠道计费行为 | **UNVERIFIED** | 本地通道均为 DEMO 模式，未触达真实阿里云 / SMTP |
| 前端端到端 | **UNVERIFIED** | `intl-booking.ts` **未封装** `sendConfirmation`（I042 Shadow 仍在），前端无调用方 |

---

## 5. 边界声明

- 结论基于**本地 docker 栈实测**；未做浏览器侧验证（纯后端改动，前端无调用方）。
- 测试数据为隔离行（`overseas_booking.id` = 990101 / 990102），验证后已删除，表回到 **0 行**基线；
  Redis 键 `booking:confirm:1:990101` / `booking:confirm:1:990102` 已 `DEL`。
- `sys_config.intl.booking.send.idempotent.window` 测完已恢复为 `300`。
- **已知边界**：预约若三个接收方全空，本次调用仍会占用窗口（EXT-19 约定该场景返 200 + `overallSuccess=false`），
  不在本次修复范围内。
- `raw/00-fixture-seed.sql` 与 `raw/verify-i177.ps1.txt` 标注为**非正式工具**，仅供证据复现，
  **未接入 CI / PR 必跑清单**。脚本为 **UTF-8 with BOM**（Windows PowerShell 5.1 无 BOM 会把中文按 ANSI 读成乱码）。
