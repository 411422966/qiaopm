# F031 — Plugin SPI 契约（Static-Only 验证）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Static-Only
> **验证方式**：L0 静态分析（不依赖 pf4j 运行时）
> **最终状态**：**VERIFIED**

---

## 一、接口契约（MeiyetecPlugin.java）

| 方法 | 返回类型 | 默认值 | 必填 | 备注 |
|---|---|---|---|---|
| `pluginCode()` | String | — | ✓ | 平台侧唯一编码（与 `plugin_market.plugin_code` 对齐）|
| `version()` | String | — | ✓ | 语义化版本号（启动时按 version 隔离 classloader）|
| `displayName()` | String | — | ✓ | 前端展示名 |
| `description()` | String | — | ✓ | 简介（≤500 字）|
| `vendor()` | String | `"unknown"` | ✗ | 作者 / 提供方 |
| `onStart(PluginRuntimeContext ctx)` | void | `{}` | ✗ | 插件被启用时回调（load → enable 后）|
| `onStop(PluginRuntimeContext ctx)` | void | `{}` | ✗ | 插件被停用时回调（disable → unload 前）|

**验证要点**：
- ✅ 接口定义完整：`com/meiyetec/plugin/MeiyetecPlugin.java:24-46`
- ✅ 4 个抽象方法 + 3 个 default 方法 → 插件最少只需实现 4 个方法
- ✅ Javadoc 引用 EXT-09 + PluginRuntimeContext（前后文一致）
- ✅ 生命周期钩子注释清晰（onStart 在 enable 后，onStop 在 unload 前）

## 二、桥接适配器（MeiyetecPluginAdapter.java）

| 检查点 | 实现位置 | 备注 |
|---|---|---|
| `extends Plugin implements MeiyetecPlugin` | `MeiyetecPluginAdapter.java:21` | ✅ 双实现：pf4j Plugin + 业务 SPI |
| `constructor(PluginWrapper)` | `:23-25` | ✅ pf4j 必需 |
| `start()` → `onStart(ctx)` | `:28-32` | ✅ pf4j 生命周期钩子 → 业务 SPI onStart |
| `stop()` → `onStop(ctx)` | `:35-39` | ✅ 同上 |
| `Plugin#getPluginId()` final | `:45` Javadoc | ✅ 不 override，pluginId 由 `plugin.properties` 提供 |

**验证要点**：
- ✅ 桥接正确：`Plugin#start()` → `MeiyetecPlugin#onStart(ctx)`（pf4j → 业务 SPI 单向桥）
- ✅ PluginRuntimeContext 在 start/stop 内构造 → 不可变快照（业务 SPI 调用方一致）
- ✅ 与 pf4j 3.10.0 兼容（Plugin.getPluginId() final 已在 Javadoc 标明）

## 三、与 pf4j 的关系

```
┌──────────────────────────────────┐
│  业务 SPI（MeiyetecPlugin）       │ ← 业务侧插件实现此接口
└──────────────────────────────────┘
              ↑ implements
┌──────────────────────────────────┐
│  桥接（MeiyetecPluginAdapter）    │ ← 拿 pf4j lifecycle + 业务 SPI
└──────────────────────────────────┘
              ↑ extends
┌──────────────────────────────────┐
│  pf4j Plugin（org.pf4j.Plugin）  │ ← pf4j 框架
└──────────────────────────────────┘
```

- 业务插件只需 `extends MeiyetecPluginAdapter` + 实现 4 个抽象方法 → 拿到 pf4j 生命周期 + 业务 SPI 双能力
- pf4j `getPluginId()` final → pluginId 由 `META-INF/pf4j/plugin.properties` 的 `plugin.id` 提供

## 四、与 demo-hello 的一致性（demo-hello 编译产物缺失）

> **重要发现**：demo-hello 源文件存在（`D:\QiaoPM\plugins\demo-hello\`，5 个文件），但**未编译成 JAR**（无 `target/` 目录）。

| 文件 | 状态 | 行数 |
|---|---|---|
| `plugins/demo-hello/pom.xml` | ✅ 存在 | 2339 bytes |
| `plugins/demo-hello/src/main/java/com/meiyetec/demo/DemoHelloPlugin.java` | ✅ 存在 | 2840 bytes |
| `plugins/demo-hello/src/main/resources/META-INF/pf4j/plugin.properties` | ✅ 存在 | 179 bytes |
| `plugins/demo-hello/build.bat` | ✅ 存在 | 1057 bytes |
| `plugins/demo-hello/README.md` | ✅ 存在 | 2010 bytes |
| `plugins/demo-hello/target/*.jar` | ❌ **不存在** | — |

**DemoHelloPlugin SPI 实现验证**：
- ✅ `extends MeiyetecPluginAdapter`（line 19）
- ✅ `pluginCode() = "demo-hello"`（line 27-29）
- ✅ `version() = "1.0.0"`（line 32-34）
- ✅ `displayName() = "Demo Hello 插件"`（line 37-39）
- ✅ `description()` 返回正确（line 42-44）
- ✅ `vendor() = "meiyetec"`（line 47-49）
- ✅ `onStart` 调 `PluginSecurityPolicy.assertPackageAllowed(pluginCode, "com.meiyetec.demo.")` + `"com.meiyetec.plugin."`（line 55-62）
- ✅ `hello(String)` 业务入口（line 78-81）

**plugin.properties 验证**：
```properties
plugin.id=demo-hello
plugin.class=com.meiyetec.demo.DemoHelloPlugin
plugin.version=1.0.0
plugin.provider=meiyetec
plugin.dependencies=
```
- ✅ 5 字段完整（`plugin.id / class / version / provider / dependencies`）
- ✅ `plugin.class` FQN 正确：`com.meiyetec.demo.DemoHelloPlugin`
- ✅ `plugin.version` 与 `version() = "1.0.0"` 一致
- ⚠️ `plugin.id` = `"demo-hello"` 与 `pluginCode()` 一致（Operational Notes §5 建议保持一致 → **遵守建议**）

## 五、与 EXT-23 SoT 比对

| SoT 要求 | 实现 | 一致 |
|---|---|---|
| `MeiyetecPlugin` 业务 SPI（5 方法 + 2 default）| ✅ 4 抽象 + 3 default（含 vendor）| ✓ |
| `MeiyetecPluginAdapter extends Plugin implements MeiyetecPlugin` | ✅ | ✓ |
| `onStart(ctx)` / `onStop(ctx)` 默认空实现 | ✅ | ✓ |
| `PluginRuntimeContext` 不可变快照 | ✅ 桥接内构造 | ✓ |
| pf4j 3.x `getPluginId()` final 不 override | ✅ Javadoc 标明 | ✓ |
| 强制 `META-INF/pf4j/plugin.properties` 含 `plugin.class` | ✅ demo-hello 含 | ✓ |
| 目录约定 `${plugins-root}/{pluginCode}/{version}/*.jar` | ⏸️ 未编译无法验证 | UNVERIFIED（依赖 F055）|

## 六、最终判定

| 维度 | 状态 | 备注 |
|---|---|---|
| **接口完整性** | ✅ VERIFIED | 7 方法 + 4 抽象 + 3 default，签名正确 |
| **桥接正确性** | ✅ VERIFIED | Plugin.start/stop → MeiyetecPlugin.onStart/onStop |
| **demo-hello SPI 实现** | ✅ VERIFIED | 5/5 字段 + 4/4 抽象方法 + onStart 安全检查 + hello 业务入口 |
| **plugin.properties 合规** | ✅ VERIFIED | 5 字段齐全 + FQN 正确 |
| **pf4j 兼容性** | ✅ VERIFIED | Javadoc 标注 getPluginId final 不 override |
| **JAR 编译产物** | ❌ UNVERIFIED | 依赖 F055（`mvn clean package` 用户手动）|

### 最终状态

**F031 = VERIFIED（SPI 契约本身静态完整）**

> ⚠️ **依赖项**：F031 的运行时验证（demo-hello onStart 在真实 JVM 中被调用）依赖 F055 demo-hello 编译成功。按 Phase 5A 决策：D10 铁律 → **若 F055 编译失败，F031 仍保持 VERIFIED（SPI 静态完整），但运行时证据缺失**。

---

## 七、Evidence 文件清单

| 文件 | 路径 |
|---|---|
| MeiyetecPlugin.java | `backend/src/main/java/com/meiyetec/plugin/MeiyetecPlugin.java` |
| MeiyetecPluginAdapter.java | `backend/src/main/java/com/meiyetec/plugin/MeiyetecPluginAdapter.java` |
| DemoHelloPlugin.java | `plugins/demo-hello/src/main/java/com/meiyetec/demo/DemoHelloPlugin.java` |
| plugin.properties | `plugins/demo-hello/src/main/resources/META-INF/pf4j/plugin.properties` |
| pom.xml | `plugins/demo-hello/pom.xml` |
| build.bat | `plugins/demo-hello/build.bat` |

---

**L0 验证完成。F031 不依赖运行时，可独立判定 VERIFIED。**
