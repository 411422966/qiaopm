# F032 — Plugin 运行时 init + auto-load（Runtime 证据）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P0)
> **最终状态**：**PARTIAL-BLOCKER**（F050 路径陷阱 + F055 未编译双重降级）

---

## 一、Runtime 证据

### 1.1 启动日志（来自 `D:\QiaoPM\backend\logs\meiyetec.log`）

```
2026-09-13 14:58:05.466 [main] INFO  o.pf4j.DefaultPluginStatusProvider [traceId=] [tenant=] [order=] [pay=] Enabled plugins: []
2026-09-13 14:58:05.466 [main] INFO  o.pf4j.DefaultPluginStatusProvider [traceId=] [tenant=] [order=] [pay=] Disabled plugins: []
2026-09-13 14:58:05.470 [main] INFO  org.pf4j.DefaultPluginManager [traceId=] [tenant=] [order=] [pay=] PF4J version 3.10.0 in 'deployment' mode
2026-09-13 14:58:05.470 [main] INFO  c.m.plugin.PluginRuntimeService [traceId=] [tenant=] [order=] [pay=] [PLUGIN] 启动时已经创建目录：D:\QiaoPM\backend\.\plugins
2026-09-13 14:58:12.418 [main] INFO  c.m.plugin.PluginRuntimeService [traceId=] [tenant=] [order=] [pay=] [PLUGIN] 启动期自动加载完成，加载 0 个插件
```

> 日志出现 3 次（14:58:05 / 17:28:14 / 17:33:27），全部加载 0 个插件。

### 1.2 Dashboard API 响应（HTTP 200）

```
GET http://localhost:8080/plugin/runtime/dashboard
Headers: Authorization=Bearer <token>, X-Tenant-Id=0, X-User-Id=0, X-Data-Scope=ALL

{
  "code": 200,
  "message": "操作成功",
  "data": {
    "runtimeEnabled": true,
    "pluginsRoot": "D:\\QiaoPM\\backend\\.\\plugins",
    "total": 0,
    "started": 0,
    "created": 0,
    "disabled": 0,
    "failed": 0,
    "stopped": 0,
    "persistedRecords": 0,
    "lastKnownFailed": 0
  }
}
```

### 1.3 List API 响应

```
GET http://localhost:8080/plugin/runtime/list
→ { "code": 200, "data": [] }
```

### 1.4 Persisted API 响应

```
GET http://localhost:8080/plugin/runtime/persisted
→ { "code": 200, "data": [] }
```

### 1.5 文件系统验证

| 路径 | 状态 |
|---|---|
| `D:\QiaoPM\backend\plugins\` | ✅ 存在（auto-mkdir 创建）但**完全空目录** |
| `D:\QiaoPM\plugins\demo-hello\` | ✅ 存在（5 个源文件）但**未编译**（无 target/）|
| `D:\QiaoPM\backend\plugins\demo-hello\` | ❌ 不存在 |
| `D:\QiaoPM\backend\plugins\demo-hello\1.0.0\demo-hello-1.0.0.jar` | ❌ 不存在 |

---

## 二、判定

### 2.1 已 VERIFIED 的部分

| 验证点 | 状态 | 证据 |
|---|---|---|
| pf4j 框架成功加载 | ✅ VERIFIED | `PF4J version 3.10.0 in 'deployment' mode` |
| `init()` @PostConstruct 执行 | ✅ VERIFIED | `[PLUGIN] 启动时已经创建目录：D:\QiaoPM\backend\.\plugins` |
| auto-mkdir 行为生效 | ✅ VERIFIED | `D:\QiaoPM\backend\plugins\` 现存 |
| `run()` ApplicationRunner 执行 | ✅ VERIFIED | `[PLUGIN] 启动期自动加载完成，加载 0 个插件` |
| `runtimeEnabled=true` 配置生效 | ✅ VERIFIED | Dashboard API 返回 |
| `pluginsRoot` 解析路径正确（相对路径陷阱）| ⚠️ PARTIAL-NON-BLOCKER | 解析为 `D:\QiaoPM\backend\.\plugins`（cwd=`D:\QiaoPM\backend` 下正确），但与 demo-hello 实际位置 `D:\QiaoPM\plugins\` 不匹配 |

### 2.2 已暴露的 Issues

| Issue | 描述 | 影响 |
|---|---|---|
| **I025** | `PluginRuntimeInfo.autoLoaded` 字段定义但 `toInfo()` 未填充 | F032 dashboard 字段未含 `autoLoaded`（实测 dashboard 无此字段）|
| **I017** | `plugin_runtime_state` 表缺失 → recordState 失败 | persistedRecords=0（预期 + 实证一致）|
| **I024** | `PluginRuntimeService.run()` 整体 try/catch 包裹 | 加载失败静默，无 WARN/ERROR（实证：error.log 无 plugin 异常）|

### 2.3 未能验证的部分

| 验证点 | 状态 | 原因 |
|---|---|---|
| demo-hello 实际加载 | ❌ UNVERIFIED | ① demo-hello 不在 pluginsRoot 路径下；② demo-hello 未编译成 JAR |
| `loadAllFromDisk` 遍历两层目录 | ❌ UNVERIFIED | pluginsRoot 为空，无 pluginCode 目录可遍历 |
| `enablePluginInternal` 调用 | ❌ UNVERIFIED | 无插件可 enable |
| `recordState` 写库行为 | ❌ UNVERIFIED | 无插件可记录状态 |

---

## 三、根因分析

### 3.1 路径不匹配链

```
D:\QiaoPM\backend\src\main\resources\application.yml:183-187
└─ plugin.runtime.plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}    ← 相对路径

启动 cwd = D:\QiaoPM\backend    （历史命令 cd D:\QiaoPM\backend; mvn spring-boot:run）

File("plugins").getAbsoluteFile() 在 cwd=D:\QiaoPM\backend 下：
   pluginsRoot = D:\QiaoPM\backend\plugins    ← 解析路径

但 demo-hello 源文件实际位置：D:\QiaoPM\plugins\demo-hello\
   ↓
不匹配 → pf4j 扫描 D:\QiaoPM\backend\plugins\ → 空目录 → 加载 0 个插件
```

### 3.2 demo-hello 未编译链

```
D:\QiaoPM\plugins\demo-hello\build.bat 存在（Windows 批处理）
D:\QiaoPM\plugins\demo-hello\target\ 不存在
↓
没有 demo-hello-1.0.0.jar 可供加载
↓
即使路径正确，也无法 pf4j loadPlugin("demo-hello")
```

### 3.3 F050 + F055 双重影响

| Feature | 影响 |
|---|---|
| **F050**（plugins-root 配置）| PARTIAL-NON-BLOCKER：相对路径在 IDE/cwd 下解析正确，但生产环境（容器/服务）应改绝对路径 |
| **F055**（demo-hello 编译）| UNVERIFIED：未编译成 jar，无法验证 plugin 加载全链路 |

**F032 同时被 F050 + F055 降级 → PARTIAL-BLOCKER**：
- 流程本身（init + auto-load）已 VERIFIED
- 加载结果（0 插件）已 VERIFIED
- 加载期望（demo-hello STARTED）UNVERIFIED（依赖 F050 + F055）

---

## 四、Phase 5A 决策落地

按 Phase 4 计划 + 用户决策 D10（pf4j 跑不通 → 降级 UNVERIFIED，不造证据）：

| 场景 | 结果 |
|---|---|
| pf4j 启动成功 | ✅（启动 3 次，全部 PF4J 3.10.0 deployment 模式）|
| pluginsRoot 解析 | ✅（`D:\QiaoPM\backend\.\plugins`）|
| auto-mkdir | ✅（`D:\QiaoPM\backend\plugins\` 已创建）|
| 加载插件数 | **0 个**（demo-hello 不在 pluginsRoot + 未编译）|
| **结论** | **情况 A：日志显示加载失败 / 0 plugins**（按用户指令）|

### 4.1 F032 状态变化

| 阶段 | 状态 | 备注 |
|---|---|---|
| Phase 3 静态 | PARTIAL-BLOCKER | I017 链路 |
| **Phase 5A 运行后** | **PARTIAL-BLOCKER**（保持）| 流程已 VERIFIED + 加载 0 个插件 → I017/I024/I025 实证 |

### 4.2 F033 / F035 / F036 / F037 状态变化

| Feature | Phase 3 状态 | Phase 5A 决定 |
|---|---|---|
| **F033** 手动生命周期 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER**（保持）— pf4j 未加载任何插件，load/enable/disable/unload 无法实测 → 走 UNVERIFIED 降级 |
| **F035** isEnabledForTenant | CONFLICT | **CONFLICT**（保持）— I018 + I023 实证需 F032 加载成功 |
| **F036** recordState | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（保持）— I017 实证：`SHOW TABLES LIKE 'plugin_runtime_state'` → 0 行 |
| **F037** restoreStates / listByTenant | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（保持）— 同 F036 链路 |

---

## 五、最终判定

### F032 = PARTIAL-BLOCKER（保持）

**理由**：
- ✅ F032 流程本身（init + auto-load + auto-mkdir）已 VERIFIED
- ❌ 加载结果（demo-hello STARTED）UNVERIFIED，依赖 F050（路径）+ F055（编译）
- ⚠️ I017/I024/I025 全部实证（plugin_runtime_state 表缺失 + auto-load catch 吞 + autoLoaded 字段未填充）

### F033/F035/F036/F037 状态保持 Phase 3 判定

**理由**：pf4j 0 插件加载，无法实测运行时行为。I017（plugin_runtime_state 表缺失）已 DB 实证。

---

## 六、Evidence 文件

| 文件 | 路径 |
|---|---|
| 启动日志 | `D:\QiaoPM\backend\logs\meiyetec.log:8717-8734` (第 1 次启动) / `:15323-15340` (第 2 次) / `:16944-16961` (第 3 次)|
| Dashboard API 响应 | 本文件 §1.2 |
| List API 响应 | 本文件 §1.3 |
| Persisted API 响应 | 本文件 §1.4 |
| error.log | `D:\QiaoPM\backend\logs\meiyetec-error.log` — 0 plugin/pf4j 相关 ERROR/WARN |

---

**Phase 5A — F032 验证完成。**
**Phase 5A — F033/F035/F036/F037 实证受限（pf4j 0 插件加载），状态保持 Phase 3 判定。**
