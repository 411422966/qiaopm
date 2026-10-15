# Issue I004 — 重构 Backlog（低优先级）

**创建时间**：2026-09-12 21:44 (UTC+8)
**Batch**：1.1 — EXT-24 CRM 集成
**Phase**：1（仅登记，**不修**）
**严重度**：🟢 低（架构异味，非阻塞）

---

## 一、问题描述

### 1.1 `CrmIntegrationService` 命名/位置违反项目约定

| 维度 | 现状 | 项目约定 |
|---|---|---|
| 文件名 | `service/CrmIntegrationService.java` | 项目其他 controller 都用 `XxxController.java` |
| 包路径 | `com.meiyetec.service` | 其他 controller 在 `com.meiyetec.controller` |
| 类型 | `@RestController` + `@RequestMapping("/crm-integration")`（`service/CrmIntegrationService.java:26-28`）| `@Service` 命名误导 |
| 行为 | 7 个 REST endpoint 全在这一个 156 行文件里 | 项目其他 Controller 平均 < 100 行 |

### 1.2 集中度问题

一个文件 7 个 endpoint：
- F001 POST `/crm-integration`
- F002 GET `/crm-integration/list`
- F003 POST `/crm-integration/{id}`
- F004 POST `/crm-integration/{id}/toggle`
- F005 POST `/crm-integration/{id}/sync` ⭐（EXT-24 行为变更）
- F006 GET `/crm-integration/sync-log/list`
- F007 GET `/crm-integration/dashboard`

按业务相关性可拆为：
- **CrmIntegrationController**（CRUD：create / list / update / toggle / dashboard）→ F001-F004 + F007
- **CrmSyncController**（同步：syncNow / syncLog）→ F005-F006

### 1.3 同类问题（项目其他文件）

`grep -l "@RestController" backend/src/main/java/com/meiyetec/service/*.java` 也会命中：
- `FollowUpClosureService.java`（含 `@RequestMapping("/crm/care/loop")`）
- `ReviewAnalysisService.java`（含 `@RequestMapping("/crm/review/loop")`）

**这是项目层面的架构异味**，不仅 EXT-24。

---

## 二、影响

| 维度 | 影响 |
|---|---|
| 功能正确性 | **无影响**（代码运行 OK） |
| 可维护性 | 🟡 中（命名误导 + 文件过长） |
| 测试性 | 🟡 中（CrmIntegrationService 单测需 mock Controller 行为） |
| 上线阻塞 | ❌ 不阻塞 |

---

## 三、建议重构（**Backlog，待用户排期**）

| 改动 | 工作量 | 优先级 |
|---|---|---|
| 1. 改名 `CrmIntegrationService` → `CrmIntegrationController` | 0.5h | P3 |
| 2. 移动到 `controller/` 包 | 0.5h | P3 |
| 3. 拆分为 `CrmIntegrationController` + `CrmSyncController` | 2h | P4 |
| 4. 同步修 `FollowUpClosureService` / `ReviewAnalysisService` 同样问题 | 1h | P4 |

**累计工作量**：约 4h，纯重构，**不动业务逻辑**。

---

## 四、不在本批次（v3 第一批）范围

按 `audit/baseline/scope-freeze.md` §2.1 + 用户决策，**v3 第一批只审计 EXT-19/22/23/24 + MKT 重构**。

本 ISSUE 登记到 backlog，**留待后续批次（v3 第二批起）评估**。

---

## 五、未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 |
|---|---|---|
| 1 | 项目整体是否有"Service 包不允许放 Controller"的明确约定 | 静态观察其他 controller 都在 `controller/` 包；未确认是否有 README / CODING_STYLE.md 明确规定 |
| 2 | 重构是否会影响其他模块对 CrmIntegrationService 的引用 | `grep "CrmIntegrationService"` 未全量扫（仅本批次范围） |

---

**本 Issue 登记完成。不影响 Phase 2 任何 Feature 的 Result 标注。**
