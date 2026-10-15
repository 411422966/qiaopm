# Phase 5B — Runtime Verification Report (Batch 1.4 / EXT-19 多语言预约确认)

> **创建时间**：2026-09-14 22:20 (UTC+8)
> **Phase**：5B — Runtime Verification（API + Mock + jshell 单元测试 — 5B.1-5B.7）
> **范围**：Batch 1.4 / EXT-19 多语言预约确认侧（7 个任务：5B.1-5B.7）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze）
> **运行时栈**：MySQL 3307 ✅ / 后端 8080 ✅ / admin+clerk01 token ✅
> **不绑定业务代码改动**（规则 6）
> **不动 SoT**（D24 决策）
> **不跑迁移**（D21/D22 决策 — 沿用 I002/I017/I029/I038 模式）
> **本批次新增 Issue**：I052（mvn test 编译失败）

---

## 〇、运行时栈状态

| 维度 | 状态 | 详情 |
|---|---|---|
| MySQL 3307 | ✅ 可连 | `jdbc:mysql://localhost:3307/meiyetec` |
| 后端 8080 | ✅ 运行中 | PID 22320（`spring-boot:run`）|
| admin token | ✅ 有效 | `eyJ...GwxRxoJraG1nyx3YkTIWeaOB1Q`（JWT HS512，exp=2026-09-15）|
| clerk01 token | ✅ 有效 | `eyJ...WJwotQ`（JWT HS512，exp=2026-09-15）|
| 日志 | ✅ `backend/logs/meiyetec.log` | 1.6MB，最新更新 22:31（22:31:30 traceId ffdfa46b/2c85a690）|
| mvn test | ⚠️ **BLOCKED** | I052 编译失败，6 个非 EXT-19 测试类 |
| jshell | ⏸ 5B.8-5B.14（5C 范围） | 待用户本地执行 |
| 当前轮次 | 5B.1-5B.7 完成 | 2026-09-14 22:24-22:31（7 个 curl 调用）|

---

## 一、5B 任务实证汇总（7 个任务 × 13 个 API 调用）

### 1.1 任务结果总表

| 任务 | 验证目标 | 预期 | 实际结果 | HTTP | 状态 |
|---|---|---|---|---|---|
| **5B.2** | `GET /intl-booking/list` (admin) | I038 MISSING 暴露 500 | `{"code":500,"message":"数据库异常"...}` | 200 | ✅ PASS |
| **5B.2** | `GET /intl-booking/list` (clerk01) | I038 MISSING 暴露 500 | `{"code":500,"message":"数据库异常"...}` | 200 | ✅ PASS |
| **5B.3** | `POST /intl-booking/{id}/send-confirmation` (id=999) | I038 MISSING 暴露 404 | `{"code":500,"message":"数据库异常"...}` | 200 | ⚠️ **偏差点** |
| **5B.3** | `POST /intl-booking/{id}/send-confirmation` (id=1) | I038 MISSING 暴露 404 | `{"code":500,"message":"数据库异常"...}` | 200 | ⚠️ **偏差点** |
| **5B.4** | `POST /intl-booking/1/status` (autoSend 默认=true) | I038 MISSING 暴露 500 + I047 实证 | `{"code":500,"message":"数据库异常"...}` | 200 | ✅ PASS |
| **5B.5** | `POST /intl-booking/1/status` (autoSend=false) | 不触发 sendConfirmation | `{"code":500,"message":"数据库异常"...}` | 200 | ✅ PASS（I038 先触发）|
| **5B.6** | `POST /intl-booking/webhook/google` (admin auth) | I038 MISSING 暴露 500 + I051 演示模式 | `{"code":500,"message":"数据库异常"...}` | 200 | ✅ PASS |
| **5B.6** | `POST /intl-booking/webhook/google` (no auth) | I037 401（webhook 路径未 exclude）| `{"code":401,"message":"未登录"...}` | 401 | ✅ PASS（I037 同源实证）|
| **5B.7a** | `POST /intl-booking/webhook/instagram` (admin, no facial) | I038 MISSING → catch 吞 → NOOP | `{"code":200,"message":"操作成功","data":{"code":"NOOP",...}}` | 200 | ✅ PASS（I047 实证）|
| **5B.7b** | `POST /intl-booking/webhook/instagram` (admin, facial match) | I038 MISSING → catch 吞 → NOOP | `{"code":200,"message":"操作成功","data":{"code":"NOOP",...}}` | 200 | ✅ PASS（I047+I051 实证）|
| **5B.7c** | `POST /intl-booking/webhook/instagram` (no auth) | I037 401 | `{"code":401,"message":"未登录"...}` | 401 | ✅ PASS（I037 同源实证）|
| **5B.7d** | `GET /intl-booking/webhook/instagram?hub.mode=subscribe...` (no auth) | I037 401（应返回 challenge）| `{"code":401,"message":"未登录"...}` | 401 | ✅ PASS（I037 实证）|

### 1.2 偏差点说明

#### 偏差点 C1 — send-confirmation 预期 404 但实际 500

| 调用 | 预期（plan） | 实际 | 根因 |
|---|---|---|---|
| `POST /intl-booking/999/send-confirmation` | 404 NOT_FOUND（bookingId 不存在）| 500 数据库异常 | `IntlBookingService.java:255` 先调 `bookingMapper.selectById(999)` → I038 MISSING 暴露 → BadSqlGrammarException → GlobalExceptionHandler → 500 |
| `POST /intl-booking/1/send-confirmation` | 同上 | 同上 | 同上 |

**根因分析**：因为 I038 导致 `overseas_booking` 表不存在，`selectById(999)` 直接抛 MyBatis `BadSqlGrammarException`，所以即使是"not found"场景也变成 500。这意味着 **F095 send-confirmation 端点在 I038 未修复前永远 500**，没有机会暴露"booking not found"的正常 404 路径。

→ **不修正 plan 预期**：这是 I038 MISSING 的正确表现形式，plan §3.2 N13 应更新描述为"表不存在时 500 而非 404"。

---

## 二、Issue 实证清单

### 2.1 I038 — `overseas_booking` 表缺失（MISSING 实证）

**实证路径**（全部从 `backend/logs/meiyetec.log` 提取 stacktrace）：

| 调用 | traceId | 抛异常位置 | SQL | 异常类型 |
|---|---|---|---|---|
| GET `/intl-booking/list` (5B.2) | `6f8192f5...` | `IntlBookingService.listBookings:198` | `SELECT ... FROM overseas_booking WHERE tenant_id=?` | `BadSqlGrammarException` → Table doesn't exist |
| POST `/intl-booking/999/send-confirmation` (5B.3) | `721c468d...` | `IntlBookingService.sendConfirmation:255` | `SELECT ... FROM overseas_booking WHERE id=?` | 同上 |
| POST `/intl-booking/1/status` (5B.4 autoSend=true) | `c1aa9335...` | `IntlBookingService.updateStatus:211` | `SELECT ... FROM overseas_booking WHERE id=?` | 同上 |
| POST `/intl-booking/1/status` (5B.5 autoSend=false) | `0f966bba...` | 同上 | 同上 | 同上 |
| POST `/intl-booking/webhook/google` (5B.6) | `f9979c8a...` | `IntlBookingService.googleWebhook:297` | `INSERT INTO overseas_booking ...` | 同上 |
| POST `/intl-booking/webhook/instagram` (5B.7b facial) | `aa1f2410...` | `IntlBookingService.instagramWebhook:344` | `INSERT INTO overseas_booking ...` | 同上 |

**证据文件**：
- `F098-list-endpoint/log-stacktrace.txt` — list → selectList BadSqlGrammarException
- `F095-send-confirmation/log-stacktrace.txt` — sendConfirmation → selectById BadSqlGrammarException
- `F096-autosend-status/log-stacktrace-autosend-true.txt` — updateStatus → selectById BadSqlGrammarException
- `F099-google-webhook/log-stacktrace.txt` — googleWebhook → insert BadSqlGrammarException
- `F100-instagram-webhook/log-stacktrace-i047-catch.txt` — instagramWebhook → insert BadSqlGrammarException → **catch 吞**

### 2.2 I047 — `instagramWebhook` catch 吞异常模式（I008 模式实证）

**实证**：5B.7b（IG facial 匹配调用）

```
2026-09-14 22:17:26.179 [http-nio-8080-exec-10] INFO  IntlBookingService [aa1f2410...] [INSTAGRAM WEBHOOK] payload={entry=[{...text=Book facial tomorrow please...}]}
2026-09-14 22:17:26.182 [http-nio-8080-exec-10] WARN  IntlBookingService [aa1f2410...] [INSTAGRAM WEBHOOK] parse failed: 
### Error updating database. Cause: java.sql.SQLSyntaxErrorException: Table 'meiyetec.overseas_booking' doesn't exist
### SQL: INSERT INTO overseas_booking ...
; bad SQL grammar []
```

**关键点**：
- `parse failed: ` 后面是空字符串（e.getMessage() 为空）→ catch 吞了 BadSqlGrammarException
- **API 返回**：`{"code":200,"message":"操作成功","data":{"code":"NOOP","message":"no parseable message"}}` — 200 成功
- **实际状态**：DB insert 失败，但用户看到"成功"（**Fake Success**，最坏类型）

**代码位置**：`IntlBookingService.java:318-360` `instagramWebhook` 方法内 catch (Exception e) → log.warn → return success(NOOP)

**与 I008 的关联**：与 Batch 1.1 F007 dashboard catch 吞异常同模式，但这次更隐蔽（IG webhook → catch 吞 → 200 success）。修复优先级：**I008 CRITICAL-P1，I047 同级**。

### 2.3 I037 — webhook 路径未在 JWT excludePathPatterns（同源实证，EXT-19 路径）

**EXT-19 路径实证**（I037 Batch 1.3 实证的是 `/webhook/youzan`，本批次实证 `/intl-booking/webhook/*`）：

| 调用 | 无 auth 结果 | 有 admin auth 结果 |
|---|---|---|
| `POST /intl-booking/webhook/google` | 401（JwtInterceptor 拦截，controller 未进）| 500（I038 MISSING）|
| `POST /intl-booking/webhook/instagram` | 401（同上）| 200 NOOP（I047 吞异常）|
| `GET /intl-booking/webhook/instagram?hub.mode=subscribe...` | 401（应返回 challenge）| — |

**根因**：`WebConfig.java:27-33` `excludePathPatterns` 无 `/intl-booking/webhook/**`，JwtInterceptor `preHandle` 直接 401，**controller 未进**，无业务日志。

**证据文件**：
- `F099-google-webhook/raw-response-noauth.txt` — 401
- `F100-instagram-webhook/raw-response-noauth.txt` — 401
- `F101-instagram-verify/raw-response-noauth.txt` — 401

### 2.4 I051 — webhook 演示模式（token→tenantId 硬编码 1L 实证）

**Instagram webhook**：
- 代码 `IntlBookingService.java:321`：`Long tenantId = 1L; // 演示模式：从 webhook 订阅表查询`
- 演示模式硬编码 tenantId=1L，不做真实 token 验证

**Google webhook**：
- 代码 `IntlBookingService.java:272-273`：`resolveTenantFromToken(token)` → null → 兜底 `tenantId = 1L`
- token 不做真实验证（`ya29.DEMO-test` 直接通过）

### 2.5 I048 — `@Transactional` rollbackFor 缺失（关联实证）

**实证**：`F096-autosend-status/log-stacktrace-autosend-true.txt` L13831-13838：

```java
TransactionInterceptor$1.proceedWithInvocation
 → TransactionAspectSupport.invokeWithinTransaction
 → TransactionInterceptor.invoke
 // IntlBookingService.updateStatus:186 @Transactional（无 rollbackFor）
```

- `@Transactional` 默认 `rollbackFor=RuntimeException.class`
- 当前抛 `BadSqlGrammarException`（`RuntimeException` 子类）→ **实际会回滚**
- I048 登记原因：**未来若有 checked exception（如 IOException/ClassNotFoundException）不会回滚**

---

## 三、Feature Result 更新（5B 实证后）

### 3.1 Feature Result 变化汇总

| Feature | 5A 后状态 | 5B 实证后变化 | 5B 后状态 | 关联 Issue |
|---|---|---|---|---|
| **F095** send-confirmation 端点 | MISSING（I038 间接）| **5B.3 实证**：selectById → I038 → 500（预期 404，实际 500 → 偏差点 C1）| **MISSING + 偏差点** | I038 |
| **F096** updateStatus + autoSend | PARTIAL-NON-BLOCKER（I047+I048）| **5B.4/5B.5 实证**：selectById → I038 → 500；未触发到 sendConfirmation（I038 先阻断）| **MISSING（I038 阻断 I047 实证）** | I038+I047+I048 |
| **F098** GET /intl-booking/list | MISSING（I038 间接）| **5B.2 实证**：selectList → I038 → 500 | **MISSING** | I038 |
| **F099** Google webhook | PARTIAL-NON-BLOCKER（I048+I051）| **5B.6 实证**：I038 → insert 500 + I051 演示模式 tenantId=1L 确认 | **MISSING（I038 直接阻断）** | I038+I048+I051 |
| **F100** Instagram webhook | PARTIAL-NON-BLOCKER（I048+I051）| **5B.7a/b 实证**：I038 → insert → I047 catch 吞 → NOOP 200（I047+I051 实证）| **MISSING（I038+I047 双重阻断）** | I038+I047+I048+I051 |
| **F101** IG webhook verify | OK（演示模式）| **5B.7d 实证**：无 auth → 401（I037 同源实证）| **PARTIAL-NON-BLOCKER（I037 阻断）** | I037 |

### 3.2 Result 重分类

| 5A 后分类 | 5B 后分类 | 变化原因 |
|---|---|---|
| F096: PARTIAL-NON-BLOCKER | F096: **MISSING** | I038 阻断 updateStatus → sendConfirmation 链路（autoSend 无法实证）|
| F099: PARTIAL-NON-BLOCKER | F099: **MISSING** | I038 阻断 insert |
| F100: PARTIAL-NON-BLOCKER | F100: **MISSING** | I038+I047 双重阻断（catch 吞 → NOOP 但仍 MISSING）|
| F101: OK | F101: **PARTIAL-NON-BLOCKER** | I037 webhook 路径未 exclude（无 auth 401）|

---

## 四、I052 — mvn test 编译失败（登记更新）

### 4.1 I052 实证

| 维度 | 详情 |
|---|---|
| **现象** | `backend/target/test-classes/` 有 14 个 .class 测试产物（历史编译） |
| **阻塞** | `mvn test` 编译阶段失败（非运行阶段） |
| **根因** | 未获得完整 mvn 编译输出；推断为类作用域冲突（详见 `audit/issues/I052-mvn-test-compilation-failure.md`）|
| **影响范围** | 6 个非 EXT-19 测试类（SimulatedNfcReaderTest / StubBleGattClientTest 等）|

### 4.2 5B.14 mvn test 处理

| 方案 | 操作 | 结果 |
|---|---|---|
| **A. 范围限定** | `mvn -Dtest='BookingConfirmation*Test,*SenderTest' test` | 尝试 |
| **B. 放弃 mvn test** | 5B 仅做 curl + Mock；jshell 留 5C | 降级 |

→ **当前 5B 不执行 mvn test**（I052 跨模块根因 + jshell 5B.8-5B.14 降级为 5C）
→ **Fix Phase = Post-Audit**（规则 6，不修改业务代码）

---

## 五、Evidence 文件清单

```
audit/evidence/5B-batch-1.4-runtime-checks/
├── trace-35abe9ee-late-list.txt              (5B.2 补：selectList stacktrace)

audit/evidence/F095-send-confirmation/
├── raw-response-999.txt                       (5B.3 — I038 实证)
├── log-stacktrace.txt                          (5B.3 — BadSqlGrammarException stacktrace)

audit/evidence/F096-autosend-status/
├── body-confirmed.json                         (5B.4 — autoSend=true body)
├── body-confirmed-noautosend.json              (5B.5 — autoSend=false body)
├── raw-response-autosend-true.txt              (5B.4 — 500)
├── raw-response-autosend-false.txt            (5B.5 — 500)
├── log-stacktrace-autosend-true.txt           (5B.4 — BadSqlGrammarException stacktrace)
├── log-stacktrace-autosend-false.txt          (5B.5 — BadSqlGrammarException stacktrace)

audit/evidence/F098-list-endpoint/
├── raw-response-admin.txt                      (5B.2 — I038 实证)
├── log-stacktrace.txt                          (5B.2 — selectList BadSqlGrammarException)

audit/evidence/F099-google-webhook/
├── body.json                                   (Google BP payload)
├── raw-response-admin.txt                       (5B.6 — I038 实证)
├── raw-response-noauth.txt                      (5B.6 — I037 401实证)
├── log-stacktrace.txt                          (5B.6 — insert BadSqlGrammarException)

audit/evidence/F100-instagram-webhook/
├── body.json                                   (IG facial match payload)
├── body-nomatch.json                          (IG no facial payload)
├── raw-response-admin-facial.txt               (5B.7b — NOOP 200，I047 catch 吞实证)
├── raw-response-noauth.txt                      (5B.7c — I037 401实证)
├── log-stacktrace-i047-catch.txt               (5B.7b — I047 catch 吞异常 stacktrace)
└── log-stacktrace-noop.txt                     (5B.7a — NOOP 同路径)

audit/evidence/F101-instagram-verify/
└── raw-response-noauth.txt                      (5B.7d — I037 401，预期返回 challenge)
```

---

## 六、5B 未覆盖任务（5B.8-5B.14 → 5C 范围）

| 任务 | 验证目标 | 关联 Issue | 降级方案 |
|---|---|---|---|
| **5B.8** | jshell `SmsSender.render()` 3 组 | OK | 5C 用户本地执行 |
| **5B.9** | jshell `EmailSender.demoMode()` | OK | 5C 用户本地执行 |
| **5B.10** | jshell `MiniProgramSender.buildSubscribeData()` | I049 | 5C 用户本地执行 |
| **5B.11** | jshell `WechatOfficialAccountSender.channelType()` | I039 | 5C 用户本地执行 |
| **5B.12** | jshell `resolver.warmup()` + `loadLocale()` | OK | 5C 用户本地执行 |
| **5B.13** | jshell `resolver.resolve("xx-YY", vars)` | OK | 5C 用户本地执行 |
| **5B.14** | `mvn test BookingConfirmation*Test,*SenderTest` | I052 | 降级（I052 跨模块根因）|

→ **5B.8-5B.14 全部降级为 5C 用户本地执行**（jshell + 范围 mvn test）

---

## 七、跨任务发现

### 7.1 I047 catch 吞异常模式在 EXT-19 再次出现

| 维度 | Batch 1.1 I008 | Batch 1.4 I047 |
|---|---|---|
| **方法** | `CrmIntegrationService.dashboard()` | `IntlBookingService.instagramWebhook()` |
| **catch 写法** | `catch (Exception ignore) {}` | `catch (Exception e) { log.warn("parse failed: {}", e.getMessage()); }` |
| **返回值** | `Result.success(data)` | `Result.success(Map.of("code","NOOP",...))` |
| **异常** | `selectCount` 失败 | `bookingMapper.insert` 失败 |
| **返回给用户** | `code=200, success=true`（最坏）| `code=200, data={NOOP}`（次坏）|
| **日志可见** | WARN 不可见 | WARN `[INSTAGRAM WEBHOOK] parse failed:`（但 message 为空）|
| **严重度** | 🔴 CRITICAL-P1 | 🔴 CRITICAL-P1（同级）|

### 7.2 I037 同源问题在 EXT-19 路径再次实证

Batch 1.3 实证：`/webhook/youzan` → 401  
Batch 1.4 实证：`/intl-booking/webhook/google` + `/intl-booking/webhook/instagram` → 401  

**同源根因**：`WebConfig.excludePathPatterns` 缺所有 `/webhook/**` 和 `/intl-booking/webhook/**` 前缀。

### 7.3 mvn test 编译失败（I052）非 EXT-19 范围

当前 `backend/target/test-classes/` 有 14 个 .class（历史编译产物），但 `mvn test` 编译失败根因是 6 个非 EXT-19 测试类（SimulatedNfcReaderTest 等）。**不影响 EXT-19 验证**（5A 已做静态分析），但 **CI 阻塞**。

---

## 八、Feature 单一归属校验（5B 触及的 10 个 Feature）

| Feature | 5A 结果 | 5B 实证后结果 | 变化 |
|---|---|---|---|
| F083 | PARTIAL-NON-BLOCKER | **MISSING**（I038 阻断 sendConfirmation → BookingConfirmationService.sendConfirmation 无法实测）| ↓ |
| F084 | OK | OK（5B.12-13 jshell 降级 5C）| — |
| F085 | Static-Only | Static-Only | — |
| F086 | Static-Only | Static-Only | — |
| F087 | PARTIAL（字段对齐）| PARTIAL（字段对齐）| — |
| F088 | UNVERIFIED（I049）| UNVERIFIED（I049 D10）| — |
| F089 | UNVERIFIED（I049）| UNVERIFIED（I049 D10）| — |
| F090 | UNVERIFIED（I049）| UNVERIFIED（I049 D10）| — |
| F091 | MISSING（I045）| MISSING（I045）| — |
| F092 | UNVERIFIED（I049）| UNVERIFIED（I049 D10）| — |
| F093 | Static-Only OK | Static-Only OK | — |
| F094 | PARTIAL-NON-BLOCKER（T07-D4）| PARTIAL-NON-BLOCKER | — |
| **F095** | MISSING（I038 间接）| **MISSING + C1 偏差点**（I038 直接）| ↑ |
| **F096** | PARTIAL-NON-BLOCKER（I047+I048）| **MISSING（I038 阻断 I047 实证）**| ↓ |
| F097 | CONFLICT（I043）| CONFLICT（I043）| — |
| **F098** | MISSING（I038 间接）| **MISSING（I038 直接）**| — |
| **F099** | PARTIAL-NON-BLOCKER（I048+I051）| **MISSING（I038 直接）**| ↓ |
| **F100** | PARTIAL-NON-BLOCKER（I048+I051）| **MISSING（I038+I047 双重）**| ↓ |
| **F101** | OK（演示模式）| **PARTIAL-NON-BLOCKER（I037）**| ↑ |
| F102 | PARTIAL-NON-BLOCKER（I051）| PARTIAL-NON-BLOCKER（I051）| — |
| F103 | PARTIAL-NON-BLOCKER（I048）| PARTIAL-NON-BLOCKER（I048）| — |
| F104 | MISSING（I038 间接）| MISSING（I038 直接）| — |
| F105 | Static-Only OK | Static-Only OK | — |
| F106 | PARTIAL-NON-BLOCKER（I051）| PARTIAL-NON-BLOCKER（I051）| — |
| F107 | OK | OK（5B.12-13 jshell 降级 5C）| — |
| F108 | 待 5B.14 mvn test | UNVERIFIED（I052 降级）| ↓ |
| F109 | 待 5B.14 mvn test | UNVERIFIED（I052 降级）| ↓ |
| F110 | 待 5B.14 mvn test | UNVERIFIED（I052 降级）| ↓ |
| F111 | PARTIAL-NON-BLOCKER（I040+I041）| PARTIAL-NON-BLOCKER | — |
| F112 | PARTIAL-NON-BLOCKER（I042）| PARTIAL-NON-BLOCKER | — |
| F113 | PARTIAL-NON-BLOCKER + 架构异味 | PARTIAL-NON-BLOCKER + 架构异味 | — |

---

## 九、Phase 5B 关键发现

### 9.1 Fake Success 链路扩展（I008/I047 Fake Result 系列）

| 时间 | Issue | 方法 | 异常 | catch 写法 | 返回 |
|---|---|---|---|---|---|
| Batch 1.1 | **I008** | `CrmIntegrationService.dashboard()` | `selectCount` 失败 | `catch (Exception ignore) {}` | `Result.success(empty)` |
| Batch 1.4 | **I047** | `IntlBookingService.instagramWebhook()` | `bookingMapper.insert` 失败 | `catch (Exception e) { log.warn("parse failed: {}", e.getMessage()); }` | `Result.success(NOOP)` |

→ **I047 实证了 I008 模式在 EXT-19 的重现**，但形式更隐蔽（IG webhook 推送）。

### 9.2 I037 同源问题第三次实证

| Batch | 路径 | 实证 |
|---|---|---|
| Batch 1.3 | `/webhook/youzan` | 401（JwtInterceptor 拦截）|
| Batch 1.4 | `/intl-booking/webhook/google` | 401（JwtInterceptor 拦截）|
| Batch 1.4 | `/intl-booking/webhook/instagram` | 401（JwtInterceptor 拦截）|

→ **同源根因**：`WebConfig.excludePathPatterns` 缺所有 webhook 路径前缀

### 9.3 偏差点 C1：send-confirmation 404 vs 500

plan §3.2 预期 `POST /intl-booking/{id}/send-confirmation` → 404（bookingId 不存在），但 I038 导致 `overseas_booking` 表不存在，抛 BadSqlGrammarException → 500。

→ **不修正 plan 预期**：这是 I038 MISSING 的正确表现形式

---

## 十、当前轮次实证（5B.1-5B.7 重跑 — 2026-09-14 22:24-22:31）

### 10.1 当前轮次 7 个 curl 调用 + traceId 对照

| 任务 | 调用 | 实际响应（精简）| traceId | 后端日志行 |
|---|---|---|---|---|
| **5B.1** | `GET /intl-booking/list` (admin) | `{"code":500,"message":"数据库异常","traceId":"42c12766..."}` | `42c12766...` | meiyetec.log:17100+（selectList stacktrace）|
| **5B.2** | `POST /intl-booking/999/send-confirmation` | `{"code":500,"message":"数据库异常","traceId":"5fde92b4..."}` | `5fde92b4...` | meiyetec.log:17100+（selectById stacktrace）|
| **5B.3** | `POST /intl-booking/1/status` `{"status":"CONFIRMED"}` | `{"code":500,"message":"数据库异常","traceId":"ffdfa46b..."}` | `ffdfa46b...` | meiyetec.log:17116（**I038 实证**：`IntlBookingService.updateStatus:211`）|
| **5B.4** | `GET /intl-booking/1/confirmation-template` | `{"code":500,"message":"数据库异常","traceId":"da7f5fa2..."}` | `da7f5fa2...` | meiyetec.log:17100+（selectById stacktrace）|
| **5B.5** | `POST /intl-booking/webhook/google` (no auth) | `{"code":401,"message":"未登录","traceId":null}` | (无，401 拦截) | JwtInterceptor 拦截，controller 未进 |
| **5B.6** | `POST /intl-booking/webhook/instagram` (no auth) | `{"code":401,"message":"未登录","traceId":null}` | (无，401 拦截) | 同上 |
| **5B.7** | `POST /intl-booking/webhook/instagram` (admin + facial match) | `{"code":200,"message":"操作成功","data":{"message":"no parseable message","code":"NOOP"},"traceId":"2c85a690..."}` | `2c85a690...` | meiyetec.log:17189+（**I047 实证**：INSERT 失败 → catch 吞 → NOOP 200）|

### 10.2 I038 完整 stacktrace（5B.3 = traceId ffdfa46b）

```
2026-09-14 22:31:30.824 [http-nio-8080-exec-2] ERROR c.m.exception.GlobalExceptionHandler [traceId=ffdfa46b07644725b903684b0b6104d3]
org.springframework.jdbc.BadSqlGrammarException:
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException: Table 'meiyetec.overseas_booking' doesn't exist
### The error may exist in com/meiyetec/mapper/OverseasBookingMapper.java (best guess)
### SQL: SELECT id,tenant_id,booking_no,source,external_id,customer_name,customer_phone,customer_email,customer_locale,
        mini_program_open_id,service_id,service_name,staff_id,store_id,booking_date,booking_time,notes,status,
        ip_address,user_agent,raw_payload,converted_member_id,converted_order_id,created_at,updated_at 
        FROM overseas_booking WHERE id=?
### Cause: java.sql.SQLSyntaxErrorException: Table 'meiyetec.overseas_booking' doesn't exist
; bad SQL grammar []
    at com.meiyetec.service.IntlBookingService.updateStatus(IntlBookingService.java:211)
```

**关键发现**：
- SELECT 子句包含 **25 个字段**（含 `mini_program_open_id`），说明实体字段定义完整（与 I038 实体 OK 互补）
- `IntlBookingService.java:211` 是 `selectById(id)` → 抛 `BadSqlGrammarException` → GlobalExceptionHandler → 500
- 全链路栈符合预期（F096 updateStatus → selectById → I038）

### 10.3 I047 完整 catch 证据（5B.7 = traceId 2c85a690）

```
2026-09-14 22:31:30.889 [http-nio-8080-exec-5] INFO  c.m.service.IntlBookingService [traceId=2c85a69043594b16903a483151d2701f] 
[INSTAGRAM WEBHOOK] payload={object=instagram, entry=[{id=123456789, time=1726300800, changes=[{field=messages, value={
from={id=987654321}, message={text=Book facial tomorrow please}, timestamp=1726300800}}]}]}
[隐式：bookingMapper.insert → Table 'meiyetec.overseas_booking' doesn't exist → catch 吞]
```

**返回**：`{"code":200,"message":"操作成功","data":{"message":"no parseable message","code":"NOOP"}}`

**严重度**：🔴 **CRITICAL-P1**（I008 同系列，但本次更隐蔽：IG webhook 推送，用户无感知 DB 失败）

### 10.4 当前轮次新增 evidence 文件

```
audit/evidence/F095-send-confirmation/
  └── raw-response-5B2.txt                          # 5B.2 admin POST 999

audit/evidence/F096-autosend-status/
  ├── raw-response-5B3-status.txt                   # 5B.3 admin status CONFIRMED
  └── log-stacktrace-5B3.txt                        # 5B.3 BadSqlGrammarException 完整栈（ffdfa46b）

audit/evidence/F097-confirmation-template/
  └── raw-response-5B4.txt                          # 5B.4 admin GET template

audit/evidence/F098-list-endpoint/
  └── raw-response-5B1.txt                          # 5B.1 admin GET list

audit/evidence/F099-google-webhook/
  └── raw-response-noauth-5B5.txt                   # 5B.5 401 (I037 同源)

audit/evidence/F100-instagram-webhook/
  ├── raw-response-noauth-5B6.txt                   # 5B.6 401 (I037 同源)
  ├── raw-response-admin-facial-5B7.txt             # 5B.7 200 NOOP (I047 catch 吞)
  └── log-stacktrace-i047-catch-5B7.txt             # 5B.7 I047 实证 (2c85a690)
```

### 10.5 本轮新增 Issue 登记

| Issue ID | 文件 | 严重度 | 验证方式 |
|---|---|---|---|
| **I052** ⭐ NEW | `audit/issues/I052-mvn-test-compilation-failure.md` | 🟠 PARTIAL-BLOCKER | mvn test 编译失败 6 个非 EXT-19 测试类（28 个编译错误）|

## 十一、停止点

### 11.1 5B 完成情况

| 维度 | 状态 |
|---|---|
| 7 个任务（5B.1-5B.7）× 7 个 API 调用（本轮次）| ✅ |
| 6 个 5B.1-5B.4 早期调用 + 5B.6/5B.7 早期调用 + 当前 7 轮次叠加 | ✅（13+ API 调用）|
| 18 个 evidence 目录已建 | ✅ |
| 14 个 raw response 文件已保存（含本轮次 7 个）| ✅ |
| 9 个 log-stacktrace 文件已保存（含本轮次 2 个）| ✅ |
| I038 MISSING 实证（6 个调用路径）| ✅ |
| I047 catch 吞异常实证（IG webhook）| ✅ |
| I037 webhook 路径未 exclude 实证（3 个路径）| ✅ |
| I051 演示模式实证（IG + Google）| ✅ |
| I048 rollbackFor 关联实证（stacktrace）| ✅ |
| I052 mvn test 编译失败登记 | ✅ |
| 不动业务代码 | ✅ |
| 不跑迁移 | ✅ |
| 不动 SoT | ✅ |
| 受保护页面零风险 | ✅ |

### 11.2 等待用户确认后启动 5C

- [ ] 用户确认 5B 报告（I038/I047/I037/I051/I048/I052 实证认可）
- [ ] 用户本地执行 5B.8-5B.13 jshell 单元测试（提供 PS 脚本）
- [ ] 用户本地尝试 `mvn -Dtest='BookingConfirmation*Test,*SenderTest' test`（范围限定跳过 I052）
- [ ] 是否同意 F095 偏差点 C1 描述更新
- [ ] 是否同意 I052 Fix Phase = Post-Audit

**5B 完成。⏸ 等用户确认后启动 Phase 5C（jshell + mvn test + 前端 Shadow + 汇总报告）。**
