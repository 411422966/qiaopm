# F011 — shouldRun 窗口判断（BROKEN inherited from I002 via 5A F008）

> **Phase**：5B Runtime Verification
> **Feature ID**：F011
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **继承策略**：I002 强依赖 → 引用 5A F008 证据

---

## 1. 继承结论

**F011 = BROKEN (inherited from I002 via 5A F008)**

| 维度 | 状态 | 证据来源 |
|---|---|---|
| shouldRun 静态可达 | ✅ CrmSyncScheduler.java:72-79 | L0 静态 |
| shouldRun 运行时可达 | ❌ I002 阻塞 selectById（syncNow 前置）| 5A F008 |
| windowMinutes 读取 | ❌ I002 阻塞（sync_window_minutes 列缺）| 5B F019 |
| 跳过场景返回 skipped=true | ❌ 无法验证 | 5A F008 |

## 2. shouldRun 实现 L0

```java
// CrmSyncScheduler.java:72-79
public boolean shouldRun(CrmIntegration cfg) {
    Integer window = cfg.getSyncWindowMinutes() == null ? 30 : cfg.getSyncWindowMinutes();
    LocalDateTime last = cfg.getLastIncrementalSyncAt();
    if (last == null) return true;
    return last.plusMinutes(window).isBefore(LocalDateTime.now());
}
```

✅ 代码逻辑正确（last=null → 立即跑；否则判断窗口）

## 3. 5A 引用证据

| 文件 | 关键内容 |
|---|---|
| `audit/evidence/F008-sync-skip-window/result.md` | F008 syncNow 抛 I002 → 无法验证 skipped=true |
| `audit/evidence/F005-manual-sync/result.md` | syncNow:88 selectById 抛 I002 → runIncremental 不被调用 |
| `audit/evidence/F019-crm-integration-entity/result.md` | DB 缺 9 列（含 lastIncrementalSyncAt / syncWindowMinutes）|

## 4. ✅ 最终状态

**❌ BROKEN (inherited from I002 via 5A F008)**

修复路径：跑 `migrate_ext_15_crm_real.sql` 补 9 列 → 5C 重测
