# F015 — Mock CRM 适配器（Static-Only + 激活条件冲突）

> **Phase**：5B Runtime Verification
> **Feature ID**：F015
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. 静态分析 L0

文件：`backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java`

### 1.1 类结构

```java
@Slf4j
@Component
@ConditionalOnMissingBean(name = "salesforceCrmAdapter")
public class MockCrmAdapter implements CrmAdapter {
    private static final List<ExternalContact> SAMPLE = List.of(
            contact("003AX000001", "张", "三", ...),
            contact("003AX000002", "李", "四", ...),
            contact("003AX000003", "王", "五", ...)
    );
    private final Deque<ExternalContact> rollingBuffer = new ConcurrentLinkedDeque<>();

    @Override public String platformCode() { return "MOCK"; }
    @Override public AuthResult authenticate(CrmIntegration config) { ... Thread.sleep(50); ... return mockToken; }
    @Override public List<ExternalContact> fetchContactsIncremental(...) { ... rollingBuffer.addFirst + filter known; }
    @Override public PushResult upsertContact(...) { ... Thread.sleep(50); return mock externalId; }
    @Override public HealthStatus healthCheck(...) { return MOCK_OK; }
}
```

### 1.2 行为特性

| 维度 | 实现 |
|---|---|
| 样本数 | 固定 3 条（张/李/王）|
| 滚动缓冲 | ConcurrentLinkedDeque，每调用 addFirst 1 条，最大 50 条 |
| known-ids 差集 | ✅ `if (isFirst \|\| !known.contains(c.externalId))` |
| 延迟模拟 | 50ms (auth) / 100ms (fetch) / 50ms (upsert)|
| Token 模拟 | `MOCK-TOKEN-{UUID}` |
| healthCheck | `healthy=true, stage="MOCK_OK"` |

✅ **完全符合 EXT-24 §0.3 known-ids 差集去重 + EXT-15 Mock fallback 语义**

## 2. ⚠️ 关键发现：激活条件冲突

### 2.1 Spring Bean 注册条件

```java
@Component                                                    // 默认注册
@ConditionalOnMissingBean(name = "salesforceCrmAdapter")     // 缺 SF bean 时才注册
public class MockCrmAdapter implements CrmAdapter { ... }
```

```java
@Component                                                    // 默认注册
public class SalesforceCrmAdapter implements CrmAdapter { ... }
```

### 2.2 实际激活结果

| 场景 | SalesforceCrmAdapter | MockCrmAdapter | Router 注入 |
|---|---|---|---|
| **当前（默认）** | ✅ 注册 | ❌ 不注册（因 SF 已存在）| 3 个（SF + HubSpot + Zoho），**无 Mock** |
| 关闭 SF | ❌ 不注册 | ✅ 注册 | 4 个（含 Mock）|

### 2.3 重要后果

- **当前生产环境默认无 Mock adapter**！
- Router.getAdapter("MOCK") → 返回 null → fallback 到 `adapters.get("MOCK")` = null → **NPE**！

### 2.4 代码层确认

```java
// CrmAdapterRouter.java:28-36
public CrmAdapter getAdapter(String platform) {
    if (platform == null) return getAdapter("MOCK");
    CrmAdapter a = adapters.get(platform.toUpperCase());
    if (a == null) {
        return adapters.get("MOCK");   // ← MOCK 没注册时，这里返回 null → NPE
    }
    return a;
}
```

→ **当 Salesforce 已注册 + Mock 未注册 + 平台=MOCK 时 = NPE**

## 3. EXT-24 §5 vs 实测冲突（I001）

| EXT-24 §5 SoT | 实测代码 |
|---|---|
| "Mock 适配器首次同步返回 3 条样本 + N 条 rollingBuffer" | ✅ 代码逻辑存在（**但 Mock 不激活，无法运行**）|
| "@ConditionalOnMissingBean(name = "salesforceCrmAdapter") → Mock 是 fallback" | ⚠ 条件存在但默认不激活 |

## 4. I002 阻塞（再次出现）

即便 Mock 激活，仍受 I002 阻塞：
- `tick:47` `selectList(...sync_enabled=1)` → I002 → catch 吞 → return
- `syncNow:88` `selectById(id)` → I002 → 抛错
- → **Mock 永远不被 e2e 调用**

## 5. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| Mock 适配器实现 | ✅ 完整（5 方法 + 差集去重 + rollingBuffer）|
| 与 EXT-24 §0.3 一致性 | ✅ 完全一致 |
| 当前环境 Mock 是否激活 | ❌ **默认不激活**（@ConditionalOnMissingBean 触发 SF 存在时跳过 Mock）|
| Router fallback 安全性 | ❌ **当 platform=MOCK 且 Mock 未注册 → NPE** |
| e2e 测试可执行性 | ❌ I002 阻塞 tick/syncNow |
| 与 SoT 描述一致性 | ⚠ §0.6 SoT 写"Mock fallback" — **当前不成立** |

### **最终状态**：⚠ **PARTIAL-NON-BLOCKER**

（实现完整但激活条件错误 + SoT 冲突）

## 6. ⚠️ 新登记建议

**I012 — Mock 适配器激活条件冲突**

| 维度 | 内容 |
|---|---|
| 类别 | 配置 / SoT 漂移 |
| 严重度 | 🟠 中-高（生产环境 platform=MOCK 集成会 NPE）|
| 范围 | F015 + F018 + F012（known-ids 链路）|
| 触发条件 | SalesforceCrmAdapter 启用 + 任意集成 platform=MOCK |
| 当前影响 | 当前环境 F015 e2e 不可达 |
| 修复建议 | (a) Mock 改用 `@Profile("dev")` 或 `@ConditionalOnProperty` 显式启用；(b) Router.getAdapter 在 null fallback 时**抛 BizCode.NOT_FOUND** 而非返回 null |

## 7. 5B 继承式策略影响

- F015 = PARTIAL-NON-BLOCKER（实现完整 + 激活冲突）
- 不被继承
- **F018 Router 分发（CONFLICT）** 应引用本文件作为实证：Mock fallback 不安全

## 8. 关联

- `audit/issues/I001-ext24-sot-conflict.md`（SoT 漂移）
- `audit/issues/I012-mock-activation-conflict.md`（建议新登记 — **本次审计新发现**）
- `audit/runtime/plan.md` §一.2 F015 + §三 关键负向证据
- `audit/evidence/F013-adapter-contract/result.md`（F013 接口层验证 Mock 实现完整）
- `audit/evidence/F018-router-fallback.md`（CONFLICT 引用）
