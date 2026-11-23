# I200 — `/store/*` 全模块 7 个端点未按 `tenant_id` 过滤，门店数据跨租户可见

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**（读端点跨租户泄露；本机因 `pos_store` 仅 `tenant_id=1` 暂不可观测 → 属**潜伏式**中高危）
> **发现日期**：2026-11-23（I194-0 调查报告复核时顺带实测发现）
> **登记人**：Cursor
> **绑定基线**：HEAD `1b617e2`
> **验证等级**：**静态 VERIFIED + 运行期条件性 VERIFIED**（本机 `pos_store` 单租户，**泄露场景在运行期不可复现**，静态证据充分）
> **关联**：**I194-0**（records.vue 门店下拉数据源）、**I186**（数据范围统一入口）、**I117**（datascope 覆盖缺口）
> **来源**：I194-0 调查报告 §4.3「I200-2.6-C」

---

## 0. 一句话描述

`StoreController` 的 **7 个端点全部未按 `tenant_id` 过滤**：`storeService.list()` / `storeService.page(wrapper)` 直接落到 `SELECT ... FROM pos_store`，**不带任何租户条件** → 门店名称、地址、联系电话、店长等经营信息**跨租户可见**。

---

## 1. 事实核实

### 1.1 逐端点清单（7 个 / 全部漏 tenant_id）

| # | 路由 | 方法 | 源码位置 | 缺 tenant 条件的位置 |
|---|---|---|---|---|
| 1 | `/store/list` | GET | `StoreController.java:33-37` | `storeService.list()` → 无条件全表查 |
| 2 | `/store/page` | POST | `StoreController.java:69-84` | `wrapper` 只拼 keyword/status/orderBy，无 `eq(tenantId)` |
| 3 | `/store/stats` | GET | `StoreController.java:86-95` | `storeService.list()` 全量聚合 |
| 4 | `/store/{id}` | GET | `StoreController.java:97-101` | `storeService.getById(id)` → 仅按主键，无租户校验 |
| 5 | `/store` | POST | `StoreController.java:103-110` | `BeanUtils.copyProperties` 后 `save`；**DTO 无 `tenantId` 字段** |
| 6 | `/store` | PUT | `StoreController.java:112-119` | `updateById(store)` → 仅按主键，无租户校验 |
| 7 | `/store/{id}` | DELETE | `StoreController.java:121-126` | `removeById(id)` → 仅按主键，无租户校验 |

**根因**：`StoreController` 全文 **0 处** `eq(Store::getTenantId, ...)`；`StoreDTO`（`dto/StoreDTO.java`）**无 `tenantId` 字段**；`StoreServiceImpl`（`service/impl/StoreServiceImpl.java`）**空实现**（仅 `extends ServiceImpl`，无任何租户兜底）。`Store` 实体确有 `tenantId` 字段（`entity/Store.java:14`），且 `TenantMetaObjectHandler.insertFill` 会自动填（`config/TenantMetaObjectHandler.java:24-25`）→ **写路径的 tenant_id 由 MetaObjectHandler 保证，但读路径无任何过滤**。

### 1.2 运行期实测（本机 2026-11-23 16:45）

| 探测 | 账号 | 结果 |
|---|---|---|
| `SELECT id,store_name,tenant_id,deleted FROM pos_store` | — | **7 行，`tenant_id` 全部 = 1**（id=1 有效；id=5/9/13/17/21/25 为软删除测试数据 `deleted=1`） |
| `GET /store/list` | manager01 (tenant 1) | 200，返回 1 条（id=1, tenantId=1）→ **本机无法观测泄露**（库里没有其他租户门店） |
| `POST /store/page` | manager01 | 200，`total=1` → 同上 |
| `GET /store/99999999` | manager01 | 200 `data:null` → 该端点主键查不到，**根因见 §1.1-4**，非租户校验 |

> ⚠️ **本机不可复现 ≠ 问题不存在**：`pos_store` 无 `tenant_id != 1` 的行，任何跨租户列表探测都只会返回同样 1 条 → **泄露场景需有其他租户门店数据才能复现**。当前结论 = **静态 VERIFIED**。

### 1.3 影响面（哪些页面会漏）

前端 `storeApi.list()` 被 **5 处**调用，全部会拿到其他租户门店（若库中有）：
`views/booking/booking-form.vue:188`、`views/member/index.vue:1197`、`views/stock/CheckCreateWizard.vue:226`、`views/stock/check.vue:372`、`views/setting/employee.vue:250`。
另有 `views/setting/store.vue:205/212` 用 `storeApi.page` / `storeApi.stats` → 同样跨租户。

**外泄字段**：`storeName` / `storeCode` / `contactPerson` / `contactPhone` / `address` / `province` / `city` / `district` / `businessHours` / `managerName`
→ **门店地址与联系电话属于个人信息/经营敏感信息**。

---

## 2. 修复方向建议（仅建议，未执行）

| 方案 | 内容 | 评价 |
|---|---|---|
| **A（推荐）** | `StoreController` 7 端点统一收敛到 `StoreService`，Service 层注入 `wrapper.eq(Store::getTenantId, tid)`；`/store/{id}` 加 `tid` 归属校验（不存在 → `NOT_FOUND`）；`/store` 写入显式 `store.setTenantId(tid)`（不依赖 MetaObjectHandler 兜底） | 修复面集中、与 I186 第 1 批 Service 层注入风格一致 |
| B | 在 `StoreController` 直接加 `eq(Store::getTenantId, tid)` | 与项目既有风格（范围条件下沉 Service）不一致；且 `list()` / `stats()` 走 `ServiceImpl.list()` 时 wrapper 不可传 → B 实际不可用 |
| C | 引入 `TenantLineInnerInterceptor` 全局自动注入 | `MybatisPlusConfig.java:12-15` 已明确**否决**（业务表字段不一致 → `Unknown column 'tenant_id'`），**不可用** |

**同时需要（I194-0 前置）**：新增 `GET /store/accessible`，按 `ctx.storeIds` + `tenant_id` 返回**当前账号可见门店** —— 作为 records.vue 门店下拉的数据源，**不复用 `/store/list`**，避免把 I200 的缺陷带进 I194-0。

> 📌 **更新（2026-11-23 I194-0 实施后）**：`GET /store/accessible` **已实现**（`StoreController.java:52-68`，按 `tenant_id` + `ctx.storeIds` 过滤）。下表 §3 验收项 #8 已由该实现覆盖。

---

## 3. 验收标准（修复后）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 在 `pos_store` 插入一行 `tenant_id = 999`（**测试租户**，非真实业务主键）后，`GET /store/list`（tenant 1 账号） | 返回**不含**该行 |
| 2 | 同上，`POST /store/page` | `total` 不含该行 |
| 3 | 同上，`GET /store/stats` | `total` 不含该行 |
| 4 | `GET /store/{其他租户门店id}`（tenant 1 账号） | **404 / NOT_FOUND**（不得返回门店详情） |
| 5 | `PUT /store` body 携带其他租户门店 id | **拒绝**（不得改写其他租户行） |
| 6 | `DELETE /store/{其他租户门店id}` | **拒绝** |
| 7 | `POST /store` 新建后查库 | `tenant_id` = 当前会话租户（不是 NULL，也不是其他租户） |
| 8 | `GET /store/accessible` | 仅返回 `ctx.storeIds` 内门店；ALL 账号返回本租户全部门店（**I194-0 已实现，见 §2 补充**） |

---

## 4. 未验证项 / 已知限制

| # | 未验证项 | 原因 |
|---|---|---|
| 1 | 跨租户泄露的运行期复现 | 本机 `pos_store` 仅 tenant1，无其他租户样本；**按规则 20，不得用真实业务主键构造"不存在"样本** → 需专用测试租户数据 |
| 2 | 生产库 `pos_store` 是否已有其他租户行 | 无生产库连接能力 |
| 3 | `StoreDTO` 缺 `tenantId` 是否已在其它入口被前端补传 | 前端 `views/setting/store.vue` 表单未传 `tenantId`（静态已见） |
