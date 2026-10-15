# Phase 2.28 — I005-C4 Fix Report

> **修复时刻**：2026-09-30 22:53 (UTC+8)
> **Commit**：`1dff3f6`
> **执行人**：Cursor Assistant
> **关联 Issue**：I005-C4（CRM 新建集成表单字段 + 后端校验）
> **优先级**：P3（PARTIAL-NON-BLOCKER，UX/数据完整性）
> **I005 状态**：✅ CLOSED（C1+C2+C4 全部 FIXED）
> **新登记 Issue**：I089（privateKey 编辑缺失）
> **验收标准**：vue-tsc + mvn compile 均 Exit 0

---

## 1. 问题描述

### 1.1 根因（改前）

**前端表单（4 字段，零校验）**：
```vue
<el-dialog v-model="addDialog" title="新建 CRM 集成" width="500px">
  <el-form :model="addForm" label-width="120px">
    <!-- 只有 platform / shopName / syncDirection / autoSync -->
  </el-form>
</el-dialog>
```

**前端 addForm 初始值（缺全部凭据）**：
```ts
{ platform: 'SALESFORCE', shopName: 'Demo 店铺', syncDirection: 'PUSH', autoSync: 1 }
```

**后端 create 无校验（L33-40）**：
```java
if (i.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
if (i.getStatus() == null) i.setStatus("ACTIVE");
i.setTenantId(tenantId);
i.setId(null);
integrationMapper.insert(i);   // 13 字段全部直 insert
```

### 1.2 实际后果

- SALESFORCE 集成 `clientId / username / loginUrl / privateKey` 4 项**全部 NULL** → `SalesforceCrmAdapter.authenticate` 立即失败
- HUBSPOT / ZOHO 集成 `clientId / clientSecret` NULL → 同上
- 字段映射 JSON 无法在前端管理，只能 SQL 直写 `crm_integration.objectMappings`
- 同步窗口无法配置 → 全部走后端默认 30
- 任意 `platform="FOO"` 字符串都能入库
- 任意 `objectMappings="not json"` 也能入库（直到下次 fetchContacts 才报错）

---

## 2. 修复内容

### 2.1 前端 `index.vue`（+108 行）

**Dialog 尺寸**：500×... → 700×620（maxHeight 620 + content scroll 540）

**Label 重命名**：`店铺名称` → `名称`

**新增 7 字段（按平台条件渲染）**：

| # | 字段 | 类型 | 平台可见性 | 说明 |
|---|---|---|---|---|
| 1 | `clientId` | input | SALESFORCE / HUBSPOT / ZOHO | Connected App Consumer Key / OAuth2 client_id |
| 2 | `clientSecret` | input type=password | HUBSPOT / ZOHO | OAuth2 client_secret |
| 3 | `username` | input | SALESFORCE | JWT subject |
| 4 | `loginUrl` | input | SALESFORCE | 默认 `https://login.salesforce.com` |
| 5 | `privateKey` | textarea 3-8 rows | SALESFORCE | ⚠️ **保存后不回显**（WRITE_ONLY） |
| 6 | `syncWindowMinutes` | el-input-number 1-1440 step=5 | 全平台 | 默认 30 |
| 7 | `objectMappings` | textarea 4-10 rows + JSON 实时校验 | 全平台 | 留空 → 后端降级 DEFAULT_FIELD_MAP |

**新增脚本逻辑**：
- `onPlatformChange()`：切换平台清空不适用字段（clientSecret / privateKey / username / loginUrl 重置）
- `watch(() => addForm.objectMappings)`：实时 `JSON.parse` 校验，失败时 `objectMappingsError.value = e.message`
- `doAdd()`：保存前二次拦截 JSON 错误

### 2.2 后端 `CrmIntegrationService.create`（+21 行）

```java
// I005-C4：platform 枚举校验（与 EXT-24 §0.6 / MockAdapter @ConditionalOnMissingBean fallback 对齐）
private static final Set<String> ALLOWED_PLATFORMS =
    Set.of("SALESFORCE", "HUBSPOT", "ZOHO", "MOCK");

// 三重校验
if (!ALLOWED_PLATFORMS.contains(i.getPlatform()))           // (c) 平台枚举
    return Result.error(BizCode.BAD_REQUEST, "platform 仅支持 ...");
if (i.getSyncWindowMinutes() == null) i.setSyncWindowMinutes(30);  // (b) 默认值
if (i.getObjectMappings() != null && !i.getObjectMappings().isEmpty()) {
    try {
        ObjectMapperHolder.MAPPER.readTree(i.getObjectMappings());
    } catch (JsonProcessingException e) {  // (a) JSON 校验
        return Result.error(BizCode.BAD_REQUEST, "objectMappings JSON 格式错误：" + e.getOriginalMessage());
    } catch (Exception e) {
        return Result.error(BizCode.BAD_REQUEST, "objectMappings JSON 解析异常：" + e.getMessage());
    }
}
```

**校验语义**：
| 输入 | 结果 |
|---|---|
| `platform ∈ {SALESFORCE, HUBSPOT, ZOHO, MOCK}` | ✅ 通过 |
| `platform = "FOO"` | ❌ `BAD_REQUEST` |
| `objectMappings = '{"a":1}'` | ✅ 通过 |
| `objectMappings = 'not json'` | ❌ `BAD_REQUEST` + `getOriginalMessage` 去位置信息 |
| `objectMappings = ""` 或 null | ✅ 通过（后端 parseObjectMappings 降级 DEFAULT_FIELD_MAP） |
| `syncWindowMinutes = null` | ✅ → 默认 30 |

---

## 3. 决策记录（用户拍板 7 项）

| 决策 | 用户选择 | 实施 |
|---|---|---|
| D1 | privateKey textarea + 不回显文案 | ✅ textarea + ⚠️ 红字提示 |
| D2 | objectMappings JSON 实时校验 | ✅ watch + 红字 + doAdd 二次拦截 |
| D3 | ❌ 不加 syncSchedule UI | ✅ 跳过（I013 僵尸字段保留） |
| D4 | 无需（同 D3） | ✅ |
| D5 | 平台条件渲染 | ✅ SALESFORCE / HUBSPOT\|ZOHO 三组 |
| D6 | 后端 (a) JSON + (b) 默认 30 + (c) 平台枚举 | ✅ 三重 |
| D7 | shopName label 改"名称" | ✅ |

**用户额外追加**：
- ✅ R6：MOCK 不加进前端 select（仅后端接受）
- ✅ R2：apiBaseUrl / portalId 暂不加 UI
- ✅ R1：登记新 Issue **I089**（privateKey 编辑缺失）

---

## 4. 验证

| 检查项 | 命令 | 结果 |
|---|---|---|
| TypeScript 类型检查 | `vue-tsc --noEmit` | ✅ Exit 0 |
| 后端编译 | `mvn -q compile -DskipTests` | ✅ Exit 0 |
| Staged 文件数 | `git status --short` | 仅 2 个 `M `（crm-integration/index.vue + CrmIntegrationService.java） |
| 946 EOL 漂移污染 | F025/F027 等 spec + e2e-result.md | ✅ 全部 unstaged，未混入 |
| 真业务改动行数 | `git diff --cached` | -8 行 + 121 行（含 +108 表单 + 13 后端） |

---

## 5. 改动文件清单

| # | 文件 | 改动类型 | 行变化 |
|---|---|---|---|
| 1 | `frontend/meiyetec-web/src/views/crm-integration/index.vue` | Dialog 700×620 + 7 字段 + 平台条件 + JSON 校验 | +108 -8 |
| 2 | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java` | create 加 (a)/(b)/(c) 三重校验 + ALLOWED_PLATFORMS 常量 | +21 -0 |
| 3 | `audit/issues/issue-register.md` | I005 CLOSED + 5.1.3 C4 FIXED + 新增 I089 | — |
| 4 | `audit/post-audit/phase-2.28-I005-C4-report.md` | 本报告 | 新建 |

---

## 6. I005 收尾

| C# | 描述 | 状态 | Commit |
|---|---|---|---|
| **C1** | toggle enum 统一 | ✅ FIXED | `d2dd4d3` |
| **C2** | syncNow force + loading + skipped UI | ✅ FIXED | `67d9b6a` |
| **C4** | 新建表单 7 字段 + JSON 校验 + 平台条件 | ✅ FIXED | `1dff3f6`（本轮） |

**I005 整体 → ✅ CLOSED**

---

## 7. 新登记 I089

| 字段 | 内容 |
|---|---|
| **Issue ID** | **I089** 🆕 |
| **类型** | UX / 编辑能力缺失 |
| **描述** | `entity/CrmIntegration.privateKey` 标 `@JsonProperty(WRITE_ONLY)` → GET /list 不返回 → 编辑集成时**无法回显也无法修改**；前端新建表单 I005-C4 已加 textarea 输入，但**编辑路径无对应 UI** |
| **优先级** | P3 PARTIAL-NON-BLOCKER |
| **影响 Feature** | F005 |
| **候选方案** | ①「重新上传」覆盖模式（最简）<br>② 拆 read 端点（更安全）<br>③ 改 READ_ONLY→READ_WRITE + 前端掩码 |
| **Fix Phase** | Post-Audit |

---

## 8. 未涉及范围

| 项目 | 状态 |
|---|---|
| I089 privateKey 编辑缺失 | 🆕 OPEN（本轮登记，未修） |
| I013 syncSchedule 僵尸字段 | OPEN（D3 跳过） |
| apiBaseUrl / portalId UI | OPEN（R2 跳过） |
| I008 dashboard catch swallow | OPEN（下一项） |
| I011 update select-after-write | OPEN（下一项） |
| 其他 CRM 漂移 | OPEN |

---

## 9. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`1dff3f6`
- **Fix 时间**：2026-09-30 22:53 (UTC+8)
- **Type Check**：✅ vue-tsc --noEmit exit 0
- **Backend Compile**：✅ mvn compile exit 0
- **EOL 政策**：✅ 仅精确 add 2 个业务文件；其他 EOL 文件留在 working tree
- **I005 状态**：✅ CLOSED（C1+C2+C4 全 FIXED）
- **新 Issue**：✅ I089 已登记