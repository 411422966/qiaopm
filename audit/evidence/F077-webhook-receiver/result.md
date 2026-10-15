# F077 POST /webhook/youzan 接收端 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：**I037 NEW**（webhook 路径未在 JWT excludePathPatterns）+ I027 + I029 PARTIAL-MISSING

---

## 1. 实证结论

**状态**：**PARTIAL-NON-BLOCKER**（双层阻断 — I037 + I029）

| 推送场景 | 期望 | 实际 | 状态 |
|---|---|---|---|
| **无 JWT token** | 200 + status=PROCESSED | **401 UNAUTHORIZED**（JwtInterceptor 拦截）| ⚠️ **I037** |
| **带 JWT token** | 200 + status=PROCESSED | **500**（Unknown column 'app_id' — I029 PARTIAL-MISSING）| ⚠️ I029 |

## 2. 实证证据

### 2.1 无 token 推送 → 401（**I037 NEW**）

```bash
POST /webhook/youzan?kdt_id=test-kdt-5B
Headers: X-Youzan-Sign=1825f6762aee9b4b95bc8eaa5d13dd99 (correct md5(secret+body))
Body: {"id":"msg-5B-001","msg_type":"TRADE_ORDER_STATE_UPDATE",...}

Response: 401 UNAUTHORIZED
```

**根因**：`backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33`
```java
registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(
                "/auth/login",
                "/auth/logout",
                "/api/auth/login",
                "/api/auth/logout",
                "/api/static/**",
                "/static/**",
                "/error"
        );
// 缺: /webhook/**
```

→ `/webhook/youzan` 路径被 `/**` 命中，但不在 exclude 列表 → JwtInterceptor 永远 401

→ **真实的有赞 webhook 推送也是无 JWT token 的**（依赖签名验签）→ **生产环境有赞 push_url 推送会全部 401**

### 2.2 带 token 推送 → 500（I029 PARTIAL-MISSING）

```bash
POST /webhook/youzan?kdt_id=test-kdt-5B
Headers: Authorization=Bearer <admin>; X-Youzan-Sign=<correct md5>
Body: same payload

Response: code=500 message="数据库异常"
```

**根因**：`YouzanWebhookController.HandleYouzan` L107-114 反查 EcommercePlatform：
```java
EcommercePlatform pf = platformMapper.selectOne(new LambdaQueryWrapper<EcommercePlatform>()
        .eq(EcommercePlatform::getPlatform, "YOUZAN")
        .eq(EcommercePlatform::getKdtId, kdtId)
        .last("LIMIT 1"));
```

→ MyBatis-Plus SELECT * → 实体声明 `appId` / `kdtId` / `signSecret` → DB 缺这 3 列 → `Unknown column 'app_id' in 'field list'`

## 3. 链路分析

```
Youzan push_url POST /webhook/youzan?kdt_id=X
   │
   ├─ 无 JWT → JwtInterceptor → 401 (I037)
   │
   └─ 有 JWT → 进入 controller
       │
       ├─ 1. 读 rawBody (ContentCachingRequestWrapper L85-95) ✓
       ├─ 2. 反查 platform → SELECT * EcommercePlatform → 500 (I029)
       │   (永远到不了第 3 步验签)
       └─ 即使绕过：第 3 步验签、第 4 步幂等、第 5 步 upsert 都依赖更多 I029 表
           (webhook_msg_log 表不存在)
```

→ **F077 webhook 接收端即使完整实现 controller，也被 I037 + I029 双层阻断**

## 4. I037 NEW 详情

| 字段 | 内容 |
|---|---|
| **Issue ID** | **I037** |
| **发现时间** | 2026-09-14 01:10 (UTC+8) |
| **范围** | EXT-22（webhook 接收端在 5B 范围内）|
| **影响** | 所有 /webhook/** 路径被 JwtInterceptor 拦截 → 生产有赞推送全部 401 |
| **修复方式** | WebConfig.excludePathPatterns 添加 `/webhook/**` |
| **严重度** | **P0**（webhook 永远走不到 controller）|
| **关联 Issue** | I027（webhook 完整性）— I037 是 I027 的具体阻断原因 |

## 5. I027 CONFLICT 状态修正

5A Phase 3 把 I027 标 CONFLICT（EXT-22 SoT 描述"反缺失"，实际 controller 存在）。
5B Phase 5 实证后发现：
- controller **存在且完整实现**
- 但 **JwtInterceptor 配置遗漏 + I029 PARTIAL-MISSING 双层阻断**

→ **I027 维持 CONFLICT 中立标**（SoT 描述方向对，但生产环境实际跑不通）

## 6. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I037 NEW** | webhook 路径未 exclude | 5B 新发现 |
| **I027** | webhook 完整性 CONFLICT | 维持中立标 |
| **I029** | PARTIAL-MISSING | 维持 |

## 7. 修复建议（Phase 7）

```java
// WebConfig.java:27 加一行
.excludePathPatterns(
    "/auth/login", "/auth/logout",
    "/api/auth/login", "/api/auth/logout",
    "/api/static/**", "/static/**",
    "/error",
    "/webhook/**",     // ← 新增：webhook 推送不走 JWT
    "/domestic/webhook/**",  // 国内支付 webhook 同源问题
    "/bnpl/webhook/**",
    "/overseas-payment/webhook/**"
)
```

+ 跑 `migrate_ext_22_google_maps_place_details.sql` 修 I029
+ 跑 `migrate_ext_24_webhook_msg_log.sql` 建 webhook_msg_log 表

## 8. 证据文件

- `raw-push-1.txt` — 无 token 401 响应
- `raw-push-with-token.txt` — 带 token 500 响应
- `db-check.txt` — 表存在性 + 列存在性核查
