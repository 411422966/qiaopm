# Phase 2.29 — I028 Ecommerce 子项 Fix Report

> **修复时刻**：2026-09-30 23:08 (UTC+8)
> **Commit**：`3989655`
> **执行人**：Cursor Assistant
> **关联 Issue**：I028（D12 跨租户 UI 漂移）
> **优先级**：P3 PARTIAL-NON-BLOCKER（前端 UI / Issue 级 DB 漂移）
> **修复范围**：仅 ecommerce 子项（google-maps 子项已 PASS 5A+5B，无需修）
> **新登记 Issue**：I090（tenantId 跨租户）+ I091（历史 secrets 明文数据清洗）
> **验收标准**：vue-tsc + mvn compile 均 Exit 0

---

## 1. 背景与 I028 真实范围

### 1.1 I028 子项拆解

| 子项 | 状态 | 证据 |
|---|---|---|
| **google-maps enablePlaceDetails el-switch** | ✅ **已 PASS 5A+5B** | commit `e25f117`（I031）+ commit `6abc30e`（I036）；前端 `index.vue:11-25`（开关+警告）+ `index.vue:80-83`（测试 tab 联动）+ 后端 `GoogleMapsService.java:79-84`（已返回 enablePlaceDetails） |
| **ecommerce 5 字段缺失**（appKey/appSecret/appId/kdtId/signSecret） | ❌ → ✅ **本轮修** | 前端 `views/ecommerce/index.vue:60-77` 仅 3 字段；后端 entity 全有；后端 createPlatform 零校验 |
| **EcommerceService tenantId=0L 写死**（D6 衍生） | 🆕 I090 P1 OPEN | `EcommerceService.java:64` `p.setTenantId(0L)` 写死 → 跨租户隔离完全没做 |
| **ecommerce_platform 历史明文 secrets**（D8 衍生） | 🆕 I091 P1 OPEN | WRITE_ONLY 仅阻止序列化输出，已存在 DB 的明文 secrets 仍在原位 |

---

## 2. 问题描述（改前）

### 2.1 前端 dialog（L60-77）

```vue
<el-dialog v-model="addDialog" title="添加电商平台" width="500px">
  <el-form :model="addForm" label-width="120px">
    <el-form-item label="平台">           <!-- 仅 4 选项：WEIDIAN/YOUZAN/TAOBAO/DOUYIN -->
    <el-form-item label="店铺名称"> <el-input v-model="addForm.shopName" />
    <el-form-item label="店铺ID">     <el-input v-model="addForm.shopId" />
    <!-- 缺 appKey / appSecret / appId / kdtId / signSecret -->
  </el-form>
</el-dialog>
```

**addForm 初始值**（缺全部凭据）：
```ts
{ platform: 'WEIDIAN', shopName: '微店商城', shopId: 'WD' + Date.now() }
```

### 2.2 后端 entity（13 业务字段）

| # | 字段 | 类型 | 表单缺 | 修复后 |
|---|---|---|---|---|
| 1 | platform | String | ✅ | ✅ |
| 2 | shopName | String | ✅ | ✅ |
| 3 | shopId | String | ✅ | ✅ |
| 4 | appKey | String | ❌ | ✅ 加 |
| 5 | appSecret | String | ❌ | ✅ 加（WRITE_ONLY） |
| 6 | appId | String | ❌ | ✅ 加（YOUZAN） |
| 7 | kdtId | String | ❌ | ✅ 加（YOUZAN） |
| 8 | signSecret | String | ❌ | ✅ 加（YOUZAN + WRITE_ONLY） |

### 2.3 后端 createPlatform（L53-61）零校验

```java
public Result<EcommercePlatform> createPlatform(@RequestBody EcommercePlatform p) {
    if (p.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
    if (p.getStatus() == null) p.setStatus("ACTIVE");
    p.setTenantId(0L);                    // ⚠️ 跨租户隔离未做（I090）
    p.setId(null);
    platformMapper.insert(p);
    return Result.success("已添加", p);
}
```

### 2.4 实际后果

- **YOUZAN 对接失效**：无 appId / kdtId / signSecret → webhook 验签失败、消息签名错误
- **WEIDIAN/TAOBAO/DOUYAN 对接失效**：无 appKey / appSecret → OAuth2 client_credentials 拿不到 accessToken
- **绕过前端可直接 POST**：任意 `platform="FOO"` 字符串都能入库
- **GET /platform/list 明文泄露 secrets**：entity 无 WRITE_ONLY → 列表返回所有 appSecret/signSecret

---

## 3. 修复内容

### 3.1 前端 `views/ecommerce/index.vue`（+126 -92 行）

**Dialog 尺寸**：500×... → 700×620（maxHeight 620 + content scroll 540）

**Label 宽度**：120 → 140

**平台 select 新增 MOCK 选项**（对齐 CRM）：
```
微店 / 有赞 / 淘宝 / 抖店 / MOCK（内部 dev）
```

**新增 5 字段（按平台条件渲染）**：

| # | 字段 | 类型 | 平台可见性 | 说明 |
|---|---|---|---|---|
| 1 | `appKey` | input | 全平台 | OAuth2 client_id |
| 2 | `appSecret` | input type=password + show-password | 全平台 | ⚠️ **保存后不回显** |
| 3 | `appId` | input | 仅 YOUZAN | 有赞开放平台 AppID |
| 4 | `kdtId` | input | 仅 YOUZAN | 店铺 kdtId |
| 5 | `signSecret` | input type=password + show-password | 仅 YOUZAN | ⚠️ **保存后不回显**，webhook 验签 |

**新增脚本逻辑**：
- `onPlatformChange()`：切换平台清空不适用字段（YOUZAN → WEIDIAN 后 signSecret 残留清空）
- `addForm` 初始值含 5 字段默认空

### 3.2 后端 `EcommerceService.createPlatform`（+8 行）

```java
// I028：platform 枚举校验（与 MockEcommerceAdapter @ConditionalOnMissingBean fallback 对齐）
private static final Set<String> ALLOWED_PLATFORMS =
    Set.of("WEIDIAN", "YOUZAN", "TAOBAO", "DOUYIN", "MOCK");

if (!ALLOWED_PLATFORMS.contains(p.getPlatform())) {
    return Result.error(BizCode.BAD_REQUEST,
        "platform 仅支持 " + ALLOWED_PLATFORMS + "，当前：" + p.getPlatform());
}
```

**未动**：`p.setTenantId(0L)` —— 用户决策 D6，单独立项 I090。

### 3.3 后端 `EcommercePlatform`（+6 行）

```java
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String appSecret;        // I028：保存可写 / GET 不回显

@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String signSecret;       // I028：同上（webhook 签名密钥）
```

**副作用**：历史数据清洗登记 I091（P1 OPEN）。

---

## 4. 决策记录（用户拍板）

| # | 决策 | 用户选择 | 实施 |
|---|---|---|---|
| **Q1** | I028 真实未修部分 | 选 A：跳过 google-maps（已完整），进 ecommerce | ✅ |
| **D2** | 字段范围 | 5 字段（appKey/appSecret/appId/kdtId/signSecret） | ✅ |
| **D3** | 平台条件 | YOUZAN 才显示 appId/kdtId/signSecret | ✅ |
| **D4** | appSecret/signSecret 渲染 | password + show-password | ✅ |
| **D5** | 后端校验 | 仅加 platform 枚举 | ✅ |
| **D6** | tenantId=0L | 不修 → 单独登记 I090 | ✅ 登记 |
| **D8** | secrets WRITE_ONLY | appSecret + signSecret 都加 | ✅ |
| **追加** | 新增 I090 + I091 | tenantId + 历史 secrets | ✅ |

---

## 5. 验证

| 检查项 | 命令 | 结果 |
|---|---|---|
| TypeScript 类型检查 | `vue-tsc --noEmit` | ✅ Exit 0 |
| 后端编译 | `mvn -q compile -DskipTests` | ✅ Exit 0 |
| Staged 文件数 | `git status --short` | 仅 3 个 `M `（ecommerce/index.vue + EcommerceService.java + EcommercePlatform.java） |
| 946 EOL 漂移污染 | F079 等 spec + e2e-result.md | ✅ 全部 unstaged，未混入 |
| 真业务改动行数 | `git diff --cached` | -92 行 + 140 行（净 +48） |

---

## 6. 改动文件清单

| # | 文件 | 改动类型 | 行变化 |
|---|---|---|---|
| 1 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | Dialog 700×620 + 5 字段 + MOCK + 平台条件 + onPlatformChange | +126 -92 |
| 2 | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | createPlatform 加 ALLOWED_PLATFORMS + 枚举校验 | +8 -0 |
| 3 | `backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java` | appSecret + signSecret 加 `@JsonProperty(WRITE_ONLY)` + javadoc | +6 -0 |
| 4 | `audit/issues/issue-register.md` | I028 拆分子项 FIXED + 新增 I090 + I091 + 总览表更新 | — |
| 5 | `audit/post-audit/phase-2.29-I028-report.md` | 本报告 | 新建 |

---

## 7. I028 整体收尾

| 子项 | 描述 | 状态 | Commit |
|---|---|---|---|
| **google-maps enablePlaceDetails** | EXT-22 计费开关 | ✅ PASS 5A+5B | `e25f117` + `6abc30e` |
| **google-maps API Key 遮蔽** | I036 遮蔽处理 | ✅ FIXED | `6abc30e` |
| **ecommerce 5 字段** | 凭据 UI + 后端校验 + WRITE_ONLY | ✅ FIXED（本轮） | `3989655` |
| **EcommerceService tenantId=0L** | 跨租户隔离 | 🆕 **I090 P1 OPEN** | — |
| **历史明文 secrets 清洗** | DB 已存明文 | 🆕 **I091 P1 OPEN** | — |

**I028 拆分结论**：
- ✅ **前端 UI 部分 CLOSED**（5A+5B PASS + 本轮 5 字段）
- ⚠️ **后端数据安全部分** I090 + I091 仍需单独处理

---

## 8. 新登记 Issue

### I090 — EcommerceService tenantId=0L 写死跨租户

| 字段 | 内容 |
|---|---|
| **Issue ID** | **I090** 🆕 |
| **类型** | 跨租户隔离 / P1 PARTIAL-BLOCKER |
| **描述** | `EcommerceService.java:64` `p.setTenantId(0L)` 写死 → 所有 `ecommerce_platform` 行 tenant_id=0 → 跨租户隔离完全没做；`listPlatforms` 也无 tenantId 过滤；`POST /ecommerce/platform` 创建后所有租户共享 |
| **影响 Feature** | F066 / F075 / F076 |
| **修复方案** | ① `createPlatform` 改用 `TenantContextHolder.get().getTenantId()`<br>② `listPlatforms` 加 `eq(tenantId)` 过滤<br>③ `updatePlatform` 加 `setTenantId(tenantId)` 防跨租户篡改 |
| **Fix Phase** | Post-Audit |

### I091 — ecommerce_platform 历史明文 secrets 数据

| 字段 | 内容 |
|---|---|
| **Issue ID** | **I091** 🆕 |
| **类型** | 数据安全 / P1 |
| **描述** | WRITE_ONLY 加后**新**写入的 `appSecret` / `signSecret` 不会泄露，但**历史已存在 DB 的明文 secret 仍原位**；WRITE_ONLY 仅阻止序列化输出，但无法清除 DB 已存值 |
| **影响 Feature** | I028 子项 |
| **修复方案** | ① KMS 一次性加密迁移<br>② 标记旧数据 `invalid`，强制用户重新配<br>③ admin 强制重置 API<br>④ 一次性 UPDATE SQL 清空 + 重新触发 OAuth2 |
| **Fix Phase** | Post-Audit |

---

## 9. 未涉及范围

| 项目 | 状态 |
|---|---|
| I090 tenantId=0L | 🆕 OPEN（P1） |
| I091 历史明文 secrets | 🆕 OPEN（P1） |
| webhookUrl / webhookSecret / apiBaseUrl UI | OPEN（I028 未点名） |
| I029 DB MISSING（migrate_ext_17/22 SQL） | OPEN（不同 Issue） |
| I006 / I007 / I024 Shadow 漂移 | OPEN（I028 同根问题） |
| I040-I042 / I064 Shadow 漂移 | OPEN |

---

## 10. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`3989655`
- **Fix 时间**：2026-09-30 23:08 (UTC+8)
- **Type Check**：✅ vue-tsc --noEmit exit 0
- **Backend Compile**：✅ mvn compile exit 0
- **EOL 政策**：✅ 仅精确 add 3 个业务文件；其他 EOL 文件留在 working tree
- **I028 状态**：⚠️ 前端 CLOSED + 后端 I090/I091 OPEN
- **新 Issue**：✅ I090 + I091 已登记