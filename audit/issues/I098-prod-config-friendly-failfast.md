# I098 — prod 配置文件缺友好 fail-fast 消息

> **Issue ID**：I098
> **严重度**：P3
> **发现时间**：2026-10-09 00:25 (UTC+8)
> **触发事件**：批次 1.7 生产配置验证，发现 Spring 6.x 不支持 `${ENV:?msg}` fail-fast 语法
> **类别**：配置 / 启动期可观测性
> **Fix Phase**：Gate 3 后续批次 / Post-Audit 三期
> **关联**：批次 1.7 / `application-prod.yml` / commit `765609e`

---

## 0. 摘要

期望在 `application-prod.yml` 用 `${SPRING_DATASOURCE_URL:?prod 必须设置}` 让 prod 部署缺关键 ENV 时启动期**显式抛异常**并附**人类可读消息**。

实测：Spring 6.x 的 `PropertySourcesPropertyResolver` **不识别** `${prop:?msg}` 语法 — 它把 `?msg` 整体当作 default 字符串（即 `${prop:?msg}` 实际行为 = `${prop:?msg}` 中 `:` 与 `?` 之间的部分当 default）。

**结果**：缺 `SPRING_DATASOURCE_URL` 时 JDBC URL 变成 `jdbc:mysql://...?PROD 配置错误：SPRING_DATASOURCE_URL 未设置` → DataSource 报 "Driver claims to not accept jdbcUrl" → 启动确实失败（隐式 fail-fast），但失败信息混在 Flyway 初始化堆栈里，**运维无法直接定位** "prod 必须设 SPRING_DATASOURCE_URL"。

**临时方案**（已采用）：`${ENV:}` 空 default + 依赖下游组件（DataSource / Flyway / JwtUtil）启动失败触发（隐式 fail-fast）。该方案**未实现**用户原意的"友好 fail-fast 消息"。

---

## 1. 证据（2026-10-09 启动实测）

### 1.1 第一次尝试（`${ENV:?}` 语法）

```yaml
# application-prod.yml L29 (撤回前)
url: ${SPRING_DATASOURCE_URL:?PROD 配置错误：SPRING_DATASOURCE_URL 未设置}
```

启动日志：
```
Caused by: java.lang.RuntimeException: Driver com.mysql.cj.jdbc.Driver
claims to not accept jdbcUrl, ?PROD 配置错误：SPRING_DATASOURCE_URL 未设置
```

**根因**：Spring 6.x `PropertyPlaceholderHelper.parseStringValue()` 在 `${prop:default}` 模式中取 `:` 与 `}` 之间的字符串作为 default。`?` 不被识别为 fail-fast 标记符，整个 `?msg` 被当作 default。

### 1.2 第二次尝试（`${ENV:}` 空 default — 已采用）

```yaml
# application-prod.yml L29 (撤回后)
url: ${SPRING_DATASOURCE_URL:}
```

启动日志（profile=prod + 缺 ENV）：
```
***************************
APPLICATION FAILED TO START
***************************
Description: Failed to configure a DataSource: 'url' attribute is not specified
            and no embedded datasource could be configured.
Reason: Failed to determine suitable jdbc url
Action: If you have database settings to be loaded from a particular profile
        you may need to activate it (the profiles prod are currently active).
```

**优点**：
- ✅ 启动确实失败（fail-fast 行为达到）
- ✅ 提示 profile=prod 激活
- ✅ 消息对 Spring 经验丰富的运维可读

**缺点**：
- ⚠️ **未明示"设 SPRING_DATASOURCE_URL"** — 运维要懂 Spring 才知道这个错误对应缺数据库配置
- ⚠️ JwtUtil 校验 fail-fast 时也是类似："secret 不能是 meiyetec/dev/test" 模式（message 友好）

---

## 2. Spring 6.x 占位符语法（事实源）

| 语法 | 行为 | 是否 fail-fast |
|---|---|---|
| `${prop}` | 缺则用空串（实际是原样保留 `${prop}`）| ❌ |
| `${prop:default}` | 缺则用 default | ❌ |
| `${prop:}` | 缺则用空串 | ❌ |
| `${prop:?msg}` | **缺则用 `?msg` 作为 default**（**不是** fail-fast）| ❌ |
| `${prop!msg}` | 不支持，抛异常 | 不可用 |
| SpEL `${T(...)}` | 表达式求值 | 不适用 |

**关键事实**：
- Spring Framework 5.2+ 文档**未承诺** `${prop:?msg}` fail-fast 行为
- 该语法来自更早的 `<context:property-placeholder>` XML 配置（已废弃）
- Spring Boot 3.x 的 `RelaxedPropertyResolver` / `Binder` 走 `PropertyPlaceholderHelper`（标准实现，不支持 `?`）
- 真正支持 fail-fast 的库：Apache Commons Lang `StrSubstitutor`（非 Spring 默认）

**结论**：方案 B+ 中"用 Spring 内置 `${ENV:?}` 替代 ProdConfigValidator" 在 Spring 6.x **不可行**。

---

## 3. 影响与风险

### 3.1 现状（2026-10-09）

- `application-prod.yml` 用 `${ENV:}` 空 default 策略 + 隐式 fail-fast（依赖下游组件）
- 缺关键 ENV 时启动期失败，但**消息不指向具体变量名**（运维定位成本高）
- 8 个 prod 必填项用空 default：SPRING_DATASOURCE_{URL,USERNAME,PASSWORD} / SPRING_DATA_REDIS_HOST / JWT_SECRET / APP_ID_GENERATOR_WORKER_ID / APP_ID_GENERATOR_DATACENTER_ID / PLUGIN_PLUGINS_ROOT
- 其中 JWT_SECRET 由 `JwtUtil` 启动期校验（消息较友好："secret 不能是 meiyetec/dev/test" 模式）

### 3.2 风险

| 场景 | 后果 | 概率 |
|---|---|---|
| 运维部署 prod 时缺 SPRING_DATASOURCE_URL | 启动报 "Failed to determine suitable jdbc url" → 运维翻 Spring 文档定位（10-30 分钟）| 高（首次部署易踩）|
| 运维部署 prod 时缺 PLUGIN_PLUGINS_ROOT | 启动报 "plugins-root directory not found" 或静默用 `./plugins` → 容器/服务模式下插件失效 | 中 |
| 运维部署 prod 时缺 APP_ID_GENERATOR_WORKER_ID | Spring 解析为空串 → 多实例部署主键冲突 → **运行时而非启动期报错** | 高（多实例时）|
| 运维部署 prod 时缺 JWT_SECRET | JwtUtil 启动校验：长度 < 32 字节 → 启动失败（消息较友好）| 低 |

### 3.3 不可逆性

- 无不可逆性 — `application-prod.yml` 已有空 default 策略，补充 EnvironmentPostProcessor 是**叠加**而非替换
- 当前 commit `765609e` 已落 main，本 issue 不阻塞批次 1.7 交付

---

## 4. 修复方案

### 4.1 推荐：EnvironmentPostProcessor SPI（30-50 行）

```java
// backend/src/main/java/com/meiyetec/config/ProdConfigEnvPostProcessor.java
public class ProdConfigEnvPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        // 仅在 prod profile 激活时执行
        if (!Arrays.asList(env.getActiveProfiles()).contains("prod")) return;

        // prod 必填项清单 + 校验规则
        Map<String, String> required = Map.of(
            "SPRING_DATASOURCE_URL", "jdbc:mysql://... 格式",
            "SPRING_DATASOURCE_PASSWORD", "≥ 8 位 + 非 root123456",
            "JWT_SECRET", "base64 ≥ 32 字节 + 非弱值模式",
            "APP_ID_GENERATOR_WORKER_ID", "0-31 整数",
            "PLUGIN_PLUGINS_ROOT", "绝对路径"
            // ...
        );

        List<String> missing = new ArrayList<>();
        for (var entry : required.entrySet()) {
            String v = env.getProperty(entry.getKey());
            if (v == null || v.isBlank() || v.startsWith("PLACEHOLDER_")) {
                missing.add("  ❌ " + entry.getKey() + " — " + entry.getValue());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "========================================\n" +
                "PROD 部署失败：以下必填环境变量缺失或非法：\n" +
                String.join("\n", missing) + "\n" +
                "请参考 docs/prod-env-manifest.md 完整清单\n" +
                "========================================"
            );
        }
    }
}
```

注册（`META-INF/spring.factories` 或 `spring.application.json`）：
```
org.springframework.boot.env.EnvironmentPostProcessor=\
com.meiyetec.config.ProdConfigEnvPostProcessor
```

**优点**：
- 30-50 行，1 个文件 + 1 个配置项
- 启动期 fail-fast + 人类可读消息（按变量名 + 校验规则）
- 不修改 `application-prod.yml`（空 default 策略保留）
- 测试容易（mock Environment）

### 4.2 备选：自定义注解 + BeanPostProcessor（不推荐）

更重，需要自定义注解 + AOP，30+ 文件改动。**不推荐**。

### 4.3 不修：保持现状

- 影响：运维排查成本高（10-30 分钟/次）
- 接受者：可承受的运维团队
- 不阻塞 Gate 3 交付

---

## 5. 实施计划

| 阶段 | 任务 | 工时 |
|---|---|---|
| Phase 1 | 写 `ProdConfigEnvPostProcessor.java`（30-50 行） | 1h |
| Phase 1 | 注册 `META-INF/spring.factories` | 10min |
| Phase 1 | 单测：覆盖 8 个 prod 必填项 + 边界条件 | 1h |
| Phase 2 | `mvn compile` + 启动 prod profile + 验证 fail-fast 消息 | 30min |
| Phase 2 | 守门脚本 04-endpoint-coverage 重跑（期望 0 回归）| 10min |
| **合计** | | **2.5-3h** |

---

## 6. 关联

| 关联 | 描述 |
|---|---|
| **commit `765609e`** | 批次 1.7 生产配置（隐式 fail-fast 策略已落）|
| **`application-prod.yml`** | 8 个空 default 占位（依赖本 issue 的显式校验）|
| **`docs/prod-env-manifest.md`** | 完整 prod ENV 清单（必填项定义来源）|
| **I097** | 同类问题：commit 设计缺陷（流程类，P3）|
| **批次 1.7 报告** | 步骤 5 启动验证（隐式 fail-fast 行为证据）|

---

## 7. 验收方式

- [ ] `ProdConfigEnvPostProcessor` 在 prod profile 激活时执行
- [ ] 缺任一必填 ENV → 启动期抛 `IllegalStateException` + 完整清单
- [ ] 全部 ENV 正确 → 启动成功（不阻塞）
- [ ] dev / test profile 不触发（不抛异常）
- [ ] 单测覆盖 8 个 prod 必填项 + 边界（空 / PLACEHOLDER_ / 弱值）
- [ ] `mvn -DskipTests compile` EXIT=0
- [ ] 启动 prod profile 验证 fail-fast 消息（5s 内出现）
- [ ] 守门脚本 04-endpoint-coverage 0 回归

---

## 8. 状态

| 维度 | 值 |
|---|---|
| **当前** | OPEN |
| **优先级** | P3（运维排查成本，非阻塞）|
| **解决时间** | Gate 3 后续批次 / Post-Audit 三期 |
| **关联 commit** | `765609e`（本 issue 触发批次）|
| **关联报告** | `audit/post-audit/phase-2.34-I088-3-report.md` 流程规范参考 |
