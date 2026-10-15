# F114 — MktChannel / 渠道列表查询（Phase 5A 验证）

> **状态**：✅ VERIFIED  
> **验证时间**：2026-09-16 02:01 (UTC+8)  
> **验证方式**：API + DB  
> **Issue 关联**：I054 NEW 已反向（表存在 — I066）

---

## 1. API 调用

### 1.1 admin 调用

```
GET http://localhost:8080/mkt/channel/list
Authorization: Bearer <admin-token>
```

- **HTTP 状态**：200
- **traceId**：`9eda86592cde403fa631a7d605b8b1a7`
- **data 长度**：5 条

| 字段 | id=1 | id=2 | id=3 | id=4 | id=8 |
|---|---|---|---|---|---|
| channelType | SMS | MINI_PROGRAM | MP | IN_APP | SMS |
| channelName | 阿里云短信 | 小程序订阅消息 | 公众号模板消息 | 站内信 | 5A-SMS-Updated-V2 |
| tenantId | 1 | 1 | 1 | 1 | 1 |
| rateLimitPerMin | 30 | 100 | 50 | 1000 | 60 |
| rateLimitPerDay | 2000 | 5000 | 3000 | 99999 | 5000 |
| status | 1 | 1 | 1 | 1 | 1 |
| lastHealthAt | 2026-09-16 01:48:48 | 2026-09-16 01:48:48 | NULL | 2026-09-16 01:48:48 | NULL |

证据：`raw-list-admin.txt`

### 1.2 clerk01 调用

```
GET http://localhost:8080/mkt/channel/list
Authorization: Bearer <clerk01-token>
```

- **HTTP 状态**：200
- **traceId**：`7ae309da31c74f65baa5fd9ad4d1cf90`
- **data 长度**：5 条（与 admin 完全相同 — 因 admin + clerk01 均为 tenantId=1）

证据：`raw-list-clerk.txt`

---

## 2. DB 断言（前后 diff）

### 2.1 DB 行数

- **DB 总行数**：`SELECT COUNT(*) FROM mkt_channel_config` → **8**（含 3 条 deleted=1）
- **API 返回行数**：5
- **差值**：3 条（deleted=1 的 id=5,6,7）

### 2.2 完整 mkt_channel_config 全表

| id | tenant_id | channel_type | channel_name | deleted |
|---|---|---|---|---|
| 1 | 1 | SMS | 阿里云短信 | 0 |
| 2 | 1 | MINI_PROGRAM | 小程序订阅消息 | 0 |
| 3 | 1 | MP | 公众号模板消息 | 0 |
| 4 | 1 | IN_APP | 站内信 | 0 |
| 5 | 1 | IN_APP | audit-test-rate-limit-per-day-updated | 1 |
| 6 | 1 | SMS | 5A-SMS-Updated | 1 |
| 7 | 1 | EMAIL | 5A-ToBeDeleted | 1 |
| 8 | 1 | SMS | 5A-SMS-Updated-V2 | 0 |

证据：`db-list-before.txt`

---

## 3. 列对齐（DESCRIBE 验证）

`mkt_channel_config` 22 列：

```
id / tenant_id / brand_id / channel_type / channel_name / provider /
signature / template_prefix / rate_limit_per_min / rate_limit_per_day /
status / last_health_at / last_health_msg / retry_max / retry_interval_sec /
operator_id / operator_name / create_time / update_time / deleted
```

✅ API 返回 15 列（id/tenantId/brandId/channelType/channelName/provider/signature/templatePrefix/rateLimitPerMin/rateLimitPerDay/status/lastHealthAt/lastHealthMsg/retryMax/retryIntervalSec），与 `MarketingAutomationService.channelList()` 第 51-55 行的 SQL `SELECT` 子句一致。

---

## 4. 静态分析（源码：MarketingAutomationService.java:50-65）

```java
public Result<List<Map<String, Object>>> channelList() {
    Long tenantId = TenantContextHolder.get().getTenantId();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, tenant_id, brand_id, channel_type, channel_name, provider, " +
        "       signature, template_prefix, rate_limit_per_min, rate_limit_per_day, " +
        "       status, last_health_at, last_health_msg, retry_max, retry_interval_sec " +
        "FROM mkt_channel_config WHERE deleted = 0 AND tenant_id = ? " +
        "ORDER BY channel_type ASC",
        tenantId);
    return Result.success(Keys.camel(rows));
}
```

✅ 强制 `tenantId` 过滤 ✅ `deleted=0` 软删过滤 ✅ 驼峰转换（`Keys.camel`）✅

---

## 5. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| HTTP 状态码 | ✅ | 200 |
| 响应体结构 | ✅ | `{code:200, message:"操作成功", data:[...]}` |
| traceId 输出 | ✅ | 每次唯一 traceId |
| tenantId 过滤 | ✅ | `WHERE tenant_id=?` 强制 |
| deleted 过滤 | ✅ | `WHERE deleted=0` 强制 |
| 驼峰字段名 | ✅ | `rateLimitPerMin` 等 |
| 列数对齐 | ✅ | 15 列与 SQL 子句一致 |
| 行数对齐 | ✅ | API=5 vs DB(deleted=0, tenant=1)=5 |

**F114 — VERIFIED** ✅

---

## 6. 已知负向（沿用 + 不在 Phase 5 修复）

- **I054 NEW**：原登记 "mkt_channel_config 缺失"，**已被 I066 反向**（表实际存在）
- **I050**：ChannelDispatcher.healthCheckAll 硬编码 `tenantId=1L`（沿用，与 F118 关联）

---

证据清单：
- `raw-list-admin.txt` — admin 列表原始响应
- `raw-list-clerk.txt` — clerk01 列表原始响应
- `db-list-before.sql` — DB 行数 + 列值查询 SQL
- `db-list-before.txt` — DB 行数 + 列值查询输出
