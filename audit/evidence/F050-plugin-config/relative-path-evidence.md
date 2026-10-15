# F050 — Plugin 配置 application.yml（Runtime 实证）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P2)
> **最终状态**：**PARTIAL-NON-BLOCKER**（保持 Phase 3 判定 + 实证升级）

---

## 一、Configuration

### 1.1 application.yml 配置（`backend/src/main/resources/application.yml:183-187`）

```yaml
plugin:
  runtime:
    enabled: ${PLUGIN_RUNTIME_ENABLED:true}
    plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}
    auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}
```

### 1.2 环境变量解析

| 配置项 | 默认值 | 运行时环境变量 | 实际生效 |
|---|---|---|---|
| `plugin.runtime.enabled` | `true` | 未设 | `true` ✓ |
| `plugin.runtime.plugins-root` | `./plugins` | 未设 | `./plugins` → 相对路径 ⚠️ |
| `plugin.runtime.auto-load-on-startup` | `true` | 未设 | `true` ✓ |

---

## 二、Runtime 实证

### 2.1 启动日志（来自 `D:\QiaoPM\backend\logs\meiyetec.log:8720`）

```
2026-09-13 14:58:05.470 [main] INFO  c.m.plugin.PluginRuntimeService
[traceId=] [tenant=] [order=] [pay=] [PLUGIN] 启动时已经创建目录：D:\QiaoPM\backend\.\plugins
```

### 2.2 Dashboard API 响应

```
GET /plugin/runtime/dashboard
→ {
    "runtimeEnabled": true,
    "pluginsRoot": "D:\\QiaoPM\\backend\\.\\plugins",   ← 相对路径解析后的绝对路径
    ...
  }
```

### 2.3 cwd 推断

```
历史命令：cd D:\QiaoPM\backend; mvn spring-boot:run
实际 cwd：D:\QiaoPM\backend

相对路径 "./plugins" 在 cwd=D:\QiaoPM\backend 下解析：
  new File("plugins").getAbsoluteFile()
  = D:\QiaoPM\backend\plugins
  = D:\QiaoPM\backend\.\plugins（实测 dashboard 返回值）
```

### 2.4 demo-hello 实际位置 vs pluginsRoot

| 项 | 路径 |
|---|---|
| pluginsRoot（运行时）| `D:\QiaoPM\backend\plugins\` |
| demo-hello 源（实际）| `D:\QiaoPM\plugins\demo-hello\` |
| **匹配？** | ❌ **不匹配**（差 1 层 `backend`）|

### 2.5 启动结果

| 指标 | 值 |
|---|---|
| pf4j 启动 | ✅ PF4J 3.10.0 deployment mode |
| pluginsRoot 目录 | ✅ 自动创建（`D:\QiaoPM\backend\plugins\`）|
| 加载插件数 | **0 个**（pluginsRoot 为空 + demo-hello 不在该路径）|
| error.log 异常 | 0 条 plugin 相关 ERROR/WARN（与 I024 一致 — 整体 try/catch 吞异常）|

---

## 三、判定

### 3.1 Phase 3 静态判定

| Issue | 描述 | 状态 |
|---|---|---|
| **F050** | `./plugins` 相对路径风险（生产建议绝对路径）| PARTIAL-NON-BLOCKER |

### 3.2 Phase 5A 运行后判定

**保持 PARTIAL-NON-BLOCKER**（实证未升级到 BLOCKER，原因是流程本身工作正常，但路径配置有隐患）。

| 维度 | 状态 | 备注 |
|---|---|---|
| 三个配置项生效 | ✅ VERIFIED | dashboard `runtimeEnabled=true` + pluginsRoot 解析正确 |
| 相对路径在 dev 环境解析正确 | ✅ VERIFIED | cwd=`D:\QiaoPM\backend` 下解析为 `D:\QiaoPM\backend\plugins` |
| demo-hello 被加载 | ❌ FAILED | 路径不匹配 + demo-hello 未编译 |
| 生产环境（容器/服务）行为 | ⚠️ UNVERIFIED | 容器 cwd 通常是 `/app`，会解析为 `/app/plugins` —— 需要挂载 volume 或改绝对路径 |
| auto-mkdir 行为 | ✅ VERIFIED | `D:\QiaoPM\backend\plugins\` 自动创建 |

### 3.3 风险分级

| 风险等级 | 场景 | 影响 |
|---|---|---|
| 🟢 低 | dev / 本地（cwd=backend）| 相对路径解析正确，能跑 |
| 🟡 中 | IDE 中启动（cwd=project root）| 解析为 `<project-root>/plugins/`，可能与 demo-hello 位置不匹配 |
| 🔴 高 | 生产容器（cwd=/app）| 解析为 `/app/plugins/`，容器内通常无此目录，**pluginsRoot 永远为空** |
| 🔴 高 | systemd 服务（cwd=/ 或 /var/lib/...）| 同上 |

### 3.4 与 EXT-23 SoT §0.6 / §2 比对

| SoT 要求 | 实施 | 一致 |
|---|---|---|
| `enabled` 默认 true | ✅ `${PLUGIN_RUNTIME_ENABLED:true}` | ✓ |
| `plugins-root` 默认相对路径 + **生产建议绝对路径** | ⚠️ 默认 `./plugins` 无注释提醒生产改绝对路径 | △ 需补注释 |
| `auto-load-on-startup` 默认 true + **生产首启建议关闭** | ⚠️ 默认 `true` 无注释提醒 | △ 需补注释 |

---

## 四、修复建议（Post-Audit，不在本 Phase 5 执行）

### 4.1 方案 A（推荐）：application.yml 加注释 + 默认改绝对路径

```yaml
plugin:
  runtime:
    # 总开关；false 时仍暴露 REST 元数据
    enabled: ${PLUGIN_RUNTIME_ENABLED:true}
    # 生产建议绝对路径（容器/服务模式下避免相对路径找不到）
    plugins-root: ${PLUGIN_PLUGINS_ROOT:/data/plugins}   # dev 默认相对路径 ./plugins
    # 生产首启建议关闭（避免未审核插件自动加载）
    auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}
```

### 4.2 方案 B：环境变量强制

部署时设置：
```bash
PLUGIN_PLUGINS_ROOT=/var/lib/meiyetec/plugins
PLUGIN_AUTOLOAD=false
```

---

## 五、最终判定

### F050 = PARTIAL-NON-BLOCKER（保持 + 实证升级）

**Phase 3 → Phase 5A 变化**：
- Phase 3：纯静态分析，建议改绝对路径
- Phase 5A：实测路径解析行为正确（dev 环境），但 demo-hello 不在路径下 → **未升级到 BLOCKER**（流程本身无 Bug，是 demo-hello 部署位置问题）

### 实证补强

| 实证维度 | 结果 |
|---|---|
| 配置加载 | ✅ VERIFIED |
| 相对路径解析 | ✅ VERIFIED（dev 环境）|
| auto-mkdir | ✅ VERIFIED |
| 生产路径风险 | ⚠️ 静态已识别（未实测）|

---

## 六、Evidence 文件

| 文件 | 路径 |
|---|---|
| application.yml | `D:\QiaoPM\backend\src\main\resources\application.yml:183-187` |
| 启动日志 | `D:\QiaoPM\backend\logs\meiyetec.log:8720` (14:58:05) / `:15326` (17:28:14) / `:16947` (17:33:27) |
| Dashboard API 响应 | 见 F032 evidence §1.2 |

---

**Phase 5A — F050 验证完成（PARTIAL-NON-BLOCKER 保持）。**
