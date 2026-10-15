# F007 — Dashboard（BROKEN inherited from I002 + I008 联动）

> **Phase**：5B Runtime Verification
> **Feature ID**：F007
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **继承策略**：I002 强依赖 + I008 联动 → 引用 5A F002 证据 + 5B F019

---

## 1. 继承结论

**F007 = BROKEN (inherited from I002 + I008 联动)**

| 维度 | 状态 | 证据来源 |
|---|---|---|
| endpoint 可用性 | ❌ I002 阻塞 selectCount → 抛错 | 5B F019 §2 |
| catch 吞异常 | ❌ I008 触发 Fake Success 链 | `audit/issues/I008-dashboard-catch-swallow.md` §7 |
| Shadow Feature | ❌ I006（前端无 dashboard tab）| `audit/issues/I006-crm-dashboard-shadow.md` |
| 5B 重新跑 | ⏸ 跳过（与 5A 一致根因）| 继承策略 |

## 2. 关键负向证据（5B 强化）

### 2.1 I002 + I008 Fake Success 链

```java
// CrmIntegrationService.java:142-155
@GetMapping("/dashboard")
public Result<Map<String, Object>> dashboard() {
    Long tenantId = TenantContextHolder.get().getTenantId();
    Map<String, Object> data = new LinkedHashMap<>();
    try {
        data.put("totalIntegrations", integrationMapper.selectCount(...));  // ← I002 抛错
        data.put("totalSyncLogs", syncLogMapper.selectCount(...));          // ← I002 抛错
    } catch (Exception ignore) {}                                            // ← I008 catch 吞
    return Result.success(data);                                             // ← 返回 success
}
```

### 2.2 I008 CRITICAL（5B 升级）

| 维度 | 等级 |
|---|---|
| 严重度 | 🔴 **CRITICAL**（Phase 5A 实证 — F010 tick 7 次静默失败）|
| 触发频次 | 每 5 分钟（自动 tick）+ 每次手动调用 |
| 告警 | 无 |
| 修复优先级 | P0 |

完整证据：`audit/issues/I008-dashboard-catch-swallow.md` §7 + `audit/evidence/F010-auto-tick-scan/log-crm-scheduler-all.txt`

## 3. I006 Shadow Feature

前端 `views/crm-integration/index.vue` 无 dashboard tab → API 已封装 `crmIntegrationApi.dashboard()` 但 UI 无消费方

完整：`audit/issues/I006-crm-dashboard-shadow.md`

## 4. 5B E2E 降级

F007 E2E 脚本已生成 → `audit/scripts/e2e/F007-no-dashboard-tab.spec.ts`

用户手动跑：
```bash
cd frontend/meiyetec-web
npx playwright test ../audit/scripts/e2e/F007-no-dashboard-tab.spec.ts
```

结果贴回 `audit/evidence/F007-dashboard/e2e-result.md`

## 5. ✅ 最终状态

**❌ BROKEN (inherited from I002 + I008) + ⚠ SHADOW (I006)**

- I002 阻塞后端数据获取
- I008 catch 吞异常 → Fake Success
- I006 前端无 tab → 即便后端修好也无人消费

修复路径：
1. 跑 `migrate_ext_15_crm_real.sql` 补 9 列
2. I008 catch 收紧（建议方案 ② 全局异常处理兜底）
3. 前端加 dashboard tab
