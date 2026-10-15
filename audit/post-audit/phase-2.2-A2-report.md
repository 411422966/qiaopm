# Phase 2.2-A2 Report — I070 grantBatch 表名 + 列名修复

> **批次**：Post-Audit Phase 2.2 → 2.2-A2
> **修复 Issue**：**I070**（CRITICAL — grantBatch 查 `member` 表不存在导致 500）
> **commit**：`e7802d3`
> **修复时间**：2026-09-17 16:55 (UTC+8)
> **工作量**：约 15 分钟（一修一验一 commit）
> **关联 Feature**：F131（MktCoupon 券发放）

---

## 0. TL;DR

| 维度 | 结果 |
|---|---|
| **修复动作** | 表名 `member` → `crm_member` + 列名 `member_name` → `name`（两处）|
| **静态验证** | ✅ mvn compile SUCCESS（exit code 0）|
| **动态验证** | ✅ curl + DB 双重验证通过 |
| **git commit** | ✅ `e7802d3`（独立 commit）|
| **I070 状态** | OPEN → ✅ FIXED |
| **受影响 Feature** | F131（券发放链路闭环）|

---

## 1. 修复前后对比

### 修复前（I070 原始 bug）

```java
// MktCouponController.java:161
StringBuilder memberSql = new StringBuilder(
    "SELECT id, member_name FROM member " +   // ← member 表不存在
    "WHERE tenant_id = ? AND deleted = 0 AND status = 1 ");
...
// MktCouponController.java:175
String memberName = m.get("member_name") == null ? null  // ← crm_member 表无此列
    : m.get("member_name").toString();
```

### 修复后（commit `e7802d3`）

```java
// MktCouponController.java:161
StringBuilder memberSql = new StringBuilder(
    "SELECT id, name FROM crm_member " +       // ← 表名 + 列名双修
    "WHERE tenant_id = ? AND deleted = 0 AND status = 1 ");
...
// MktCouponController.java:175
String memberName = m.get("name") == null ? null         // ← 列名同步
    : m.get("name").toString();
```

**变更规模**：1 文件，+2 行 -2 行。

---

## 2. 为什么两处改动必须同时

| 改动 | 单独修的后果 |
|---|---|
| 只改表名 `FROM member → crm_member` | `SELECT member_name` 在 `crm_member` 表中不存在 → `Unknown column 'member_name'` → HTTP 500 |
| 只改列名 `member_name → name`（表名不动）| `FROM member` 在 DB 中不存在 → `Table 'meiyetec.member' doesn't exist` → HTTP 500 |
| **两处同时改** | ✅ `crm_member` 表存在 + `name` 列存在 → 正常发放 |

---

## 3. DB 字段兼容性确认

`crm_member` 表（`init_m03_member.sql`）：

| 字段 | 类型 | 注释 | 状态 |
|---|---|---|---|
| `id` | BIGINT PK | 会员ID | ✅ 匹配 |
| `name` | VARCHAR(50) | 姓名 | ✅ 匹配（对应原 `member_name`）|
| `tenant_id` | BIGINT | 租户ID | ✅ 匹配 |
| `status` | TINYINT | 状态（1=正常）| ✅ 匹配 |
| `deleted` | TINYINT | 逻辑删除 | ✅ 匹配 |

SQL 过滤条件 `WHERE tenant_id = ? AND deleted = 0 AND status = 1` 全部与 `crm_member` 表兼容。

---

## 4. 运行时验证证据

### 4.1 curl 测试（JWT Bearer Token）

```bash
# 1. 登录获取 JWT
$ curl -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    --data-binary "@login_body.json"
→ HTTP 200 + JWT token

# 2. 全员发放（memberIds=[] → 查全部 active 会员）
$ curl -X POST http://localhost:8080/mkt/coupon/batch/1/grant \
    -H "Authorization: Bearer eyJhbGci..." \
    -H "Content-Type: application/json" \
    -d '{"batchId":1,"memberIds":[]}'

→ HTTP 200
→ {"code":200,"message":"已发放 100 张",
    "data":{"grantedMembers":489,"issuedCount":100},
    "traceId":"3c9d5e3496ba465eb6b96065be531fb9",
    "timestamp":"2026-09-17 16:54:58"}
```

### 4.2 DB 双重验证

```sql
-- mkt_coupon 新增记录（修复前：mkt_coupon 0 行；修复后：100 行）
SELECT id, batch_id, member_id, member_name, coupon_type, status, granted_date
FROM mkt_coupon WHERE tenant_id=1 ORDER BY id DESC LIMIT 5;
-- id=100, batch_id=1, member_id=724, member_name=李雅琳, DISCOUNT, status=1, 2026-09-17
-- id=99,  batch_id=1, member_id=723, ...
-- ...（共 100 条新增记录）

-- mkt_coupon_batch 批次 issued_count 更新
SELECT id, coupon_name, total_count, issued_count FROM mkt_coupon_batch WHERE id=1;
-- id=1, audit-test-coupon-batch, total_count=100, issued_count=100
-- 修复前：issued_count=0；修复后：issued_count=100 ✅
```

### 4.3 与 Phase 5A Baseline 对比

| 指标 | Phase 5A（I070 登记前）| Phase 2.2-A2（修复后）| 状态 |
|---|---|---|---|
| HTTP 响应 | 200（但 code=500 错误）| **200（code=200）** | ✅ |
| mkt_coupon 新增行 | 0 | **100** | ✅ |
| issued_count 更新 | 不变（0）| **0→100** | ✅ |
| `member_name` 有值 | ❌（SQL 失败）| **✅ 有值** | ✅ |

---

## 5. 受保护页面零影响确认

| 受保护页面 | 与本次改动交集 | 状态 |
|---|---|---|
| `/dashboard` | 0（mkt_coupon 与 dashboard 无直接调用链路）| ✅ 零影响 |
| `/mh/visual` | 0 | ✅ 零影响 |
| `/mh/consent` | 0 | ✅ 零影响 |

---

## 6. 与 final-acceptance.md §8 P0-2 一致性

| 维度 | final-acceptance.md §8 P0-2 | 实际 |
|---|---|---|
| Issue | I070 | ✅ 一致 |
| 涉及文件 | MktCouponController | ✅ 一致 |
| 改动量 | 1 行代码（grantBatch SQL）| **2 行（表名 + 列名）** |
| 影响 Feature | F131 券发放 | ✅ 一致 |
| 修复时长 | 30 分钟 | 15 分钟 |

---

## 7. 一修一验清单

| # | 项 | 状态 | 备注 |
|---|---|---|---|
| 1 | mvn compile | ✅ | exit code 0 |
| 2 | git commit | ✅ | `e7802d3` 独立 commit |
| 3 | curl 运行时验证 | ✅ | HTTP 200, code=200, 已发放 100 张 |
| 4 | DB 双重验证 | ✅ | mkt_coupon 100 行新增 + issued_count 0→100 |
| 5 | 受保护页面零影响 | ✅ | — |
| 6 | Issue 状态更新 | ✅ | I070 OPEN → FIXED |
| 7 | 文档化（issue 文件 + report）| ✅ | 188 行 issue + 本报告 |

---

## 8. 下一步（Phase 2.2-A3）

按确认顺序，进入 **Phase 2.2-A3 = I071**（markRead Fake Success）：

| 维度 | 内容 |
|---|---|
| 文件 | `MktConversionController.java` |
| Issue | I071 CRITICAL |
| 影响 | F123 标记已读链路假成功 |
| diff 估算 | 1-3 行结果校验 |
| 预估耗时 | 30 分钟 |
| 状态 | ⏸ 等待用户确认进入 A3 |

---

## 9. 报告签署

| 字段 | 值 |
|---|---|
| 报告版本 | v1.0（首发）|
| 创建时间 | 2026-09-17 17:00 (UTC+8) |
| 批次 | Phase 2.2-A2 |
| commit | `e7802d3` |
| 状态 | ✅ 修复 + 验证 + 文档完成 |
| A1 验证补充 | ✅ 已补 A1 curl 验证（HTTP 200，JwtInterceptor bypass 生效）|
| 不动文件 | ✅ `/dashboard` / `/mh/visual` / `/mh/consent` |

---

*Phase 2.2-A2 完成 — I070 关闭，进入 A3 确认环节。*
