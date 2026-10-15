# F018 — Router 分发 + Mock fallback（CONFLICT per I001, 5B F013/F015 confirmed）

> **Phase**：5B Runtime Verification
> **Feature ID**：F018
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **策略**：CONFLICT-只读（per I001）→ 5B F013 + F015 已实证

---

## 1. CONFLICT 结论

**F018 = CONFLICT (per I001, 5B F013 + F015 confirmed)**

EXT-24 §5 SoT 写："router.getAdapter 返回 null → NPE"

实测：
- Router 自动注入所有 `@Component` adapter（5B F013）
- Router.getAdapter 包含 `fallback to adapters.get("MOCK")`（CrmAdapterRouter.java:31-35）
- 但 **`MockCrmAdapter` 有 `@ConditionalOnMissingBean(name = "salesforceCrmAdapter")`** → 当前环境默认不激活（5B F015）
- → 实际 NPE 风险：**当 platform=MOCK 且 Mock 未注册 → fallback 仍返回 null → NPE**

| 维度 | SoT 描述 | 实测 |
|---|---|---|
| Router 自动注入 | 未描述 | ✅ 注入 4 个（SF/HubSpot/Zoho + Mock 条件性）|
| Mock fallback | "@ConditionalOnMissingBean → Mock fallback" | ⚠ 条件性激活（SF 存在时 Mock 不激活）|
| NPE 风险 | "router.getAdapter 返回 null → NPE" | ⚠ **当前环境 platform=MOCK 会触发 NPE**（SF 默认存在 + Mock 不激活）|

## 2. 5B F013 + F015 引用证据

### 2.1 F013 引用

`audit/evidence/F013-adapter-contract/result.md` §3：

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

### 2.2 F015 引用

`audit/evidence/F015-mock-adapter/result.md` §2：

- MockCrmAdapter 有 `@Component + @ConditionalOnMissingBean(name = "salesforceCrmAdapter")`
- SalesforceCrmAdapter 默认注册 → Mock 不激活
- 当前 Router 注入 3 个（SF + HubSpot + Zoho），**无 Mock**
- Router.getAdapter("MOCK") → null → NPE

## 3. ⚠️ 新发现

**I012 — Mock 适配器激活条件冲突**

| 维度 | 内容 |
|---|---|
| 类别 | 配置 / SoT 漂移 |
| 严重度 | 🟠 中-高 |
| 范围 | F015 + F018 + F012（known-ids 链路）|
| 当前影响 | 当前环境 platform=MOCK 集成会 NPE |
| 修复建议 | (a) Mock 改用 `@Profile("dev")` 或 `@ConditionalOnProperty` 显式启用；(b) Router.getAdapter 在 null fallback 时**抛 BizCode.NOT_FOUND** 而非返回 null |

完整：`audit/evidence/F015-mock-adapter/result.md` §6

## 4. ✅ 最终状态

**⚠ CONFLICT (per I001, 5B F013 + F015 confirmed) + 🆕 I012 激活冲突**

修复路径：
1. 短期：生产环境禁用 platform=MOCK 集成（或保留 SF 禁用 mock 显式启用）
2. 长期：Mock 激活条件显式化（dev profile 或 @ConditionalOnProperty）+ Router fallback 抛异常
