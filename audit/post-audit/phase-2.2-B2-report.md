# B2 — I073 `journeyPage` COUNT args 修复报告

> **修复时间**：2026-09-19 23:41 (UTC+8)
> **修复人**：Cursor Agent
> **关联 Issue**：I073
> **关联 Feature**：F125 旅程分页（POST + GET 双形式）
> **Commit**：`5cb2c01`
> **状态**：OPEN → **FIXED** ✅

---

## 1. 根因（实测）

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:468-499` 旧实现：

```java
StringBuilder sql = ...FROM mkt_journey WHERE deleted=0 AND tenant_id=?
List<Object> args = new ArrayList<>();
args.add(tenantId);                 // [tenantId]
if (status != null) { args.add(status); }             // [tenantId, status]
if (journeyType != null) { args.add(journeyType); }    // [..., journeyType]
if (keyword != null) { args.add(kw, kw); }            // [..., kw, kw]
sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
args.add(pageSize); args.add(offset);                  // [..., pageSize, offset]
jdbc.queryForList(sql, args.toArray());                // 7 个 params ✅

StringBuilder csql = "SELECT COUNT(*) FROM mkt_journey WHERE deleted=0 AND tenant_id=?"
jdbc.queryForObject(csql, Long.class, args.toArray()); // 7 个 params vs 1 个 ? → SQLException
```

- 原 SQL（未拆分）：`csql = "SELECT COUNT(*) FROM mkt_journey WHERE deleted=0 AND tenant_id=?"` — 只有 1 个 `?`
- 但传 5~7 个参数 → `PreparedStatement` 报错 → 500

---

## 2. 改动（最小化但 total 与 records 对齐）

```diff
- // 共享 args：条件 + 分页
+ // 拆分：cond 复用 SELECT 和 COUNT；pageArgs 只给 SELECT
+ StringBuilder cond = new StringBuilder(" WHERE deleted=0 AND tenant_id=? ");
+ List<Object> condArgs = new ArrayList<>();
+ condArgs.add(tenantId);
+ if (status != null) { cond.append("AND status=? "); condArgs.add(status); }
+ if (journeyType != null && !journeyType.isEmpty()) { cond.append("AND journey_type=? "); condArgs.add(journeyType); }
+ if (keyword != null && !keyword.isEmpty()) {
+     cond.append("AND (journey_name LIKE ? OR journey_code LIKE ?) ");
+     condArgs.add("%" + keyword + "%"); condArgs.add("%" + keyword + "%");
+ }
+
+ String sql = "SELECT ..." + cond + "ORDER BY id DESC LIMIT ? OFFSET ?";
+ List<Object> pageArgs = new ArrayList<>(condArgs);
+ pageArgs.add(pageSize); pageArgs.add((pageNum-1) * pageSize);
+ jdbc.queryForList(sql, pageArgs.toArray());   // ✅ pageArgs 数 = 占位数
+
- String csql = "...WHERE deleted=0 AND tenant_id=?";
- jdbc.queryForObject(csql, Long.class, args.toArray());
+ String csql = "SELECT COUNT(*) FROM mkt_journey" + cond;   // 复用 cond
+ jdbc.queryForObject(csql, Long.class, condArgs.toArray()); // ✅ condArgs 数 = 占位数
```

**关键改进**：COUNT 现在**也**带上 `status/journeyType/keyword` 条件 → total 与 records 语义一致（原版 total 是该 tenant 全部，**bug 改进**）。

文件：`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java` — `+20 / -16` 行

---

## 3. 编译

```
mvn -B -DskipTests compile
[INFO] BUILD SUCCESS
[INFO] Total time:  40.184 s
```

---

## 4. 状态变更

| Issue | 之前 | 之后 |
|---|---|---|
| **I073** | OPEN（Post-Audit P0-7）| **FIXED** ✅ |
