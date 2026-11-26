# I209 — `/mh/visual` + `/mh` + `/complication` 按 id 端点缺 `tenant_id` 过滤，可跨租户读写

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**
> **P1 理由**：跨租户**写入** + 涉及**不可逆合规操作**（撤销视觉档案 / 撤销合规记录 / 批量撤回 / 药械召回）。虽然 F175 批次已为这 41 个端点补齐 `@RequirePermission`，但**权限码只回答「谁能调」，不回答「能调到哪些租户的数据」** —— 持对应功能权限的任意登录用户，传他租户的有效主键即可跨租户读写。
> **发现日期**：2026-11-26（F175 批次 —— I206 §3 修复方向落地后的隔离性复核）
> **登记人**：Cursor
> **绑定基线**：HEAD `78c41f8`（`docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则`）
> **验证等级**：**静态 VERIFIED**（12 个端点 + 2 处裸 UPDATE 均带文件路径 + 行号 + 对照证据）+ **运行期 UNVERIFIED**（跨租户越权读写场景本机**未实测** —— 库内仅 1 个业务租户，无第二个租户的有效主键样本）
> **关联**：I206（同批落地后的衍生复核）、I200（`/store/*` 同型缺 `tenant_id`）、I201（`DataScopeInterceptor` 不采路径变量）、I209 与 I206 **互补不重叠**（I206 管「谁能调」，本条管「能调到哪些租户」）
> **登记状态**：**已登记于 `audit/issues/issue-register.md`**（2026-11-26，用户批准后写入）
>
> ⚠️ **行号口径说明**：下文 §1.1 / §1.2 的行号为 **F175 批次落地后**（即 +44 行注解的当前工作区）行号；
> §1.3 的读侧对照行号同口径。F175 落地**前**（HEAD `78c41f8`）的对应行号各 **+2 ~ +4**（注解插在 `@*Mapping` 下一行）。

---

## 0. 一句话描述

F175 为 `VisualArchiveService` / `ConsentAndDrugDeviceService` / `ComplicationService` 的 41 个端点补齐了 `@RequirePermission`，
但其中 **12 个「按 id 操作」的端点在 `selectById(id)` 之后不校验 `tenantId`** —— 同文件内的 `list` / `page` / `stats` 类端点**均带 `tenant_id` 过滤**，
说明这是**遗漏而非设计**。
其中 `VisualArchiveService.handleWithdraw` 的**两条裸 `UPDATE`**（`mh_visual_archive` / `mh_image_consent` 按 `member_id`）**WHERE 子句连 `tenant_id` 都没有**。

---

## 1. 事实

### 1.1 缺租户校验的端点清单（12 个）

**`backend/src/main/java/com/meiyetec/service/VisualArchiveService.java`（类前缀 `/mh/visual`）—— 7 个**

| 行 | 端点 | 缺 tenant 校验的写法 | 不可逆性 |
|---|---|---|---|
| 158 | `POST /archive/{id}/revoke` | `archiveMapper.selectById(id)` → 直接 `updateById` | 撤销档案 |
| 274 | `POST /archive/batch-analyze` | `ids` 列表逐个 `selectById(id)` | 触发 AI 写入 |
| 301 | `POST /archive/batch-revoke` | `ids` 列表逐个 `selectById(id)` → `setRevoked(1)` | **批量撤销** |
| 410 | `POST /compare/{id}/review` | `compareMapper.selectById(id)` | 审核结论 |
| 465 | `GET /consent/member/{memberId}` | `consentMapper.selectList(... eq(memberId))` — **连 memberId 都没校验归属** | **跨租户读** |
| 473 | `POST /consent/{id}/revoke` | `consentMapper.selectById(id)` | 撤销合规记录 |
| 570 | `POST /withdraw/{id}/handle` | `withdrawMapper.selectById(id)` → **两条裸 UPDATE**（见 §1.2） | **批量撤回 + 撤销** |

**`backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java`（类前缀 `/mh`）—— 4 个**

| 行 | 端点 | 缺 tenant 校验的写法 | 不可逆性 |
|---|---|---|---|
| 94 | `GET /consent/template/{id}` | `consentTplMapper.selectById(id)` | 跨租户读 |
| 294 | `POST /consent/sign/{id}/revoke` | `consentSigMapper.selectById(id)` | 撤销签署 |
| 431 | `POST /drug-device/batch/{id}/recall` | `drugDeviceBatchMapper.selectById(id)` | **药械召回** |
| 498 | `POST /drug-device/usage/{id}/adverse` | `drugDeviceUsageMapper.selectById(id)` | 不良反应上报 |

**`backend/src/main/java/com/meiyetec/service/ComplicationService.java`（类前缀 `/complication`）—— 1 个**

| 行 | 端点 | 缺 tenant 校验的写法 | 不可逆性 |
|---|---|---|---|
| 133 | `POST /event/{id}/status` | `eventMapper.selectById(id)` | 改事件状态 |

### 1.2 两处裸 `UPDATE`（`WHERE` 无 `tenant_id`）

`backend/src/main/java/com/meiyetec/service/VisualArchiveService.java:589-594`（`handleWithdraw` 内）：

```java
// 批准撤回：执行撤回动作
if (newStatus == 2) {
    if ("ALL".equals(w.getWithdrawScope()) || "STAFF_VIEW".equals(w.getWithdrawScope())) {
        jdbc.update("UPDATE mh_visual_archive SET revoked = 1, revoked_at = NOW(), revoked_reason = '会员撤回' " +
                "WHERE member_id = ? AND deleted = 0", w.getMemberId());
    }
    if ("ALL".equals(w.getWithdrawScope())) {
        jdbc.update("UPDATE mh_image_consent SET revoked = 1, revoked_at = NOW(), revoked_reason = '会员撤回' " +
                "WHERE member_id = ? AND deleted = 0", w.getMemberId());
    }
}
```

两条 `UPDATE` 的 `WHERE` 只有 `member_id` + `deleted` —— **无 `tenant_id`**。
即：只要构造出一个本租户的 `mh_image_withdraw` 记录（`withdrawScope='ALL'`），
其 `member_id` 指向的**其他租户的** `mh_visual_archive` / `mh_image_consent` 行会被**一并撤销**。

### 1.3 对照证据：同文件的读侧端点**均带** `tenant_id`

| 端点 | 是否带 `tenant_id` |
|---|---|
| `GET /archive/page`（`VisualArchiveService.java:115`） | ✅ `tenant_id = ?` |
| `GET /archive/stats`（`:172`） | ✅ |
| `GET /compare/page`（`:366`） | ✅ |
| `GET /consent/page`（`:487`） | ✅ |
| `GET /consent/expiring`（`:527`） | ✅ |
| `GET /withdraw/stats`（`:601`） | ✅ |
| `GET /withdraw/page`（`:640`） | ✅ |
| `GET /consent/template/list`（`ConsentAndDrugDeviceService.java:65`） | ✅ |
| `GET /consent/stats`（`:181`） | ✅ |
| `GET /consent/sign/page`（`:248`） | ✅ |
| `GET /drug-device/list`（`:324`） | ✅ |
| `GET /drug-device/batch/page`（`:379`） | ✅ |
| `GET /drug-device/usage/page`（`:510`） | ✅ |
| `GET /complication/sop/list`（`ComplicationService.java:50`） | ✅ |
| `GET /complication/event/list`（`:120`） | ✅ |
| `GET /complication/dashboard`（`:149`） | ✅ |

→ **读侧一律带、写侧按 id 一律不带**，是**系统性遗漏**，不是「按 id 查询天然跨租户」的设计。
（`mh_consent` / `mh_visual_archive` 等表本身**有 `tenant_id` 列**，具备隔离条件，只是查询时没用上。）

---

## 2. 影响

持对应功能权限码的任意登录用户（如 `clerk01` 持 `mh.visual` / `complication:list` / `complication:report`；`manager01` / `boss01` 持 `mh:visual:edit` / `mh:visual:revoke` / `mh:consent:revoke` / `complication:manage`），
只要拿到**其他租户的有效主键**，即可：

- **跨租户读**：`GET /mh/visual/consent/member/{memberId}` —— 连 `memberId` 的归属都不校验，任何会员的合规记录（含授权书）可被列出；
- **跨租户写 + 不可逆操作**：撤销视觉档案 / 撤销合规记录 / 批量撤销 / 药械召回 / 不良反应上报 / 改并发症事件状态；
- **放大面**：`POST /mh/visual/withdraw/{id}/handle` 的两条裸 UPDATE 是**按 `member_id` 全表命中**，一次调用即跨租户批量撤销。

> **未确认**：本机**只有 1 个业务租户**（`seed_test_roles.sql` 全部测试账号同租户），**无第二个租户的有效主键样本** → 跨租户越权读写场景**未实测**，标 **UNVERIFIED**。
> 本条结论基于**静态代码事实**（WHERE 子句确实缺 `tenant_id`）与**对照证据**（同文件读侧端点均带），属结构性确证。

---

## 3. 修复方向（不在本批范围）

1. **统一加租户断言**：在 `selectById(id)` 之后显式比对 `entity.getTenantId()` 与 `TenantContextHolder.get().getTenantId()`，不一致返回 `BizCode.TENANT_DENIED`（403）或 `NOT_FOUND`（404，避免存在性泄露）。
2. **两处裸 UPDATE 补 `tenant_id`**：`UPDATE mh_visual_archive ... WHERE member_id = ? AND tenant_id = ? AND deleted = 0`（`mh_image_consent` 同）。
3. **`GET /mh/visual/consent/member/{memberId}`**：`memberId` 需先经 `member` 表反查 `tenant_id`，不能直接按 `member_id` 查合规记录。
4. **建议抽公共断言**：三个 service 重复出现 12 次「selectById 后比 tenantId」，宜抽 `TenantGuard.assertOwned(entity)` 之类的小工具，避免第 13 处再漏。

---

## 4. 遗留标注

本批**只登记，不修**。

与 **I206** 互补且不重叠：I206 管的是「**谁能调**」（角色 → 权限码），本条管的是「**能调到哪些租户**」（权限码 → 数据范围）。
F175 落地后，I206 的鉴权缺口已闭合，但本条的数据隔离缺口**依然存在且因权限码补齐而更易被触发**（原本任何登录用户可调，现在至少需要有对应码的账号）。
与 **I200**（`/store/*` 缺 `tenant_id`）、**I201**（`DataScopeInterceptor` 不采路径变量）属同族，但**端点集合不重叠**。

---

## 5. 关联与交叉引用

- `backend/src/main/java/com/meiyetec/service/VisualArchiveService.java:158,274,301,410,465,473,570,589-594`
- `backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java:94,294,431,498`
- `backend/src/main/java/com/meiyetec/service/ComplicationService.java:133`
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`
- `audit/issues/I200-store-endpoints-missing-tenant.md`（同型）
- `audit/issues/I201-datascope-interceptor-ignores-path-variables.md`（同族）
- Evidence：`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md`
