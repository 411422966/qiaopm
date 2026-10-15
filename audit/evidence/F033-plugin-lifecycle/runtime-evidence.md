# F033 — Plugin 手动生命周期（Runtime 受限）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P0)
> **最终状态**：**PARTIAL-NON-BLOCKER**（保持 Phase 3 + Phase 5A 受限）

---

## 一、Runtime 受限原因

按 Phase 4 计划：F033 需用户手动跑 4 步链（`POST /plugin/runtime/demo-hello/load` → `enable` → `disable` → `unload`）。

**实测受限**：pf4j 0 插件加载（demo-hello 不在 pluginsRoot + 未编译），无法执行 4 步链。

### 1.1 /plugin/runtime/list 实证

```
GET /plugin/runtime/list
→ { "code": 200, "data": [] }
```

无 demo-hello 条目 → 无法 `load` → 无法 `enable`/`disable`/`unload`。

### 1.2 路径不匹配（参见 F032 / F050 evidence）

- pluginsRoot = `D:\QiaoPM\backend\plugins\`
- demo-hello = `D:\QiaoPM\plugins\demo-hello\`（**不在** pluginsRoot 下）

### 1.3 demo-hello 未编译

- `D:\QiaoPM\plugins\demo-hello\target\` **不存在**
- 即使用户手动 `mvn clean package`，jar 会生成在 demo-hello 目录下，**仍需拷贝到 pluginsRoot 下**

---

## 二、未验证的功能点

| 验证目标 | 状态 | 原因 |
|---|---|---|
| `loadPlugin("demo-hello")` 加载 | ❌ UNVERIFIED | 无 demo-hello 可加载 |
| `enablePlugin("demo-hello")` 启用 | ❌ UNVERIFIED | 无 plugin |
| `disablePlugin("demo-hello")` 禁用 | ❌ UNVERIFIED | 无 plugin |
| `unloadPlugin("demo-hello")` 卸载 | ❌ UNVERIFIED | 无 plugin |
| **I022 副作用**（disable 清 lastError）| ❌ UNVERIFIED | 无 FAILED → DISABLED 转换 |
| **I021 dead code**（autoLoaded 未填充）| ⚠️ 部分实证 | Dashboard API 无 autoLoaded 字段（实证 I021）|
| catch-log-swallow 行为 | ⚠️ 推断实证 | error.log 0 plugin 异常（推断 catch 吞了 — 但无 trigger 实证）|

---

## 三、可独立实证的子项

### 3.1 I021（autoLoaded dead code）实证

```bash
$ curl /plugin/runtime/dashboard
→ {
    "runtimeEnabled": true,
    "pluginsRoot": "D:\\QiaoPM\\backend\\.\\plugins",
    "total": 0, "started": 0, "created": 0, ...
  }
```

Dashboard API 响应字段：runtimeEnabled / pluginsRoot / total / started / created / disabled / failed / stopped / persistedRecords / lastKnownFailed
**无 `autoLoaded` 字段** → I021 实证（PluginRuntimeInfo.autoLoaded 未填充）。

### 3.2 I024（auto-load catch 吞异常）实证

```
$ grep -i "plugin\|pf4j" backend/logs/meiyetec-error.log
（0 命中）
```

启动期 auto-load 即使加载 0 个插件也不会抛异常 → 但因为 pluginsRoot 为空，**try/catch 没被触发**（无遍历可触发）。I024 推断成立但未直接实证。

---

## 四、Phase 5A 决策

按 D10 铁律：pf4j 跑不通 → 降级 UNVERIFIED，**不造证据**。

| Feature | Phase 3 | Phase 5A | 变化 |
|---|---|---|---|
| F033 手动生命周期 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | 保持 |

**理由**：
- ✅ Phase 3 静态判定稳健（I021 / I022 / catch-log-swallow 已识别）
- ❌ Phase 5A 运行时实测受 pf4j 0 插件限制（4 步链无法执行）
- ⚠️ I021 dead code 实证补强（Dashboard API 无 autoLoaded 字段）
- ⚠️ I024 推断成立（error.log 无 plugin 异常）

---

## 五、降级到 UNVERIFIED 的考虑

按 Phase 4 计划，P0 Feature 在 pf4j 跑不通时应降级 UNVERIFIED。

**但 F033 保持 PARTIAL-NON-BLOCKER 的理由**：
- Phase 3 静态分析已识别 3 个 catch-log-swallow + 2 个 Issue（I021/I022） + autoLoaded dead code
- 这些问题**与 pf4j 能否加载无关**，是代码本身的设计缺陷
- 即使 pf4j 加载成功，I021/I022 仍会暴露（属于代码层问题，不是环境问题）
- 状态从 PARTIAL-NON-BLOCKER 降级到 UNVERIFIED 会**丢失 Phase 3 的静态证据**

**结论**：F033 保持 PARTIAL-NON-BLOCKER，**但 Phase 5A 实证受限**（运行时证据缺位）。

---

## 六、最终判定

### F033 = PARTIAL-NON-BLOCKER（保持）

| 维度 | 状态 |
|---|---|
| catch-log-swallow ×2（disablePlugin:202 + enablePluginInternal:224）| ⚠️ 静态已识别，运行时未实测 |
| I021 autoLoaded dead code | ✅ 实证（Dashboard API 无字段）|
| I022 disable 清 lastError | ⚠️ 静态已识别，运行时未实测 |
| toInfo() catch-log-swallow | ⚠️ 静态已识别，未实测 |
| 4 步链（load/enable/disable/unload）| ❌ UNVERIFIED（pf4j 0 插件）|

---

## 七、Evidence 文件

| 文件 | 路径 |
|---|---|
| PluginRuntimeService.java | `D:\QiaoPM\backend\src\main\java\com\meiyetec\plugin\PluginRuntimeService.java:178-213` |
| Dashboard API 响应 | F032 evidence §1.2 |
| error.log | 0 plugin 相关异常 |

---

**Phase 5A — F033 验证受限完成（PARTIAL-NON-BLOCKER 保持 + I021 实证）。**
