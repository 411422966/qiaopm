# F013 — CRM Adapter 接口契约（Static-Only）

> **Phase**：5B Runtime Verification
> **Feature ID**：F013
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. 接口契约 L0 验证

文件：`backend/src/main/java/com/meiyetec/crm/CrmAdapter.java`

| 元素 | 行号 | 类型 | 验证 |
|---|---|---|---|
| `platformCode()` | 26 | abstract method | ✅ |
| `authenticate(CrmIntegration)` | 31 | abstract method | ✅ |
| `fetchContactsIncremental(CrmIntegration, SyncCursor)` | 39 | abstract method | ✅ |
| `upsertContact(CrmIntegration, Map)` | 44 | abstract method | ✅ |
| `healthCheck(CrmIntegration)` | 49 | abstract method | ✅ |
| `AuthResult` DTO | 57 | class | ✅ |
| `SyncCursor` DTO | 79 | class（含 `knownExternalIds`）| ✅ |
| `ExternalContact` DTO | 100 | class | ✅ |
| `PushResult` DTO | 116 | class | ✅ |
| `HealthStatus` DTO | 132 | class | ✅ |

→ **5 方法 + 5 DTO 完整声明**（plan.md §一.2 F013 描述一致）

## 2. 实现覆盖 L0 验证

| Adapter | 文件 | platformCode | authenticate | fetchContactsIncremental | upsertContact | healthCheck | @Component |
|---|---|---|---|---|---|---|---|
| **Salesforce** | SalesforceCrmAdapter.java | SALESFORCE ✓ (line 61) | ✓ (line 70) | ✓ (line 229) | ✓ (line 307) | ✓ (line 333) | — |
| **HubSpot** | HubSpotCrmAdapter.java | HUBSPOT ✓ (line 71) | ✓ (line 122) | ✓ (line 177) | ✓ (line 276) | ✓ (line 321) | — |
| **Zoho** | ZohoCrmAdapter.java | ZOHO ✓ (line 72) | ✓ (line 122) | ✓ (line 191) | ✓ (line 272) | ✓ (line 319) | — |
| **Mock** | MockCrmAdapter.java | MOCK ✓ (line 38) | ✓ (line 43) | ✓ (line 55) | ✓ (line 95) | ✓ (line 104) | — |

→ **4 个 adapter 全部完整实现 5 方法** — 无任何 stub

## 3. Router 自动注入 + fallback

文件：`backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java`

```java
@Component
public class CrmAdapterRouter {
    private final Map<String, CrmAdapter> adapters = new HashMap<>();

    @Autowired
    public CrmAdapterRouter(List<CrmAdapter> adapterList) {
        for (CrmAdapter adapter : adapterList) {
            adapters.put(adapter.platformCode().toUpperCase(), adapter);
        }
    }

    public CrmAdapter getAdapter(String platform) {
        if (platform == null) return getAdapter("MOCK");
        CrmAdapter a = adapters.get(platform.toUpperCase());
        if (a == null) {
            // fallback to mock for unknown platforms in dev
            return adapters.get("MOCK");
        }
        return a;
    }
    // hasAdapter / listPlatforms ...
}
```

→ **自动注入 4 个 adapter + unknown platform fallback MOCK**（无 NPE）

## 4. ⚠️ 与 EXT-24 SoT 冲突（I001 实证）

| 维度 | EXT-24 §0.6 / §5 SoT 写 | 实测代码 | 冲突 |
|---|---|---|---|
| HUBSPOT adapter | "未实现（前端可选会 500）" | HubSpotCrmAdapter.java 完整 5 方法 | ✅ SoT 错误 |
| ZOHO adapter | "未实现（前端可选会 500）" | ZohoCrmAdapter.java 完整 5 方法 | ✅ SoT 错误 |
| Mock fallback | "@ConditionalOnMissingBean(name=...)" | Router 自动注入全部 + unknown → MOCK fallback | ✅ SoT 错误 |
| NPE | "router.getAdapter 返回 null → NPE" | 代码 fallback to MOCK，无 NPE | ✅ SoT 错误 |

→ F016/F017/F018 = **CONFLICT**（按 plan.md F016/F017/F018 行）

## 5. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| 接口契约完整性 | ✅ 5 方法 + 5 DTO |
| Salesforce 实现 | ✅ 完整 |
| HubSpot 实现 | ✅ 完整（与 SoT 冲突 — SoT 错）|
| Zoho 实现 | ✅ 完整（与 SoT 冲突 — SoT 错）|
| Mock 实现 | ✅ 完整 |
| Router fallback | ✅ 自动注入 + MOCK fallback（无 NPE）|
| 与 EXT-24 SoT | ❌ **4 处冲突**（I001 实证）|

### **最终状态**：✅ **VERIFIED**（接口契约层）

（接口层静态分析完全通过；与 SoT 的冲突是 I001 范畴，由 F016/F017/F018 单独标记）

## 6. 5B 继承式策略影响

- F013 = VERIFIED（接口契约层）
- 不被继承
- **F016/F017/F018** 应引用本文件作为 "CONFLICT (per I001, 5B F013 confirmed)" 的实证依据

## 7. 关联

- `audit/issues/issue-register.md` §三 I001（SoT 漂移）
- `audit/issues/I001-ext24-sot-conflict.md`（I001 详细）
- `audit/runtime/plan.md` §一.2 F013
- `audit/runtime/report-5A.md` §0.3 关键发现 5
