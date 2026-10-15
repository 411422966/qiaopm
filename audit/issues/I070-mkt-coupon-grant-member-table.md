# I070 — `MktCouponController.grantBatch` 用错表名 `member` 而非 `crm_member`（发券链路必失败 500）

> **发现时间**：2026-09-16 01:56 (UTC+8)
> **Phase**：5A — Runtime Verification
> **关联 Feature**：F131（MktCoupon / 批次分页+创建+发布+结束+发放）
> **严重度**：🔴 **CRITICAL**（直接阻塞券发放链路，前端点击「发放」后所有券都发不出去）
> **类别**：SQL 错误（Wrong Table Name）+ 业务链路失败
> **修复阶段**：**Post-Audit**（Phase 7）
> **修复状态**：✅ **FIXED**（2026-09-17 16:55 / commit `e7802d3` / Post-Audit 批次 2.2-A2）

---

## 1. 摘要

`MarketingAutomationService.grantBatch()` 用 SQL `SELECT id, member_name FROM member` 反查活跃会员，但项目实际**没有 `member` 表**，只有 `crm_member` 表。grant 端点一旦被调，必然抛 SQLException → Spring 500 数据库异常。

| 维度 | 值 |
|---|---|
| **触发条件** | `POST /mkt/coupon/batch/grant`（status=2 发放中批次）|
| **失败现象** | HTTP 200 / code=500 / message="数据库异常" |
| **根本原因** | `MarketingAutomationService.grantBatch()` 用 `SELECT id, member_name FROM member` 查全 active 会员 — 实际表名 `crm_member` |
| **影响范围** | F131 整条发券链路（前端「发放」按钮 100% 失败）|
| **阻断路径** | MktCoupon 发券 + 会员营销 + 券实例生成 |
| **批次元数据** | Batch 1.5（Phase 5A 实证发现） |

---

## 2. 实证证据（Phase 5A）

### 2.1 运行时实证

```
POST http://localhost:8080/mkt/coupon/batch/grant
Body: {"batchId":2,"memberIds":[]}
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 869e5be7639e4ba3b4c1aa87800fbcae
→ timestamp 2026-09-16 01:56:06
```

```
POST http://localhost:8080/mkt/coupon/batch/grant
Body: {"batchId":2,"memberIds":null}
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId af9968580ce94ed696255e72d8c9dcc9
→ timestamp 2026-09-16 01:56:06
```

证据文件：
- `audit/evidence/F131-coupon/raw-grant-retry.json`（指定 memberIds 空数组）
- `audit/evidence/F131-coupon/raw-grant-all.json`（memberIds=null 全员发放）

### 2.2 DB 实证

```sql
mysql> SELECT COUNT(*) FROM mkt_coupon;
0
-- 5A 写入 mkt_coupon_batch id=2 后，grant 链路 0 行 coupon 实例生成

mysql> SHOW TABLES LIKE 'member';
0 行 — 表不存在

mysql> SHOW TABLES LIKE 'crm_member';
1 行 — 表实际存在
```

### 2.3 代码位置

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:grantBatch()`：

```java
public Result<Integer> grantBatch(@RequestBody Map<String, Object> body) {
    Long batchId = ((Number) body.get("batchId")).longValue();
    // ...
    if (memberIds == null || memberIds.isEmpty()) {
        // 错误：表名 member 不存在
        memberIds = jdbc.queryForList(
            "SELECT id, member_name FROM member WHERE status = 1 AND deleted = 0",
            Long.class
        );
    }
    // ... 后续 INSERT mkt_coupon 永远走不到（前置 SQL 抛异常）
}
```

---

## 3. 影响 Feature

| Feature ID | Module / 功能点 | 当前 Result | 修复后 Result |
|---|---|---|---|
| **F131** | MktCoupon / 批次发放 | **PARTIAL-BLOCKER**（grant 失败 500，mkt_coupon 0 行） | VERIFIED（修复 `member` → `crm_member` 后） |

---

## 4. 同类风险

- **历史沿用**：`mkt_order_discount` / `member_message` / `crm_member` 表名混淆 — 前四批次 EXT-* 已多次登记
- **建议统一**：所有发券 / 会员积分 / 会员营销 SQL 应统一从 `crm_member`（会员主表）查；如用别名需明确 `crm_member m` 别名

---

## 5. 修复建议（Post-Audit）

```java
// 修复 1 行：
memberIds = jdbc.queryForList(
    "SELECT id FROM crm_member WHERE status = 1 AND deleted = 0",
    Long.class
);
```

---

## 6. 关联文档

- `audit/evidence/F131-coupon/raw-grant-retry.json` — 500 数据库异常实证
- `audit/evidence/F131-coupon/raw-grant-all.json` — 全员发放 500 实证
- `audit/evidence/F131-coupon/runtime-evidence.md` — F131 Phase 5A 验证报告
- `audit/runtime/report-5A-batch-1.5.md` §一 F131 + §三 I070 NEW
- `audit/runtime/plan-batch-1.5.md` §三.3.1 F131

---

## 7. Post-Audit 修复实录（批次 2.2-A2）

### 7.1 修复动作摘要

| 维度 | 内容 |
|---|---|
| **commit** | `e7802d3` fix(mkt): I070 grantBatch table member→crm_member + column name fix |
| **修改文件** | `backend/src/main/java/com/meiyetec/controller/MktCouponController.java` |
| **diff 规模** | +2 / -2（2 行，2 处独立改动）|
| **mvn compile** | ✅ BUILD SUCCESS（exit code 0）|
| **运行时验证** | ✅ 已通过 curl + DB 双重验证 |

### 7.2 两处改动（一次性修完）

| 行号 | 修复前 | 修复后 | 原因 |
|---|---|---|---|
| `MktCouponController.java:161` | `FROM member` | `FROM crm_member` | I070 核心：member 表不存在 |
| `MktCouponController.java:161` | `SELECT id, member_name` | `SELECT id, name` | `crm_member` 表实际列名是 `name`（非 `member_name`）|
| `MktCouponController.java:175` | `m.get("member_name")` | `m.get("name")` | 同步列名引用 |

> ⚠️ 如果只改表名不改列名，仍会因 `Unknown column 'member_name'` 报 500。文档 §5 修复建议已正确去掉 `member_name`。

### 7.3 运行时验证证据

```bash
# POST /mkt/coupon/batch/1/grant（全员发放）
$ curl -X POST http://localhost:8080/mkt/coupon/batch/1/grant \
    -H "Authorization: Bearer <JWT>" \
    -H "Content-Type: application/json" \
    -d '{"batchId":1,"memberIds":[]}'

→ HTTP 200
→ {"code":200,"message":"已发放 100 张",
    "data":{"grantedMembers":489,"issuedCount":100}}
```

```sql
-- DB 验证
SELECT id, batch_id, member_id, member_name, coupon_type, status, granted_date
FROM mkt_coupon WHERE tenant_id=1 ORDER BY id DESC LIMIT 5;
-- id=100..96, batch_id=1, member_name 有值（不再 null）,
-- status=1（已发放）, granted_date=2026-09-17

SELECT id, total_count, issued_count FROM mkt_coupon_batch WHERE id=1;
-- id=1, total_count=100, issued_count=100（0→100）
```

### 7.4 一修一验清单

- [x] mvn compile 通过（exit code 0）
- [x] git commit 已提交（`e7802d3` 独立 commit）
- [x] 运行时 curl 验证：HTTP 200，`message=已发放 100 张`
- [x] DB 双重验证：mkt_coupon 新增 100 条，issued_count 0→100
- [x] I070 状态更新：OPEN → ✅ FIXED
- [x] A2 报告已落档（`audit/post-audit/phase-2.2-A2-report.md`）

---

## 8. 下一步（批次 2.2-A3）

按顺序：**Phase 2.2-A3 = I071**（markRead Fake Success / MktConversionController）

---

**I070 修复完成（Post-Audit 批次 2.2-A2）。**