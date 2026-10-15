# F046 — Plugin Marketplace 安装生命周期（×3 端点 实证）

> **验证时间**：2026-09-13 20:44 (UTC+8)
> **Phase**：5B — API + DB 实证
> **验证方式**：install → uninstall → listInstall 完整链 + DB 验
> **最终状态**：**PARTIAL-NON-BLOCKER → 保持 PARTIAL-NON-BLOCKER**（CRUD 正确 + I023 幽灵 NPE + I009 @Transactional 无 rollbackFor）

---

## 一、3 端点实证

### 1.1 实证结果（来自 `probe-5b-f045-7.ps1`，步骤 5/6/7）

| Step | 端点 | 响应 | DB 行 |
|---|---|---|---|
| 5 | `POST /plugin/install` (pluginId=20, clerk01 tenant=1) | **200** `{id:12, tenantId:1, pluginId:20, pluginCode:probe-mkt-5b, version:"1.0.0", enabled:1, installStatus:"ACTIVE"}` | INSERT plugin_install (12, 1, 20, "probe-mkt-5b", "1.0.0", ACTIVE, 1) |
| 6 | `POST /plugin/install/12/uninstall` (admin) | **200** `{message:"已卸载"}` | UPDATE plugin_install SET installStatus="REMOVED", enabled=0 WHERE id=12 |
| 7 | `GET /plugin/install/list` (clerk01) | **200** `[{id:12, tenantId:1, installStatus:"REMOVED", enabled:0, ...}]` | SELECT WHERE tenant_id=1（clerk01 tenant=1）|

### 1.2 关键发现

1. **installCount 累加**：
   - `PluginService.install` 第 90-92 行：`plugin.setInstallCount(cur + 1); marketMapper.updateById(plugin);`
   - 但 uninstall **未**做 installCount 减一 → **计数单调递增**（运维侧不一致）

2. **uninstall 是软删除**：
   - `installStatus="REMOVED"` + `enabled=0`
   - **不删除行** → 历史记录可查
   - **bug 风险**：相同 tenant + pluginCode 可再次 install → 出现多个 ACTIVE/REMOVED 行

3. **install 自动写 tenantId**：
   - `install` 从 `TenantContextHolder.get().getTenantId()` 取当前 tenant
   - clerk01 tenant=1 → 写出 tenant_id=1（正确）

## 二、I023 幽灵 NPE 风险 实证

### 2.1 场景

按 I023 SoT 描述：
> `POST /plugin/install` 只插 `plugin_install` 表，**不触发 runtime load** → `isEnabledForTenant` 返回 `STARTED && true` → 业务侧可能调到一个不存在的插件入口（NPE）

### 2.2 当前环境实测

按 F038 静态判定 + F039 实测 `effectiveEnabled=false`：
- **当前 pf4j 0 插件** → `isEnabledForTenant` 第一关就拒 → 返回 false
- **业务侧拿 false** → 不调插件入口 → 不会触发 NPE
- **I023 风险降级**：service 层正确阻断，业务侧需自律（详细见 F038 evidence）

### 2.3 静态风险（pf4j 加载后）

如果未来 pf4j 加载真实插件 + install 后未 load：
- `isEnabledForTenant` 拿 pf4j state=STARTED → 进入后续判断
- `install` 记录存在 + enabled=1 → 返回 true
- 业务侧调插件入口 → **可能 NPE**（如果 wrapper.getPlugin() 返回 null 或 method 找不到）

**I023 风险等级**：**降低但未消除**。

## 三、I009 @Transactional 无 rollbackFor 实证

`PluginService.install`（行 75-95）：
```java
@PostMapping("/install")
@Transactional  // 默认 rollbackFor = RuntimeException
public Result<PluginInstall> install(@RequestBody Map<String, Object> body) {
    ...
    installMapper.insert(i);
    int cur = plugin.getInstallCount() == null ? 0 : plugin.getInstallCount();
    plugin.setInstallCount(cur + 1);
    marketMapper.updateById(plugin);  // 如果这一步抛异常？
    return Result.success("已安装", i);
}
```

**问题**：
- `marketMapper.updateById` 抛出 RuntimeException（如 SQL 错误）→ 默认 rollback
- 但若抛 Error 或 checked exception → **不 rollback**（I009 同模式）
- 建议加 `rollbackFor = Exception.class`

**当前实测**：未触发异常场景，未观察到问题。

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| install CRUD | ✅ VERIFIED | INSERT plugin_install + installCount +1 |
| uninstall 软删除 | ✅ VERIFIED | UPDATE installStatus=REMOVED + enabled=0 |
| listInstall tenant 隔离 | ✅ VERIFIED | clerk01 仅看到 tenant=1 行 |
| installCount 累加 | ✅ VERIFIED | 但 uninstall 未减一 |
| I023 幽灵 NPE | ⚠️ 静态识别 | 当前环境不可触发（pf4j 0 插件），未来风险待 pf4j 加载后验证 |
| I009 @Transactional 无 rollbackFor | ✅ 静态识别 | 默认 RuntimeException rollback |
| I019 命名异味 | ✅ 静态识别 | PluginService 直接是 @RestController |

### 最终状态

**F046 = PARTIAL-NON-BLOCKER**（CRUD 正确 + I023/I009/I019 静态识别）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-f045-7.ps1` |
| Service | `backend/src/main/java/com/meiyetec/service/PluginService.java:75-110` |

---

**F046 CRUD 正确。I023 在当前环境不可触发；I009/I019 静态识别。**
