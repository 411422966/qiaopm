# I074 — `rateLimitPerDay` API 响应 null vs DB 列默认 5000 不一致（I062 setter 缺失延伸 — 契约漂移）

> **发现时间**：2026-09-16 02:30 (UTC+8)
> **Phase**：5A — Runtime Verification
> **关联 Feature**：F115（MktChannel / 创建渠道配置）
> **严重度**：🟡 **MEDIUM**（API 契约漂移 — 前端拿到 null 与 DB 实际值不一致）
> **类别**：契约漂移（API/DB 不一致）+ 沿用 I062
> **修复阶段**：**Post-Audit**（Phase 7 — 与 I062 合并同类）

---

## 1. 摘要

`MktChannelConfig.rateLimitPerDay` 字段在 `setRateLimitPerDay()` 处**缺失 setter**（沿用 I062 — `MarketingAutomationService.java:81` 空语句），但 DB 列有默认值 `DEFAULT 5000`。Phase 5A 创建渠道时不传 `rateLimitPerDay`，API 响应 `data.rateLimitPerDay = null`，但 DB 实际写入 5000 → **API/DB 契约漂移**。

| 维度 | 值 |
|---|---|
| **触发条件** | `POST /mkt/channel` body 不含 `rateLimitPerDay` 字段 |
| **失败现象** | API 响应 `rateLimitPerDay=null`；DB 实际写入 `5000` |
| **根本原因** | I062 rateLimitPerDay setter 缺失 — 字段不会被 Jackson 反序列化 → 实体字段为 null → 但 DB 列有默认值 5000 |
| **影响范围** | F115 创建渠道 + F118 health-check（rate limit 计算）+ 前端展示 |
| **批次元数据** | Batch 1.5（Phase 5A 实证发现，I062 延伸） |

---

## 2. 实证证据（Phase 5A）

### 2.1 运行时实证

```
POST http://localhost:8080/mkt/channel
Body: {"channelName":"5A-ToBeDeleted","channelType":"SMS","provider":"Test",
       "rateLimitPerMin":100}
→ HTTP 200 / code 200 / message: "创建成功"
→ response.data.rateLimitPerDay = null
```

### 2.2 DB 实证

```sql
mysql> SELECT id, channel_name, rate_limit_per_day FROM mkt_channel_config WHERE id=8;
+----+-----------------+--------------------+
| id | channel_name     | rate_limit_per_day |
+----+-----------------+--------------------+
|  8 | 5A-ToBeDeleted   |               5000 |
+----+-----------------+--------------------+

-- API 响应 null / DB 实际 5000
```

证据文件：`audit/evidence/F115-create-channel/raw-response.json`

### 2.3 代码位置（沿用 I062）

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:81`：

```java
// I062 setter 缺失 — 空语句（编译过但不设值）
// public void setRateLimitPerDay(Integer rateLimitPerDay) { ... }  ← 缺失

// DB 列定义
// rate_limit_per_day INT DEFAULT 5000 NOT NULL
```

---

## 3. 沿用 I062

- **I062** 已登记 MKT 多渠道重构第一波 Phase 3 静态差距：`rateLimitPerDay` setter 缺失（line 81 空语句）
- **I074 NEW** 是 I062 的运行时延伸：I062 仅静态（代码层），I074 增加运行时实证（API/DB 不一致）

---

## 4. 影响 Feature

| Feature ID | Module / 功能点 | 当前 Result | 修复后 Result |
|---|---|---|---|
| **F115** | MktChannel / 创建渠道 | **PARTIAL-NON-BLOCKER**（I062 + I074 契约漂移）| VERIFIED（补 setter 后，API/DB 一致） |

---

## 5. 修复建议（Post-Audit）

```java
// 在 MktChannelConfig.java 或 MarketingAutomationService.java:81 补 setter
public void setRateLimitPerDay(Integer rateLimitPerDay) {
    this.rateLimitPerDay = rateLimitPerDay != null ? rateLimitPerDay : 5000;
}
// 或在 Service createChannel() 显式默认值
if (channel.getRateLimitPerDay() == null) channel.setRateLimitPerDay(5000);
```

---

## 6. 关联文档

- `audit/evidence/F115-create-channel/raw-response.json` — API 响应 null
- `audit/runtime/report-5A-batch-1.5.md` §一 F115 + §三 I074 NEW
- `audit/runtime/plan-batch-1.5.md` §三.3.1 F115 + §五.5 N7
- `audit/issues/issue-register.md` §二十一 I062 沿用（Plan Phase 3 静态登记）