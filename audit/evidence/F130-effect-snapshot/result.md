# F130 — MktEffect / 效果快照+生成快照 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:51 (UTC+8)
> **方式**：API + DB + 静态（N8 I059 dead code 实证）

## 1. 接口实证

### GET /mkt/effect/snapshot

```
→ HTTP 200 / code 200 / message: "操作成功"
→ traceId ...
```

### POST /mkt/effect/snapshot/save

```
Body: {"snapshotDate":"2026-09-15","totalRevenue":1000,"totalCost":500,"totalReach":200,"totalConvert":50}
→ HTTP 200 / code 200 / message: "快照已记录"
```

## 2. N8 I059 实证 — dead code（仅 log 不写表）

**源码位置**：`MktEffectController.java:175-181`

```java
@PostMapping("/snapshot/save")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> saveSnapshot() {
    // 快照仅为当前 snapshot 的计算结果，print 到日志，实际未写 mkt_effect_snapshot 表
    Map<String, Object> snap = snapshot(null, null).getData();
    return Result.success("快照已记录", snap);
}
```

**关键证据**：
- `@RequestBody` **未声明**（方法签名零参数）→ body 参数被忽略
- 方法内部仅调用 `snapshot(null, null)` 拿数据 + log.info
- **无 INSERT INTO mkt_effect_snapshot**

## 3. mkt_effect_snapshot 表实证

```
mysql> SHOW TABLES LIKE 'mkt_effect_snapshot';
0 行 — 表 MISSING
```

## 4. Final Result

**PARTIAL-NON-BLOCKER** — **N8 I059 dead code 实证**：
- snapshot 读端点 OK
- snapshotSave 仅 log 不写表（I059 PARTIAL-NON-BLOCKER）
- mkt_effect_snapshot 表本身 MISSING（**I054 NEW 已记录**）
