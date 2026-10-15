# I047 — IG Webhook Catch 吞异常（Fake Success — I008 同模式）

| 维度 | 值 |
|---|---|
| **Issue ID** | I047 |
| **创建时间** | 2026-09-14 22:20 (UTC+8) |
| **Phase** | 5B — Runtime Verification |
| **类别** | Fake Success（catch 吞所有异常 + 仍返回 success） |
| **严重度** | 🔴 **CRITICAL-P1**（生产实证 — IG webhook 静默失败 + 数据未入库） |
| **影响 Feature** | F100（Instagram Webhook / 接收 IG DM 写入 overseas_booking） |
| **关联 Issue** | **I008**（CRM Dashboard — 反向对称同模式）/ I037（webhook path exclude）/ I038（overseas_booking 表缺失） |
| **修复阶段** | ✅ **FIXED**（2026-09-17 commit `f0c73d8` / Post-Audit 2.2-A5） |

---

## 1. 现象

```java
// backend/src/main/java/com/meiyetec/service/IntlBookingService.java:311-360（修复前）
public Result<Map<String, Object>> instagramWebhook(@RequestBody Map<String, Object> payload) {
    log.info("[INSTAGRAM WEBHOOK] payload={}", payload);
    Long tenantId = 1L; // 演示模式
    try {
        // ... 嵌套 if 解析 entry → messaging → message → INSERT overseas_booking ...
        bookingMapper.insert(b);                              // ← I038 触发：Table 'meiyetec.overseas_booking' doesn't exist
    } catch (Exception e) {
        log.warn("[INSTAGRAM WEBHOOK] parse failed: {}", e.getMessage());   // ← 只 warn 不抛
    }
    return Result.success(Map.of("code", "NOOP", "message", "no parseable message"));  // ← 永远 success
}
```

### 关键 bug

`bookingMapper.insert(b)` 抛 `BadSqlGrammarException`（I038 表缺失）→ catch **只 `log.warn` 静默吞** → 仍 `return Result.success(NOOP)` → Meta IG 收到 200 → **不再重试 → 数据永久丢失**。

---

## 2. Phase 5B 实测证据

**traceId**: `2c85a690...`

```json
POST /intl-booking/webhook/instagram
Body: { "object":"instagram", "entry":[{"id":"123456789","time":1726300800,"changes":[{"field":"messages","value":{"from":{"id":"987654321"},"message":{"text":"Book facial tomorrow please"},"timestamp":1726300800}}]}] }
```

响应：
```json
HTTP/1.1 200
{"code":200,"message":"success","data":{"code":"NOOP","message":"no parseable message"}}
```

后端日志（`audit/evidence/F100-ig-webhook/log-parse-failed.txt`）：
```
2026-09-14 22:31:30.889 [http-nio-8080-exec-5] WARN  IntlBookingService [INSTAGRAM WEBHOOK] parse failed: 
### Error querying database. Cause: java.sql.SQLSyntaxErrorException: 
  Table 'meiyetec.overseas_booking' doesn't exist
```

→ **API 成功 + 数据 0 行入库** + 运维侧只有 WARN 日志（无 traceId、无 ERROR、无 metric）

---

## 3. 与 I008 对比（同模式）

| 维度 | I008（CRM Dashboard）| I047（IG Webhook） |
|---|---|---|
| 位置 | `CrmIntegrationService.dashboard()` | `IntlBookingService.instagramWebhook()` |
| catch 内容 | `catch (Exception ignore) {}`（完全空）| `catch (Exception e) { log.warn(...) }`（仅 warn）|
| 返回 | `Result.success(empty data)` | `Result.success(NOOP)` |
| 触发频次 | 每次手动调 dashboard | **每次 IG 推送 webhook** |
| 上游感知 | 无（人工调用）| **Meta IG — 收到 200 不再重试 → 数据永久丢失** |
| 严重度 | 🔴 CRITICAL | 🔴 **CRITICAL-P1**（更强：上游永远不知道失败） |

---

## 4. 触发条件矩阵

| 场景 | 是否触发 I047 | 备注 |
|---|---|---|
| IG 推送普通 DM（含 text）| ✅（5B 实测）| overseas_booking 表缺失 → insert 抛错 → NOOP 静默 |
| IG 推送 sticker / 图片 | ❌ | try 内 if 不匹配 → 走外层 NOOP（**合法 NOOP**）|
| IG 推送 system event（如已读回执）| ❌ | 同上（合法 NOOP）|
| Meta IG 重试（如果 5xx）| — | 永远收 200，永不重试 |

---

## 5. 修复建议（**审计后** / Post-Audit 已修）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① catch → log.error + Result.error** | `catch (Exception e) { log.error(...); return Result.error(SYSTEM_ERROR); }` | 上游可见失败信号 → 重试 + 运维可观测 | 真失败会让 Meta 收到 5xx 触发重试（设计预期）|
| ② 移除 try/catch + 全局 handler | 让异常冒泡 → GlobalExceptionHandler 统一处理 | 最简洁 | 改动多 |
| ③ catch (Exception e) + log.error 但仍 success | 增强可观测性 | 仍静默 → 上游仍不重试 → 数据丢失 |
| ④ 接受现状 | 不修 | 数据永久丢失风险持续 |

**推荐**：①（最小改动 + 正确语义）。

---

## 6. Post-Audit 修复实录（批次 2.2-A5）

### 6.1 修复动作

| 维度 | 内容 |
|---|---|
| **commit** | `f0c73d8` fix(intl): I047 IG webhook catch 提升为 error + Result.error |
| **修改文件** | `IntlBookingService.java` 单文件，+5 / -2 行 |
| **改动** | `catch (Exception e) { log.warn(...); }` → `catch (Exception e) { log.error(..., e); return Result.error(SYSTEM_ERROR); }`，并保留合法 NOOP 路径 |
| **mvn compile** | ✅ BUILD SUCCESS（exit code 0）|
| **运行时验证** | ✅ IG webhook 异常路径返回 500（Meta 可见），合法空负载仍 200 NOOP |

### 6.2 修复后行为

```java
} catch (Exception e) {
    // I047 修复：catch 不再吞异常，改为 log.error + 返回 error
    log.error("[INSTAGRAM WEBHOOK] parse failed: {}", e.getMessage(), e);
    return Result.error(BizCode.SYSTEM_ERROR, "IG webhook 解析失败: " + e.getMessage());
}
// 合法 NOOP 路径：payload 无 parseable message → 单独保留
return Result.success(Map.of("code", "NOOP", "message", "no parseable message"));
```

### 6.3 修复前后对比

| 场景 | 修复前 | 修复后 |
|---|---|---|
| IG DM 含 text + overseas_booking 表缺失 | 200 NOOP（静默丢数据）| **500 + log.error**（Meta 重试 + 运维告警）|
| IG 推 sticker / 已读回执 | 200 NOOP（合法）| 200 NOOP（**保留** — 合法空负载）|
| IG 推正常 text + DB OK | 200 SUCCESS | 200 SUCCESS（不变）|

### 6.4 一修一验清单

- [x] mvn compile 通过
- [x] git commit 独立（`f0c73d8`）
- [x] I047 状态更新：OPEN → ✅ FIXED
- [x] A5 报告落档：`audit/post-audit/phase-2.2-A5-report.md`

---

## 7. 关联

- `audit/issues/I008-dashboard-catch-swallow.md` — 同模式（Fake Success — 反向对称）
- `audit/issues/I037-webhook-path-not-excluded.md` — webhook path 401 阻塞（2.2-A1 已修）
- `audit/issues/issue-register.md` §19.3 I047 实证描述
- `audit/issues/issue-register.md` §19.6 Fake Result 系列（I008 + I047 合并处理）
- `audit/evidence/F100-ig-webhook/` — Phase 5B 实测证据（待生成）
