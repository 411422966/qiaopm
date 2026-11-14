# I177 — 预约确认发送无防重复触发机制（外部渠道计费放大）

> **状态**：**已修复**（2026-11-15，修复前 HEAD `4ccca57`）→ 证据：`audit/evidence/F167-intl-booking-confirm-idempotent/`（VERIFIED / L5a）
> **严重度**：**P1**（直接对应**真金白银的外部渠道计费**——阿里云短信 / SMTP 邮件 / 微信订阅消息，每触发一次就是一次真实扣费）
> **发现日期**：2026-11-14（I169 通用限流调查批次）
> **登记人**：Cursor
> **绑定基线**：`audit/baseline/env.md` → 修复前 HEAD `4ccca57`
> **验证等级**：静态代码分析 → 运行期 **VERIFIED（L5a，7/7 CHECK PASS）**
> **关联**：I169（通用限流基建，本批**暂缓**）、I178（ChannelDispatcher 限流覆盖缺口）、I166 / I179（同批拆分）

---

## 0. 一句话描述

`POST /intl-booking/{id}/send-confirmation`（手动重发）与 `POST /intl-booking/{id}/status`
的「非 CONFIRMED → CONFIRMED」自动触发，都会**直接调用**阿里云短信 / SMTP 邮件 / 微信订阅消息
（**外部计费渠道**），此前**没有任何防重复触发机制** —— 同一笔预约可在数秒内被反复发送，成本直接按倍数放大。

> ⚠️ 本 Issue 的修复是**幂等**（同一预约在窗口内只发一次），**不是频率限流**。
> 真正的「同 IP / 同用户 N 次/分钟」限流属平台级基建，见 **I169**（方案 A/B/C 待业务 + 运维拍板）。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 手动触发端点 | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` — `sendConfirmation`（修复后 **L267-276**） |
| 自动触发点 | 同文件 — `updateStatus`（**L215**），自动发送分支 **L236-257** |
| 实际发送逻辑 | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationService.java` — `sendConfirmation` |
| 渠道实现 | `service/channel/SmsSender.java`（阿里云）、`service/channel/EmailSender.java`（SMTP）、`service/channel/MiniProgramSender.java`（微信订阅消息） |
| 幂等组件（新增） | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationIdempotency.java` |
| 配置脚本（新增） | `backend/src/main/resources/db/sql/migrate_i177_intl_booking_send_idempotent_window.sql` |
| Redis key | `booking:confirm:{tenantId}:{bookingId}` |
| sys_config key | `intl.booking.send.idempotent.window`（秒，默认 **300**） |
| 前端调用方 | **无** —— `frontend/meiyetec-web/src/api/intl-booking.ts` **未封装** `sendConfirmation`（I042 Shadow 仍在） |

---

## 2. 缺陷机制

### 2.1 两条重复触发路径

```
路径 A（手动重发）
  POST /intl-booking/{id}/send-confirmation
    → IntlBookingService.sendConfirmation
      → confirmationService.sendConfirmation(b)
        → SmsSender.send  → 阿里云 SMS API   ⇒ 扣费
        → EmailSender.send → SMTP           ⇒ 扣费
        → MiniProgramSender.send → 微信订阅消息 ⇒ 扣费

路径 B（状态迁移自动触发，EXT-19）
  POST /intl-booking/{id}/status {"status":"CONFIRMED"}
    → IntlBookingService.updateStatus
      → 旧状态 != CONFIRMED → 自动 sendConfirmation(...)  ⇒ 同上扣费

两条路径均无任何「已发送」标记 / 幂等键 / 频率闸门
→ 前端连点、网络重试、用户重复点击、外部 webhook 重放 → 成本按次线性放大
```

### 2.2 与 I169 的关系（口径澄清）

- I169 登记的是**平台级通用限流**（Redis 固定窗口计数器 + 注解 + AOP），覆盖「同 IP N 次/分钟」这类**滥用**。
- I177 是**幂等**：防的是「**同一次确认**被重复触发」，与「谁在高频调用」无关。
- 两者**不互相替代**：I177 修好后，同一预约仍可由**不同**预约的批量调用打爆渠道配额（那属于 I169 + I178 的范畴）。

---

## 3. 修复方案（已实施）

### 3.1 新增 `BookingConfirmationIdempotency`（Redis 幂等闸门）

```java
public boolean acquire(Long tenantId, Long bookingId) {
    if (redis == null) { log.warn(...); return true; }          // 未装配 → fail-open
    String key = KEY_PREFIX + tenantId + ":" + bookingId;        // booking:confirm:{tid}:{bid}
    try {
        int window = resolveWindow();                             // sys_config，缺省 300s
        Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(window));
        return Boolean.TRUE.equals(acquired);
    } catch (Exception e) {
        log.warn(...);
        return true;                                              // Redis 异常 → fail-open
    }
}
```

**关键设计**：

| 项 | 口径 | 理由 |
|---|---|---|
| 占位时机 | **发送之前** | 发送后占位存在「并发双写」窗口（两个请求同时判空通过） |
| 失败后是否释放 | **不释放** | 宁可让运营等一个窗口，也不为了「允许重试」重新打开重复计费的口子 |
| Redis 不可用 | **fail-open**（放行 + `log.warn`） | 幂等是防重复，**不是安全边界**；Redis 故障不应阻断业务 |
| 窗口长度 | `sys_config` `intl.booking.send.idempotent.window`，默认 300s | 可运营调整，无需发版 |
| 租户隔离 | key 含 `tenantId` | 同租户内同预约才算同一次确认 |

### 3.2 接入点

**手动触发**（`IntlBookingService.sendConfirmation` L267-276）：

```java
if (!confirmationIdempotency.acquire(tenantId, id)) {
    return Result.error(BizCode.BAD_REQUEST.code, "确认消息已在发送中，请勿重复操作");
}
```

→ HTTP **200** + 业务 `code=400` + `message="确认消息已在发送中，请勿重复操作"`。

**自动触发**（`IntlBookingService.updateStatus` L236-257）：

```java
if (!confirmationIdempotency.acquire(tenantId, id)) {
    log.info("[INTL-BOOKING] auto-send skipped: 幂等窗口内已发送确认消息，跳过 bookingId={}", id);
    data.put("confirmationSkipped", true);
    data.put("confirmationSkippedReason", "幂等窗口内已发送确认消息，跳过重复发送");
} else {
    try { ... confirmationService.sendConfirmation(b) ... }
}
```

→ HTTP 200，`data` 含 `confirmationSkipped=true`，**不**产生 `confirmation` 字段（状态变更本身仍然成功）。

### 3.3 契约变更说明（前端）

| 端点 | 变更前 | 变更后 |
|---|---|---|
| `POST /intl-booking/{id}/send-confirmation` | 永远 200 + `已发送` | 窗口内重复 → **200 + code=400** +「确认消息已在发送中，请勿重复操作」 |
| `POST /intl-booking/{id}/status` | `data` 含 `booking` / `confirmation` | 新增 `data.confirmationSkipped` / `data.confirmationSkippedReason`（**纯增量**，不破坏既有字段） |

- ⚠️ `send-confirmation` **前端当前无任何调用方**（`frontend/meiyetec-web/src/api/intl-booking.ts` 未封装该方法，I042 Shadow 未解）
  → 本次契约变更**当前不影响前端**；待前端补上该方法时，必须处理 `code=400` 分支（不能一律按「已发送」提示）。
- `updateStatus` 的新增字段是**增量**，前端现有逻辑不受影响。

---

## 4. 验证口径（修复后需满足）

| # | 检查项 | 期望 | 实测 |
|---|---|---|---|
| 1 | 手动触发第 1 次 | 200 + `overallSuccess=true` + Redis key 建立、TTL=300 | ✅ PASS |
| 2 | 手动触发第 2 次（窗口内） | `code=400` +「请勿重复操作」+ TTL **未**刷新 | ✅ PASS |
| 3 | 窗口长度来自 `sys_config` | 改成 5s → TTL=5；6s 后可再次发送 | ✅ PASS |
| 4 | 自动触发第 1 次（NEW→CONFIRMED） | `data.confirmation` 存在、无 skip 标记 | ✅ PASS |
| 5 | 自动触发第 2 次（窗口内） | `data.confirmationSkipped=true`，**无** `confirmation` | ✅ PASS |
| 6 | 手动与自动共用同一闸门 | 自动触发后再手动 → `code=400` | ✅ PASS |
| 7 | Redis 宕机 | 仍然放行发送（fail-open） | ✅ PASS |

> 证据：`audit/evidence/F167-intl-booking-confirm-idempotent/raw/01-verify-i177.txt`

---

## 5. 遗留与边界

| 项 | 状态 |
|---|---|
| 历史脏数据 | **无** —— 本 Issue 是「缺失防护」，不是「写坏数据」，无需回填 |
| 预约无任何可发送渠道（phone / email / openId 全空） | 仍会占用窗口（EXT-19 约定该场景返 200 + `overallSuccess=false`）→ **已知边界**，不在本次修复范围 |
| 「同一用户 1 分钟最多 N 次」类频率限流 | **未做** → I169（待业务 / 运维拍板 429 契约） |
| 渠道配额（多品牌 / 每分钟 / 每日） | **未做** → I178（需业务确认多品牌配额口径） |
| 前端 `sendConfirmation` 方法 | **仍缺失**（I042 Shadow）—— 契约变更需在补齐时同步 |
| 单实例 vs 多实例 | 依赖 Redis 计数器 → 多实例天然共享（与 I169 §5 检查项 5 一致） |

---

## 6. 边界声明

- 运行期结论基于**本地 docker 栈实测**（`meiyetec/api:dev` + `meiyetec-redis` + `meiyetec-mysql`），
  通道均为 **DEMO 模式**（`mkt_channel_config` id=13 的 `config_json='{}'` → `SmsSender` 降级 demo；
  EMAIL 配置全部 `deleted=1` → `EmailSender` 降级 demo）→ **未产生任何真实外部扣费**。
- 未做浏览器侧验证（纯后端改动，且前端无调用方）。
- 测试数据为隔离行（`overseas_booking.id` = 990101 / 990102），验证后已删除，表回到 0 行基线。

---

**下一步**：① 前端补 `intlBookingApi.sendConfirmation` 时同步处理 `code=400` 分支；
② I169 通用限流基建待业务拍板 429 契约 + 运维确认 Nginx 侧限制；
③ I178 渠道配额口径待业务确认多品牌归属。
