# F016 — HubSpot 适配器（CONFLICT per I001, 5B F013 confirmed）

> **Phase**：5B Runtime Verification
> **Feature ID**：F016
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **策略**：CONFLICT-只读（per I001）→ 5B F013 已实证

---

## 1. CONFLICT 结论

**F016 = CONFLICT (per I001, 5B F013 confirmed)**

EXT-24 §0.6 SoT 写："`HUBSPOT` **未实现**（前端可选会 500）"

实测：`HubSpotCrmAdapter.java` 完整实现 5 方法（5B F013 已确认）

| 维度 | SoT 描述 | 实测代码 |
|---|---|---|
| HubSpot adapter | 未实现 | ✅ 完整（platformCode/authenticate/fetchContacts/upsert/health）|
| 500 风险 | "前端可选会 500" | ❌ 不存在（Router 自动注入）|

## 2. 5B F013 引用证据

`audit/evidence/F013-adapter-contract/result.md`：

```
| **HubSpot** | HubSpotCrmAdapter.java | HUBSPOT ✓ (line 71) | ✓ (line 122) | ✓ (line 177) | ✓ (line 276) | ✓ (line 321) | — |
```

→ 5/5 接口方法完整实现，5B 实证 I001 SoT 漂移。

## 3. ✅ 最终状态

**⚠ CONFLICT (per I001, 5B F013 confirmed)**

修复路径：更新 `ext-24-crm-integration.mdc` §0.6 修正 SoT（用户决策 D2 = 不修，仅登记）
