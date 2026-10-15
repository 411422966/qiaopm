# E2E 测试汇总报告（Phase 5B 第三轮）

> **提交时间**：2026-09-13 17:44 (UTC+8)
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **跑测框架**：Playwright MCP（手动触发）
> **Spec 文件根目录**：`audit/scripts/e2e/`
> **结果证据根目录**：`audit/evidence/FXXX-*/e2e-result.md`
> **关联报告**：5A `audit/runtime/report-5A.md` / 5B `audit/runtime/report-5B.md`

---

## 1. 总览

| 指标 | 数值 |
|---|---|
| **总测试数** | 12 |
| **PASS** | **5** |
| **FAIL** | **7** |
| **假 PASS** | 0（第三轮已消除 5B 第二轮的 1 个假 PASS）|
| **未确认 / 跳过** | 0 |
| **影响 Feature** | F007 / F024 / F025 / F026 / F027 / F028（6 个 UI/API 集成点）|

### 1.1 5B 三轮对比

| 轮次 | 时间 | PASS | FAIL | 假 PASS | 关键变化 |
|---|---|---|---|---|---|
| **第一轮** | 2026-09-13 00:30 | 6 | 6 | 0 | 初始 E2E 跑测 |
| **第二轮** | 2026-09-13 17:00 | 5 | 7 | 1 | F025 `>=` 假 PASS + F026 列头方向错 |
| **第三轮（本轮）**| 2026-09-13 17:44 | **5** | **7** | **0** | F025 改 `+1` + dialog 关闭断言；F026 改中文「集成」+ I016 反向断言 |

### 1.2 第三轮测试分类

#### PASS 5 个

| # | Feature | Test name | 验证点 | 关联 Issue |
|---|---|---|---|---|
| 1 | F007 | I006: 应无 dashboard tab | 前端无 dashboard tab | I006（Shadow Feature）|
| 2 | F007 | API 端点可访问（I008 Fake Success 链）| dashboard HTTP 200 + code=200 | I008（catch 吞异常）|
| 3 | F025 | 对话框渲染 + I005 C4 字段缺失 | 4 字段存在 + 4 字段缺失 | I005 C4（前端缺字段）|
| 4 | F026 | 切到同步日志 tab + 验证 integrationId 列 | 列头中文「集成」+ 缺 platform | I003（prop 漂移）+ I016（缺 platform 列）|
| 5 | F028 | 新建表单无 objectMappings 字段（I005 C4）| 表单无 objectMappings | I005 C4 |

#### FAIL 7 个（全部 I002 负向证据）

| # | Feature | Test name | 根因 | 关联 Issue |
|---|---|---|---|---|
| 1 | F024 | 集成列表渲染 + 列名 + 状态 enum | LIST API 500 | **I002** |
| 2 | F025 | 填表保存 → 列表新增行 | CREATE API 500 + dialog 不关 + 行数未 +1 | **I002 + I011（二次实证）**|
| 3 | F026 | 同步日志 API 响应（I003 漂移实证）| listSyncLogs API 500 | **I002** |
| 4 | F027 | 点击同步按钮 → URL 不带 `?force=1` | URL 不带 force 参数 | **I005 C2** |
| 5 | F027 | API 直调验证 force 未传 | syncNow API 500 | **I002** |
| 6 | F027 | API 直调 force=1 验证 | syncNow API 500 | **I002** |
| 7 | F028 | 后端 API + DB 验证 object_mappings 持久化为 NULL | CREATE API 500 | **I002 + I005 C4** |

---

## 2. 详细结果

### 2.1 F007 — Dashboard 看板

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| I006: 应无 dashboard tab | ✅ PASS | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F007-no-dashboard-tab-F007-4ce44-ature-I006-应无-dashboard-tab\trace.zip) | [test-failed-1.png](D:\QiaoPM\frontend\meiyetec-web\test-results\F007-no-dashboard-tab-F007-4ce44-ature-I006-应无-dashboard-tab\test-failed-1.png) | 前端无 dashboard tab → I006 Shadow Feature |
| API 端点可访问（I008）| ✅ PASS | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F007-no-dashboard-tab-F007-a6a8f--端点可访问（I008-Fake-Success-链）\trace.zip) | — | dashboard API 正常返回 totalIntegrations |

**意外发现**：`totalIntegrations` 17:37→17:44 从 2 → 3，**证明 I002 对 dashboard 端点意外不阻塞**（selectCount 不引用 EXT-15 新列）。完整分析见 `audit/evidence/F007-dashboard/e2e-result.md` §3。

**完整结果**：`audit/evidence/F007-dashboard/e2e-result.md`

### 2.2 F024 — 集成列表 UI

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| 集成列表渲染 + 列名 + 状态 enum | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F024-list-ui-F024-—-集成列表-UI-集成列表渲染-列名-状态-enum\trace.zip) | [test-failed-1.png](D:\QiaoPM\frontend\meiyetec-web\test-results\F024-list-ui-F024-—-集成列表-UI-集成列表渲染-列名-状态-enum\test-failed-1.png) | I002 阻塞 list API → 行数 = 0 |

**完整结果**：`audit/evidence/F024-list-ui/e2e-result.md`

### 2.3 F025 — 新建集成表单

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| 对话框渲染 + I005 C4 字段缺失 | ✅ PASS | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F025-create-form-F025-—-新建集成表单-对话框渲染-I005-C4-字段缺失\trace.zip) | — | 4 字段存在 + 4 字段缺失 |
| 填表保存 → 列表新增行 | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F025-create-form-F025-—-新建集成表单-填表保存-→-列表新增行\trace.zip) | [test-failed-1.png](D:\QiaoPM\frontend\meiyetec-web\test-results\F025-create-form-F025-—-新建集成表单-填表保存-→-列表新增行\test-failed-1.png) | dialog 不关 + 行数未 +1 |

**关键发现**：
- 5B 第二轮假 PASS 已修复（`>=` 改 `+1` + dialog 关闭断言）
- **I011 Fake Failure 二次实证**：dashboard totalIntegrations 17:37→17:44 从 2→3，反推 F025 填表保存期间 DB 多一行

**完整结果**：`audit/evidence/F025-create-form/e2e-result.md` + `audit/issues/I011-crm-fake-failure.md` §10

### 2.4 F026 — 同步日志 tab

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| 切到同步日志 tab + 验证 integrationId 列 | ✅ PASS | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F026-sync-log-tab-F026-—-同-157ec-同步日志-tab-验证-integrationId-列\trace.zip) | — | 列头中文「集成」 + 缺 platform（I016）|
| 同步日志 API 响应（I003 漂移实证）| ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F026-sync-log-tab-F026-—-同步日志-tab-同步日志-API-响应（I003-漂移实证）\trace.zip) | — | listSyncLogs API 500 |

**关键发现**：
- 5B 第二轮断言方向错已修复（列头中文 + I016 反向）
- I003 描述已补正（prop/label 区分）
- I016 已新建（缺 platform 列）

**完整结果**：`audit/evidence/F026-sync-log-tab/e2e-result.md` + `audit/issues/I003` 描述补正 + `audit/issues/I016-sync-log-missing-platform-column.md`

### 2.5 F027 — sync 按钮 force 未传

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| 点击同步按钮 → URL 不带 `?force=1` | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F027-sync-button-F027-—-sy-7240d--未传-点击同步按钮-→-URL-不带-force-1\trace.zip) | [test-failed-1.png](D:\QiaoPM\frontend\meiyetec-web\test-results\F027-sync-button-F027-—-sy-7240d--未传-点击同步按钮-→-URL-不带-force-1\test-failed-1.png) | I005 C2：URL 不带 force |
| API 直调验证 force 未传 | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F027-sync-button-F027-—-sync-按钮-force-未传-API-直调验证-force-未传\trace.zip) | — | syncNow API 500 |
| API 直调 force=1 验证 | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F027-sync-button-F027-—-sync-按钮-force-未传-API-直调-force-1-验证\trace.zip) | — | syncNow API 500（无论 force）|

**完整结果**：`audit/evidence/F027-sync-button/e2e-result.md`

### 2.6 F028 — objectMappings 字段

| Test | 状态 | trace.zip | 截图 | 关键发现 |
|---|---|---|---|---|
| 新建表单无 objectMappings 字段（I005 C4）| ✅ PASS | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F028-object-mappings-F028--34644--objectMappings-字段（I005-C4）\trace.zip) | [test-failed-1.png](D:\QiaoPM\frontend\meiyetec-web\test-results\F028-object-mappings-F028--34644--objectMappings-字段（I005-C4）\test-failed-1.png) | 表单无 objectMappings（I005 C4）|
| 后端 API + DB 验证 object_mappings 持久化为 NULL | ❌ FAIL | [trace.zip](D:\QiaoPM\frontend\meiyetec-web\test-results\F028-object-mappings-F028--c2f56-证-object-mappings-持久化为-NULL\trace.zip) | — | CREATE API 500 |

**完整结果**：`audit/evidence/F028-object-mappings/e2e-result.md`

---

## 3. 与 Issue Register 的交叉引用

### 3.1 FAIL → Issue 映射

| FAIL Test | 主要根因 Issue | 次要根因 | Issue Register 行 |
|---|---|---|---|
| F024 集成列表渲染 | **I002** | — | §一 I002 |
| F025 填表保存 | **I002** | **I011（二次实证升级）**| §一 I002 + §八 I011 §10 |
| F026 同步日志 API | **I002** | I003（数据层断言被 I002 阻塞）| §一 I002 + §三 I003 |
| F027 点击同步按钮 | **I005 C2** | — | §五 I005 |
| F027 API 直调 force 未传 | **I002** | I005 C2 | §一 I002 + §五 I005 |
| F027 API 直调 force=1 | **I002** | — | §一 I002 |
| F028 object_mappings NULL | **I002** | I005 C4 | §一 I002 + §五 I005 |

### 3.2 PASS → 验证点映射

| PASS Test | 验证点 | Issue Register 行 |
|---|---|---|
| F007 I006 应无 dashboard tab | I006（Shadow Feature）实证 | §六 I006 |
| F007 API 端点可访问 | I008（catch 吞异常）观测 + 意外发现 selectCount 不受 I002 阻塞 | §八 I008 |
| F025 对话框渲染 | I005 C4（前端缺字段）实证 | §五 I005 |
| F026 同步日志 tab | I003（prop 漂移）+ **I016（新建）** 实证 | §三 I003 + §十四 I016 |
| F028 objectMappings 字段缺失 | I005 C4（前端缺字段）实证 | §五 I005 |

---

## 4. Phase 5A / 5B / 第三轮 交叉引用

### 4.1 Phase 5A（端点 + Adapter 验证）

| 验证内容 | 5A 结论 | 第三轮 E2E 一致性 |
|---|---|---|
| F002 list API | PARTIAL-BLOCKER（I002）| ✅ 一致（F024 E2E FAIL）|
| F003 update API | CRITICAL（I011）| — |
| F005 sync API | PARTIAL-BLOCKER（I002）| ✅ 一致（F027 E2E FAIL）|
| F007 dashboard | BROKEN（I002 + I008 联动）| ⚠️ 部分不一致（E2E 端点可访问 + totalIntegrations 真实返回）|
| F010 auto tick | I008 升级 CRITICAL | — |

### 4.2 Phase 5B 第一轮（API + 静态）

| 验证内容 | 5B 第一轮 | 第三轮 E2E 一致性 |
|---|---|---|
| F001 create | PARTIAL-BLOCKER | ✅ 一致（F025 + F028 CREATE FAIL）|
| F004 toggle | PARTIAL-BLOCKER | — |
| F006 sync log | PARTIAL-BLOCKER | ✅ 一致（F026 API FAIL）|
| F009 sync cross-tenant | PARTIAL-BLOCKER | — |
| F013-F018 adapter | PARTIAL-NON-BLOCKER | — |
| F029 status state machine | PARTIAL-NON-BLOCKER | — |

### 4.3 Phase 5B 第二轮（首轮 E2E）

| 验证内容 | 第二轮发现 | 第三轮修复 |
|---|---|---|
| F025 填表保存 | **假 PASS**（`>=` 断言 0>=0 永远 PASS）| ✅ 改 `+1` + dialog 关闭断言 |
| F026 同步日志 tab 列头 | **断言方向错**（检查 integrationid 不检查中文「集成」）| ✅ 改中文 + I016 反向 |
| F007 dashboard | ⚠️ 正常 | ⚠️ 正常（发现 selectCount 不受 I002 阻塞）|
| 其他 9 个测试 | ✅ 行为正确 | ✅ 一致 |

### 4.4 本轮（第三轮）新增发现

| 发现 | 影响 |
|---|---|
| **I011 二次实证** | F025 create 端点同样存在 Fake Failure，影响面扩大 |
| **dashboard 不受 I002 阻塞** | F007 端点 `selectCount` 不引用 EXT-15 列，意外可用 |
| **I003 描述补正** | 精确化为 prop/label 区分 |
| **I016 新建** | 同步日志 tab 缺 platform 列 |

---

## 5. 严重度矩阵

| Feature | 测试数 | PASS | FAIL | 状态 | 主要问题 |
|---|---|---|---|---|---|
| F007 | 2 | 2 | 0 | ⚠️ PARTIAL-NON-BLOCKER | I006 + I008（I002 意外不阻塞）|
| F024 | 1 | 0 | 1 | ❌ PARTIAL-BLOCKER | I002 |
| F025 | 2 | 1 | 1 | ❌ PARTIAL-BLOCKER | I002 + I011（二次）|
| F026 | 2 | 1 | 1 | ⚠️ PARTIAL-NON-BLOCKER | I003 + I016 + I002 |
| F027 | 3 | 0 | 3 | ❌ PARTIAL-BLOCKER | I005 C2 + I002 |
| F028 | 2 | 1 | 1 | ⚠️ PARTIAL-NON-BLOCKER | I005 C4 + I002 |

---

## 6. 修复优先级（不动业务代码前提下）

| 优先级 | 修复路径 | 影响 Issue | 阻塞 E2E? |
|---|---|---|---|
| **P0** | 跑 `migrate_ext_15_crm_real.sql` | I002 | 解锁 6/7 FAIL |
| **P1** | F027 前端补 `?force=1` | I005 C2 | 解锁 1/7 FAIL |
| **P2** | F025 create 端点 select-after-insert 重构 | I011 §10（扩大）| 解锁 I011 风险 |
| **P3** | F026 前端补 platform 列 | I016 | 解锁 UX |
| **P4** | F007 前端加 dashboard tab | I006 | 解锁 Shadow |
| **P5** | F025/F028 表单补 objectMappings/clientId/clientSecret/privateKey | I005 C4 | 解锁表单完整性 |

---

## 7. 总结

### 7.1 第三轮 E2E 状态

- ✅ **所有假 PASS 已消除**（F025 第二轮假 PASS + F026 方向错）
- ✅ **12 个测试结果有效**（5 PASS + 7 FAIL）
- ✅ **I011 二次实证升级**（dashboard 数字反推）
- ✅ **I003 描述补正 + I016 新建**同步日志 tab 列漂移

### 7.2 主要审计贡献

1. **测试有效性修复**：第二轮 1 个假 PASS + 1 个方向错 → 第三轮真 FAIL + 正确方向
2. **I011 适用面扩大**：单端点实证 → 多端点联合实证（5B 首次 + 5B 二次）
3. **Issue 增补**：I003 描述补正 + I016 新建（同步日志 tab 列缺失）
4. **意外发现**：F007 dashboard 端点 I002 意外不阻塞（selectCount 不引用 EXT-15 列）

### 7.3 下一阶段建议

- **不修业务代码**（用户决策 D2/D3/D4 维持）
- **不重跑**（用户决策本轮后手动跑验证）
- 等待用户决策：是否调整 D2/D3/D4 / 是否进入 Phase 5C
