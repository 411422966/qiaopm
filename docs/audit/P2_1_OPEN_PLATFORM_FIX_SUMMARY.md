# P2.1 开放平台补强修复结果

> 审计报告 §10.3 第 685-688 行："开放平台 / Open API 设计 / API Key 管理 / Webhook 机制"

---

## 1. 调研结论

| 审计子项 | 审计判定 | 实际 |
|---|---|---|
| **Open API 设计** | 🔴 MISSING | ✅ **已实装**（P3EcosystemService `/open/app` + `/open/oauth/token` + `verifyToken` + scope + rateLimit + allowedIps） |
| **API Key 管理** | 🔴 MISSING | ✅ **已实装**（`OpenApp.appSecret` + `genSecret` 24 字节 URL-safe + 仅返回一次） |
| **Webhook 机制** | 🔴 MISSING | ✅ **已实装**（`/platform/webhook/{platform}` + sha256 幂等 + `PlatformWebhookLog`） |

**审计判定 3/3 准确**——但**实际已有一套完整骨架**。本次补强：
1. **HMAC-SHA256 签名**（Webhook 原本仅靠 sha256 幂等，无签名抗伪造）
2. **重试 + 死信队列**（Webhook 失败无重试）
3. **API 限流**（rateLimit 字段有但无实际限流）
4. **密钥轮换**（appSecret 不可重新生成）
5. **事件订阅**（多租户多订阅）

---

## 2. 实际修复

### 2.1 新增 SQL

`migrate_r3_0_h_p2_1_open_platform_enhance.sql`：
- `platform_webhook_event` — 10 个预置事件类型
- `platform_webhook_subscription` — 订阅配置（含 targetUrl + signingSecret + retryStrategy）
- `platform_webhook_delivery` — 投递记录（含 attempt + status + nextRetryAt）
- `open_api_quota` — 限流配额（MINUTE/HOUR/DAY/MONTH）
- `open_app_secret_audit` — 密钥轮换审计

### 2.2 新增 Entity（5 个）

| Entity | 用途 |
|---|---|
| `PlatformWebhookEvent` | 事件类型定义 |
| `PlatformWebhookSubscription` | 订阅配置（含 signingSecret） |
| `PlatformWebhookDelivery` | 投递记录（attempt / status / nextRetryAt） |
| `OpenApiQuota` | 限流配额 |
| `OpenAppSecretAudit` | 密钥轮换审计 |

### 2.3 新增 Mapper（5 个）

### 2.4 新增服务：`OpenPlatformEnhanceService`（**10 个新 API**）

#### A. Webhook 订阅（3 个）

| 端点 | 说明 |
|---|---|
| `POST /open/api/v2/webhook/subscription` | 创建订阅（生成 signingSecret） |
| `GET /open/api/v2/webhook/subscription/page` | 订阅分页 |
| `POST /open/api/v2/webhook/subscription/{id}/rotate-secret` | 轮换订阅密钥 |

#### B. Webhook 投递（3 个）

| 端点 | 说明 |
|---|---|
| `POST /open/api/v2/webhook/dispatch` | 分发事件（创建投递 + HMAC 签名） |
| `POST /open/api/v2/webhook/delivery/retry` | 处理投递结果（成功 / 失败重试 / 死信） |
| `GET /open/api/v2/webhook/delivery/dead-letter` | 死信队列 |

#### C. 限流（1 个）

| 端点 | 说明 |
|---|---|
| `GET /open/api/v2/quota/status?appId=X` | 4 窗口配额（MINUTE=60 / HOUR=1000 / DAY=20000 / MONTH=500000） |

#### D. 密钥轮换（2 个）

| 端点 | 说明 |
|---|---|
| `POST /open/api/v2/app/{id}/rotate` | 轮换 App Secret + 审计 |
| `GET /open/api/v2/secret/audit?appId=X` | 密钥操作审计 |

#### E. 事件类型（2 个）

| 端点 | 说明 |
|---|---|
| `GET /open/api/v2/webhook/events` | 事件列表 |
| `POST /open/api/v2/webhook/events` | 新增事件 |

---

## 3. 核心算法

### 3.1 HMAC-SHA256 签名

```java
String signature = "sha256=" + HexFormat.of().formatHex(HmacSHA256(secret, payload));
```

**验证流程**：
1. 接收方读 `X-Signature` 头
2. 用同一 secret 算 HMAC
3. **常时间比较**（防时序攻击）

### 3.2 签名 header 格式

```
POST /your/webhook HTTP/1.1
Content-Type: application/json
X-Webhook-Id: WHD1234567890
X-Webhook-Timestamp: 1692000000
X-Webhook-Signature: sha256=abc123...
```

### 3.3 指数退避重试

```
attempt 1: 30s
attempt 2: 60s
attempt 3: 120s
attempt 4: 240s
attempt 5: 480s
attempt 7+: 3600s（封顶）
```

**死信队列**：`attempt >= maxAttempts(默认5)` → `DEAD_LETTER`

### 3.4 限流（4 窗口）

| 窗口 | 默认配额 | 用途 |
|---|---|---|
| MINUTE | 60 | 突发限流 |
| HOUR | 1000 | 一般防护 |
| DAY | 20000 | 一般配额 |
| MONTH | 500000 | 长期配额 |

**存储**：`open_api_quota`（app_id + window + window_start UNIQUE）
**CAS 扣减**：`ON DUPLICATE KEY UPDATE used_count = IF(window_end <= NOW(), 1, used_count + 1)`

### 3.5 密钥轮换审计

```sql
INSERT INTO open_app_secret_audit (app_id, old_secret_prefix, new_secret_prefix, secret_hash, action)
```

**审计只存前 8 字符 + SHA-256 哈希**，完整 secret 不入库。

---

## 4. 完整 Webhook 投递流程

```
[生产者] dispatch(event, payload)
  ↓
[路由] 匹配订阅（eventsCsv 匹配 / 全部）
  ↓
[签名] HMAC-SHA256(secret, payload)
  ↓
[入库] platform_webhook_delivery (status=PENDING, attempt=1)
  ↓
[异步] 推送 + 启动 timer
  ↓
[回调] delivery/retry (httpStatus, responseBody)
  ├─ 2xx → SUCCESS
  ├─ 4xx（非 429）→ DEAD_LETTER（不再重试）
  ├─ 5xx / 429 → 计算 nextRetryAt, attempt++
  └─ attempt >= maxAttempts → DEAD_LETTER
```

---

## 5. 测试覆盖（23 个新场景）

| 维度 | 测试 | 场景 |
|---|---|---|
| **HMAC 签名** | `hmacSha256_consistentForSameInput` | 相同输入 → 相同签名 |
| | `hmacSha256_differentSecret_differentSignature` | 不同 secret → 不同签名 |
| | `hmacSha256_differentPayload_differentSignature` | 不同 payload → 不同签名 |
| | `verifyHmac_correctSignature_passes` | 正确签名通过 |
| | `verifyHmac_wrongSignature_fails` | 错误签名失败 |
| | `verifyHmac_nullSignature_fails` | null 签名失败 |
| | `verifyHmac_tamperedPayload_fails` | 篡改 payload 失败 |
| **SHA-256** | `sha256Hex_rfc3174_testVector` | "abc" → 标准向量 |
| | `sha256Hex_emptyString` | 空串 → 标准向量 |
| | `sha256Hex_differentInput_differentHash` | 差异性 |
| **重试退避** | `exponentialBackoff_minutes` | 30→60→120→240→480→封顶3600 |
| | `nextRetryAt_scheduledAfter` | 重试时间排期 |
| **限流** | `quotaLimits_minute` / `quotaLimits_complete` | 4 窗口配额 |
| **事件订阅** | `eventSubscription_match_all` | 通配符 * |
| | `eventSubscription_match_specific` | 指定事件 |
| | `eventSubscription_handleSpaces` | 空格处理 |
| | `eventSubscription_empty_notMatch` | 空订阅不匹配 |
| | `eventSubscription_null_notMatch` | null 不匹配 |
| **密钥** | `secretGeneration_urlSafeBase64` | 24 字节 → 32 字符 |
| **签名长度** | `signature_lengthCorrect` | 7 + 64 = 71 字符 |
| **防时序** | `constantTimeCompare_sameLength` | 长度相等 |
| **审计** | `auditPrefix_doesNotLeakFullSecret` | 审计只存前 8 字符 |

---

## 6. 编译 + 测试

- ✅ `mvn compile` 通过
- ✅ 新增 23 个测试全部通过
- ✅ 全量 208 个测试，207 通过；剩 1 个失败为已知预先问题（与本次无关）

---

## 7. 开放平台 Checklist

| 维度 | 审计判定 | 实际 |
|---|---|---|
| **Open API 设计** | 🔴 | ✅ P3 已有（OpenApp + Token + scope） |
| **API Key 管理** | 🔴 | ✅ P3 已有（appSecret + 仅返回一次） |
| **Webhook 机制** | 🔴 | ✅ P3 已有（幂等） |
| **HMAC 签名** | — | ✅ **本次新增** |
| **签名验证** | — | ✅ **本次新增**（常时间比较） |
| **Webhook 重试** | — | ✅ **本次新增**（指数退避） |
| **死信队列** | — | ✅ **本次新增** |
| **API 限流** | — | ✅ **本次新增**（4 窗口） |
| **密钥轮换** | — | ✅ **本次新增**（App + Subscription） |
| **密钥审计** | — | ✅ **本次新增** |
| **事件订阅** | — | ✅ **本次新增**（多租户多订阅） |
| **事件管理** | — | ✅ **本次新增**（10 预置事件） |
| **测试覆盖** | — | ✅ 23 个新场景 |

---

## 8. 累计审计报告修正

| 批次 | 结果 |
|---|---|
| **P0.1** 库存消耗 | ✅ 9 合计 |
| **P0.2** 回访流程 | ✅ 11 新测试 |
| **P0.3** 跨店核销 | ✅ 29 合计 |
| **P1.4** 服务 SOP | ✅ 10 新测试 |
| **P1.5** 评价体系 | ✅ 15 新测试 |
| **P1.6** AI 真实化 | ✅ 17 新测试 |
| **P2.1** 开放平台 | ✅ **23 新测试**（本次） |
| P2.2+ 长期 | 待执行 |

---

## 9. 相关文件

- `backend/src/main/resources/db/sql/migrate_r3_0_h_p2_1_open_platform_enhance.sql`（本次新增）
- `backend/src/main/java/com/meiyetec/service/OpenPlatformEnhanceService.java`（本次新增 10 API）
- `backend/src/main/java/com/meiyetec/entity/{PlatformWebhookEvent,PlatformWebhookSubscription,PlatformWebhookDelivery,OpenApiQuota,OpenAppSecretAudit}.java`（本次新增 5）
- `backend/src/main/java/com/meiyetec/mapper/*`（本次新增 5 个）
- `backend/src/test/java/com/meiyetec/service/OpenPlatformTest.java`（本次新增 23 测试）

---

## 10. 测试统计

| 测试套件 | 场景数 |
|---|---|
| **P2.1 开放平台**（本次新增） | **23** |
| P1.6 AI 真实化 | 17 |
| P1.5 评价体系 | 15 |
| P1.4 SOP 步骤 | 10 |
| P0.1 库存链路 | 9 |
| P0.2 回访补强 | 11 |
| P0.3 跨店补强 | 29 |
| 其他（P1/P2/P3/其他） | 约 94 |
| **总计** | **208** |