# I103 — PluginRuntimeTest 编译失败

| 项 | 值 |
|---|---|
| **Issue ID** | I103 |
| **标题** | PluginRuntimeTest.java 编译失败，mvn test CI 必红 |
| **严重度** | **P1**（阻塞 CI backend job） |
| **影响范围** | `mvn test` / `mvn package` / CI/CD pipeline backend job |
| **发现批次** | Gate 3 批次 1.4a（2026-10-12 凌晨 mvn build） |
| **修复批次** | Gate 3 批次 2.x 或独立 hotfix |
| **负责人** | 后端主值班 |
| **状态** | ⚠️ OPEN |

---

## 1. 问题描述

### 1.1 编译错误（mvn build 截获）

```
[ERROR] /D:/QiaoPM/backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java:[89,36]
无法解析 com.meiyetec.plugin.PluginRuntimeService 中的构造函数
  需要: PluginRuntimeService.PluginRuntimeProperties,PluginPersistenceService,PluginTenantService
  找到: com.meiyetec.plugin.PluginRuntimeService.PluginRuntimeProperties
  原因: 实际参数列表长度与形式参数列表长度不同
[ERROR] /D:/QiaoPM/backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java:[100,36]
无法解析 com.meiyetec.plugin.PluginRuntimeService 中的构造函数
  需要: PluginRuntimeService.PluginRuntimeProperties,PluginPersistenceService,PluginTenantService
  找到: com.meiyetec.plugin.PluginRuntimeService.PluginRuntimeProperties
  原因: 实际参数列表长度与形式参数列表长度不同
[ERROR] /D:/QiaoPM/backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java:[112,36]
无法解析 com.meiyetec.plugin.PluginRuntimeService 中的构造函数
  需要: PluginRuntimeService.PluginRuntimeProperties,PluginPersistenceService,PluginTenantService
  找到: com.meiyetec.plugin.PluginRuntimeService.PluginRuntimeProperties
  原因: 实际参数列表长度与形式参数列表长度不同
[ERROR] /D:/QiaoPM/backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java:[131,36]
无法解析 com.meiyetec.plugin.PluginRuntimeService 中的构造函数
  需要: PluginRuntimeService.PluginRuntimeProperties,PluginPersistenceService,PluginTenantService
  找到: com.meiyetec.plugin.PluginRuntimeService.PluginRuntimeProperties
  原因: 实际参数列表长度与形式参数列表长度不同
```

### 1.2 根因

`PluginRuntimeService` 构造器在 EXT-23 接入时扩展为 **3 参**：

```java
// PluginRuntimeService.java（当前）
public PluginRuntimeService(
    PluginRuntimeProperties properties,   // 嵌套类
    PluginPersistenceService persistence,   // 新增依赖
    PluginTenantService tenant            // 新增依赖
) { ... }
```

`PluginRuntimeTest.java` 仍使用旧的 **1 参**构造器调用：

```java
// PluginRuntimeTest.java（过时）
private PluginRuntimeService createService() {
    PluginRuntimeProperties props = new PluginRuntimeProperties();
    return new PluginRuntimeService(props);  // ❌ 只传 1 个参数
}
```

**EXT-23 接入时漏改了测试代码**，属于 SoT 漂移（entity/Service 改了但 test 未同步）。

### 1.3 业务影响

| 影响维度 | 描述 |
|---|---|
| **CI/CD** | `mvn test` 失败 → backend job 红 → 所有 PR 无法合并 |
| **本地开发** | `mvn package` 时即便 `-DskipTests` 也会因 test-compile 阶段失败而失败 |
| **绕过方式** | `mvn -D"maven.test.skip=true" package` 可跳过 test-compile，但这是 CI 不接受的黑魔法 |

### 1.4 关联上下文

- **相关 Issue**：I020（`assertCallerAllowed` 沙箱问题，已 fix commit `7d455eb`）、I022（`disablePlugin` lastError，已 fix commit `8c256c9`）、I024（前端 runtime tab，已 fix commit `1f2b608`）— 都是 EXT-23 批次的历史遗留
- **类路径**：`backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java`
- **涉及类**：`PluginRuntimeService`、`PluginPersistenceService`、`PluginTenantService`
- **Gate 3 1.4a 绕过**：使用 `mvn -D"maven.test.skip=true" package` 临时跳过 test 编译

---

## 2. 修复方案

### 2.1 推荐方案（加 mock 依赖）

在 `PluginRuntimeTest.java` 的 `createService()` 方法中，补齐 3 个构造器参数：

```java
private PluginRuntimeService createService() {
    PluginRuntimeProperties props = new PluginRuntimeProperties();
    // 设置必要的 @Value 字段（可用 ReflectionTestUtils.setField）
    props.setPluginsRoot("./plugins");
    props.setEnabled(true);
    props.setAutoLoadOnStartup(false);

    // Mock PluginPersistenceService
    PluginPersistenceService mockPersistence = mock(PluginPersistenceService.class);
    when(mockPersistence.selectByTenantAndCode(anyLong(), anyString()))
        .thenReturn(null);

    // Mock PluginTenantService
    PluginTenantService mockTenant = mock(PluginTenantService.class);
    when(mockTenant.isEnabledForTenant(anyString(), anyLong())).thenReturn(true);

    return new PluginRuntimeService(props, mockPersistence, mockTenant);
}
```

### 2.2 备选方案（Spring context test）

改用 `@SpringBootTest` 加载真实 Spring 上下文，Spring 自动注入 3 个依赖：

```java
@SpringBootTest
class PluginRuntimeTest {
    @Autowired
    private PluginRuntimeService service;

    @Test
    void testDashboard() { ... }
}
```

优点：无需手写 mock；缺点：启动慢（约 30s），不适合纯单元测试场景。

### 2.3 验证方法

修复后验证：

```bash
cd D:\QiaoPM\backend
mvn test -Dtest=PluginRuntimeTest
# 期望：BUILD SUCCESS（PluginRuntimeTest 全部 PASS）

mvn package
# 期望：BUILD SUCCESS（不再因 test-compile 失败）
```

---

## 3. 影响评估

| 项目 | 评估 |
|---|---|
| **紧急度** | P1 — 阻塞 CI，所有 PR 红 |
| **影响范围** | 仅 PluginRuntimeTest.java 一个文件 |
| **修复难度** | 低 — 3 行 mock 代码 |
| **风险** | 低 — 不改业务逻辑，只补测试依赖 |

---

## 4. 关联文档

- `backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java` — 故障文件
- `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java` — 3 参构造器
- `backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java` — 新增依赖
- `backend/src/main/java/com/meiyetec/plugin/PluginTenantService.java` — 新增依赖
- `audit/issues/issue-register.md` — EXT-23 历史 Issue（I020/I022/I024）
- Gate 3 批次 2.x — 计划修复批次

---

## 5. 闭环记录

| 动作 | 日期 | 证据 |
|---|---|---|
| 发现 | 2026-10-12 凌晨 | mvn build 截获（shell 122654/122655） |
| 临时绕过 | 2026-10-12 凌晨 | `mvn -D"maven.test.skip=true" package` BUILD SUCCESS |
| I103 登记 | 2026-10-12 | 本文档 + issue-register.md |
| 修复 | 待定 | Gate 3 批次 2.x |
| 验证 | 待定 | `mvn test -Dtest=PluginRuntimeTest` BUILD SUCCESS |
