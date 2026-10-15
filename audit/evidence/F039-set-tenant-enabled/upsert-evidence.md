# F039 — Plugin `setTenantEnabled` 启停（API + DB 实证）

> **验证时间**：2026-09-13 20:39 (UTC+8)
> **Phase**：5B — API + DB 实证
> **验证方式**：调 `/plugin/runtime/tenant/{tid}/{code}/enable|disable` + 查 DB
> **最终状态**：**PARTIAL-NON-BLOCKER**（upsert 行为正确 + `effectiveEnabled=false` UI 困惑）

---

## 一、实证结果

### 1.1 测试用例（来自 `probe-5b-f039.ps1`）

| Step | 操作 | API 响应 | DB 行 (id, tid, code, enabled, install_status) |
|---|---|---|---|
| 1 | 初始状态 | — | (无) |
| 2 | enable #1 (tid=1, code=test-probe-5b) | 200, tenantEnabled=true, effectiveEnabled=false | (10, 1, test-probe-5b, 1, ACTIVE) |
| 3 | enable #2（重复）| 200, tenantEnabled=true, effectiveEnabled=false | (10, 1, test-probe-5b, 1, ACTIVE) **id 不变** |
| 4 | enable #3（重复）| 200, tenantEnabled=true, effectiveEnabled=false | (10, 1, test-probe-5b, 1, ACTIVE) **id 不变** |
| 5 | disable #1 | 200, tenantEnabled=false, effectiveEnabled=false | (10, 1, test-probe-5b, 0, DISABLED) |
| 6 | disable #2（重复）| 200, tenantEnabled=false, effectiveEnabled=false | (10, 1, test-probe-5b, 0, DISABLED) **id 不变** |
| 7 | enable tenant=2 | 200, tenantEnabled=true, effectiveEnabled=false | (11, 2, test-probe-5b, 1, ACTIVE) **新 id，多 tenant 隔离** |

### 1.2 实证结论

- ✅ **upsert by (pluginCode, tenantId) 正确**：3 次 enable 重复调用，DB 仅 1 行，id 不变
- ✅ **多 tenant 隔离**：tenant=1 / tenant=2 各自独立行（id=10/11）
- ✅ **enable/disable 切换**：enabled 字段在 0/1 间切换，install_status 在 DISABLED/ACTIVE 间切换
- ⚠️ **`effectiveEnabled=false` 即使 `tenantEnabled=true`**：因为 `effectiveEnabled` 委派 `isEnabledForTenant` → pf4j 0 插件阻断 → 永远 false

## 二、源码定位

`PluginTenantService.setTenantEnabled`：`plugin/PluginTenantService.java:69-95`

```java
public boolean setTenantEnabled(String pluginCode, Long tenantId, boolean enabled) {
    if (pluginCode == null || tenantId == null) return false;
    try {
        PluginInstall existing = installMapper.selectOne(...);  // 查 (code, tid)
        if (existing == null) {
            PluginInstall ins = new PluginInstall();
            ins.setTenantId(tenantId);
            ins.setPluginCode(pluginCode);
            ins.setVersion("latest");  // ⚠️ 默认 version="latest"
            ins.setEnabled(enabled ? 1 : 0);
            ins.setInstallStatus(enabled ? "ACTIVE" : "DISABLED");
            ins.setUseCount(0L);
            installMapper.insert(ins);  // INSERT
            return true;
        }
        existing.setEnabled(enabled ? 1 : 0);
        existing.setInstallStatus(...);
        installMapper.updateById(existing);  // UPDATE by id
        return true;
    } catch (Exception e) { return false; }
}
```

## 三、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| upsert 行为正确 | ✅ VERIFIED | 重复 enable 不重复行 |
| 多 tenant 隔离 | ✅ VERIFIED | 不同 tenantId 独立行 |
| enable/disable 状态切换 | ✅ VERIFIED | enabled 0↔1 切换正确 |
| version 默认值 | ⚠️ PARTIAL | "latest" 是占位，可能与真实插件版本不一致（影响 PluginTenantService 查询 LIMIT 1）|
| effectiveEnabled UI 困惑 | ⚠️ PARTIAL | 当前 pf4j 0 插件 → 永远 false，UI 应区分 "已开启但插件未加载" 状态 |

### 最终状态

**F039 = PARTIAL-NON-BLOCKER**（upsert 行为完全正确，effectiveEnabled UI 体验问题）

---

## 四、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-f039.ps1` |
| 原始响应 | `audit/runtime/probe-5b-f039.json` |
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginTenantService.java:69-95` |

---

**F039 upsert 行为已实证。effectiveEnabled UI 问题不在本次审计范围。**
