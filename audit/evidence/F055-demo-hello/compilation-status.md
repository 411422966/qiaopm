# F055 — Plugin 示例 demo-hello（用户手动编译）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P2)
> **最终状态**：**UNVERIFIED**（demo-hello 未编译成 jar）

---

## 一、当前状态

### 1.1 demo-hello 目录结构

```
D:\QiaoPM\plugins\demo-hello\
├── README.md                       (2010 bytes)
├── build.bat                       (1057 bytes)
├── pom.xml                         (2339 bytes)
└── src/
    └── main/
        ├── java/
        │   └── com/meiyetec/demo/
        │       └── DemoHelloPlugin.java  (2840 bytes)
        └── resources/
            └── META-INF/pf4j/
                └── plugin.properties    (179 bytes)
```

**重要**：`target/` 目录**不存在** → **未编译成 jar**。

### 1.2 编译命令（build.bat）

用户需要手动执行：
```bash
cd D:\QiaoPM\plugins\demo-hello
build.bat
```

预期产出：`D:\QiaoPM\plugins\demo-hello\target\demo-hello-1.0.0.jar`

然后**还必须**拷贝到 pluginsRoot 路径：
```bash
mkdir D:\QiaoPM\backend\plugins\demo-hello\1.0.0
copy D:\QiaoPM\plugins\demo-hello\target\demo-hello-1.0.0.jar D:\QiaoPM\backend\plugins\demo-hello\1.0.0\
```

最后**重启后端**或调 `POST /plugin/runtime/demo-hello/load`。

---

## 二、Phase 5A 决策（D10 铁律）

按用户决策 D10：**Phase 5 pf4j 跑不通 → 降级 UNVERIFIED，不造证据**。

### 2.1 降级链

```
F055 demo-hello 编译失败 / 未编译
  ↓
F031 SPI 静态完整（保持 VERIFIED）— SPI 契约本身不需要 jar
  ↓
F032/F033/F035/F036/F037 全部运行时受限制（PARTIAL-* 保持）
  ↓
F041/F042 沙箱验证需要 demo-hello onStart 调用 → 受限制（PARTIAL-NON-BLOCKER 保持）
```

### 2.2 受影响 Feature 状态汇总

| Feature | Phase 3 | Phase 5A | 变化原因 |
|---|---|---|---|
| **F031** SPI 契约 | UNVERIFIED | **VERIFIED** | L0 静态完整，独立判定 |
| **F032** Plugin init + auto-load | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | 流程 VERIFIED + 加载 0 插件 |
| **F033** 手动生命周期 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | I021 实证 + 4 步链受限制 |
| **F035** isEnabledForTenant | CONFLICT | **CONFLICT** | I018 + I023 未实测 |
| **F036** recordState | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | I017 DB 实证 + 受限制 |
| **F037** restoreStates / listByTenant | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | 同 F036 |
| **F041** assertPackageAllowed | UNVERIFIED | **UNVERIFIED** | demo-hello 未编译 |
| **F042** assertCallerAllowed | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | 静态已识别 + demo-hello 未编译 |
| **F055** demo-hello | UNVERIFIED | **UNVERIFIED** | 未编译成 jar |

---

## 三、需要用户手动执行的步骤（5A 后续）

### 3.1 编译 demo-hello

```powershell
cd D:\QiaoPM\plugins\demo-hello
.\build.bat
```

**预期产出**：
- `D:\QiaoPM\plugins\demo-hello\target\demo-hello-1.0.0.jar`

**失败处理**：
- Maven 未安装 → 装 Maven 3.x
- Java 版本不匹配 → 检查 `pom.xml` 要求的 Java 版本
- build.bat 报错 → 看 build.bat 内容（可能仅 Windows）

### 3.2 拷贝到 pluginsRoot（路径修正后）

```powershell
# 方案 A：拷贝到现有 pluginsRoot（D:\QiaoPM\backend\plugins\）
New-Item -Path "D:\QiaoPM\backend\plugins\demo-hello\1.0.0" -ItemType Directory -Force
Copy-Item "D:\QiaoPM\plugins\demo-hello\target\demo-hello-1.0.0.jar" "D:\QiaoPM\backend\plugins\demo-hello\1.0.0\"

# 方案 B：修改 application.yml 改绝对路径
# plugins-root: D:\QiaoPM\plugins
# 然后 New-Item + Copy 到 D:\QiaoPM\plugins\demo-hello\1.0.0\
```

### 3.3 重启后端 + 验证

```bash
# 重启后端
# cd D:\QiaoPM\backend; mvn spring-boot:run

# 等启动完成 → 验证
curl /plugin/runtime/list | jq '.data[] | select(.pluginCode=="demo-hello")'
# 预期：返回 demo-hello STARTED 状态

curl /plugin/runtime/dashboard | jq '.data.total'
# 预期：> 0
```

---

## 四、降级原则

按 D10 铁律：

> **pf4j 跑不通 → 降级 UNVERIFIED，不造证据**

Phase 5A 已严格执行：
- ✅ F032-F037 不造「demo-hello STARTED」证据（pf4j 0 插件加载）
- ✅ F041/F042 不造「沙箱拦截 / 不拦截」证据（demo-hello 未编译）
- ✅ F055 不造「demo-hello jar 存在」证据（仅源文件）
- ✅ F031 独立判定 VERIFIED（不依赖 jar）

---

## 五、最终判定

### F055 = UNVERIFIED（保持 Phase 3 判定）

**Phase 5A 决定**：
- Phase 3 状态：UNVERIFIED
- Phase 5A 状态：UNVERIFIED（保持）

**理由**：
- demo-hello 未编译成 jar（target/ 不存在）
- 即使编译成功，仍需拷贝到 pluginsRoot 路径下才能被 pf4j 加载
- 当前路径不匹配（demo-hello 在 D:\QiaoPM\plugins\，pluginsRoot 在 D:\QiaoPM\backend\plugins\）

---

## 六、Evidence 文件

| 文件 | 路径 |
|---|---|
| demo-hello 源 | `D:\QiaoPM\plugins\demo-hello\` |
| build.bat | `D:\QiaoPM\plugins\demo-hello\build.bat` |
| pom.xml | `D:\QiaoPM\plugins\demo-hello\pom.xml` |
| DemoHelloPlugin.java | `D:\QiaoPM\plugins\demo-hello\src\main\java\com\meiyetec\demo\DemoHelloPlugin.java` |
| plugin.properties | `D:\QiaoPM\plugins\demo-hello\src\main\resources\META-INF\pf4j\plugin.properties` |

---

**Phase 5A — F055 验证完成（UNVERIFIED 保持，等用户手动编译）。**
