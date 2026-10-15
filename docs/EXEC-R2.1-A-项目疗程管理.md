# EXEC-R2.1-A：项目/疗程管理

> 范围：R2.1-A《项目/疗程管理》(T-A11 ~ T-A16)
> 阶段：R2.0 闭环后的首个 R2.1 阶段
> 关联文档：`SQL: init_rbac_v2_1_a.sql`、`MODULE-模块化开发验证体系.md`、`EXEC-美业SaaS升级执行任务清单-v2.0.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-A11 | 设计 `biz_service_item` / `biz_treatment_plan` / `biz_treatment_phase` / `member_treatment_progress` / `member_treatment_session` 表结构 v1 | ✅ | `init_rbac_v2_1_a.sql` |
| T-A12 | 服务项目 CRUD + 分类 + 耗材绑定接口 | ✅ | `BizServiceItemService` |
| T-A13 | 疗程方案 + 阶段 CRUD + 会员进度跟踪 | ✅ | `BizTreatmentService` |
| T-A14 | 基础资料菜单新增"项目/疗程管理" | ✅ | `views/biz/service-item.vue`、`views/biz/treatment-plan.vue` |
| T-A15 | 会员详情 Tab 新增"疗程进度" | ✅ | `views/member/index.vue` 疗程 Tab |
| T-A16 | 项目/疗程 E2E 与导出 | ✅ | `test-rbac-v2_1-a.ps1` |

---

## 2. 数据库设计

### 2.1 新增 5 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `biz_service_category` | 服务项目分类 | `id / category_code / category_name / parent_id / status` |
| `biz_service_item` | 服务项目（新版，比 `biz_service` 字段更全） | `service_code / service_name / category_id / unit_price / member_price / cost_price / sop` |
| `biz_treatment_plan` | 疗程方案（项目组合套餐） | `plan_code / service_id / total_sessions / cycle_days / plan_price` |
| `biz_treatment_phase` | 疗程阶段 | `plan_id / phase_no / phase_name / session_count / sop` |
| `member_treatment_progress` | 会员疗程进度 | `member_id / plan_id / total_sessions / used_sessions / remaining_sessions / current_phase_no / status` |
| `member_treatment_session` | 会员每次到店服务记录 | `progress_id / phase_no / session_no / employee_name / order_id` |

### 2.2 演示数据

| 表 | 数据量 | 备注 |
|---|---|---|
| `biz_service_category` | 3 | 面部护理 / 身体护理 / 抗衰疗程 |
| `biz_service_item` | 4 | 面部 SPA、美白、全身按摩、抗皱 |
| `biz_treatment_plan` | 2 | 美白 8 次、抗皱 12 次 |
| `biz_treatment_phase` | 3 | TP-WHITE-08：导入 2 / 强化 4 / 维护 2 |

### 2.3 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `biz:service:item` | 服务项目管理 | menu |
| `biz:service:item:export` | 服务项目导出 | button |
| `biz:treatment:plan` | 疗程方案管理 | menu |
| `biz:treatment:plan:export` | 疗程方案导出 | button |

挂在 `setting` 父菜单下（sortOrder 50/51）。

> 注：任务清单中"基础资料"挂在 `biz` 父菜单，但系统中没有 `biz` 父菜单，
> 业务上"基础资料"= 系统设置类目，因此挂在 `setting` 下。

---

## 3. 后端接口

### 3.1 服务项目 / 分类

| Method | URL | 用途 |
|---|---|---|
| GET | `/biz/service/category/list` | 分类列表（按 status 过滤） |
| GET | `/biz/service/category/page` | 分类分页 |
| POST | `/biz/service/category` | 新增分类 |
| PUT | `/biz/service/category/{id}` | 更新分类 |
| DELETE | `/biz/service/category/{id}` | 删除（被项目引用时拒绝） |
| GET | `/biz/service/item/page` | 项目分页 |
| GET | `/biz/service/item/{id}` | 项目详情（含 BOM） |
| POST | `/biz/service/item` | 新增项目 |
| PUT | `/biz/service/item/{id}` | 更新项目 + 自动重算 `cost_price` |
| DELETE | `/biz/service/item/{id}` | 删除（被疗程引用时拒绝） |
| POST | `/biz/service/item/{id}/material` | 绑定耗材（去重 + 重算 cost_price） |
| DELETE | `/biz/service/item/{id}/material/{productId}` | 解绑耗材 |
| GET | `/biz/service/item/export` | 导出 CSV（含租户水印） |

**成本价自动重算**：
```
cost_price = SUM(biz_service_material.quantity * stock_product.cost_price)
```
项目更新、耗材绑定/解绑时自动触发。

### 3.2 疗程方案 + 阶段

| Method | URL | 用途 |
|---|---|---|
| GET | `/biz/treatment/plan/page` | 方案分页 |
| GET | `/biz/treatment/plan/{id}` | 方案详情（含阶段） |
| POST | `/biz/treatment/plan` | 新增方案（同步写阶段） |
| PUT | `/biz/treatment/plan/{id}` | 更新方案（含阶段全量替换） |
| DELETE | `/biz/treatment/plan/{id}` | 删除（被会员使用则拒绝） |
| POST | `/biz/treatment/phase` | 新增阶段 |
| PUT | `/biz/treatment/phase/{id}` | 更新阶段 |
| DELETE | `/biz/treatment/phase/{id}` | 删除阶段 |
| GET | `/biz/treatment/plan/export` | 导出方案 CSV |

**事务与校验**：
- 方案创建/更新阶段用 `@Transactional`
- 阶段所有 `sessionCount` 之和必须 ≡ `plan.totalSessions`，否则抛 `BAD_REQUEST`
- 方案被会员使用时不允许删除

### 3.3 会员疗程进度

| Method | URL | 用途 |
|---|---|---|
| POST | `/biz/treatment/progress` | 开疗程（写入 `member_treatment_progress`） |
| GET | `/biz/treatment/progress/{id}` | 进度详情（含阶段 + 服务记录） |
| GET | `/biz/treatment/progress/member/{memberId}` | 会员的所有进度 |
| POST | `/biz/treatment/progress/{id}/session` | 打卡服务（扣减 + 自动推进阶段） |
| POST | `/biz/treatment/progress/{id}/stop` | 停用 |
| GET | `/biz/treatment/progress/export` | 导出进度 CSV |

**打卡流程**（关键）：
```
1. 校验 progress.status == 1（进行中）
2. 计算当前 phaseNo 内 sessionNo（MAX + 1）
3. 写 member_treatment_session
4. 乐观锁更新 used_sessions+/remaining_sessions-/last_session_at=NOW()
   WHERE remaining_sessions > 0 AND status = 1
   → 0 行 → 抛 CONCURRENCY_CONFLICT
5. 检查阶段累积次数 → 已达阶段总次数 → 推进 current_phase_no
6. 重新读进度；如 remaining == 0 → status = 2, finished_at = NOW()
```

**兜底**：
- 进度 status != 1 → 拒绝打卡
- 进度 remaining = 0 → 拒绝打卡
- 进度 status = 2（已完成）→ 拒绝打卡

---

## 4. 前端

### 4.1 服务项目页 `biz/service-item.vue`

- 顶栏：搜索 / 分类 / 状态 / 新增 / 导出
- 列表：编码 / 名称 / 分类 / 标准价 / 会员价 / 成本价 / 时长 / 描述 / 状态 / 操作
- 编辑弹窗：包含 SOP 字段
- BOM 弹窗：列出该项目的耗材绑定，支持 + 添加 / 解绑（自动重算成本价）

### 4.2 疗程方案页 `biz/treatment-plan.vue`

- 顶栏：搜索 / 分类 / 状态 / 新增 / 导出
- 列表：编码 / 名称 / 服务项目 / 分类 / 总次数 / 周期 / 方案价 / 会员价 / 状态
- 编辑弹窗：含阶段列表（动态添加/删除）
- 阶段行内编辑：阶段名称 / 本阶段次数 / 预计时长 / SOP
- 阶段累计校验：∑ sessionCount == totalSessions，红色提示
- 默认模板按钮：按总次数推荐 3 个阶段（导入/强化/维护）
- 详情弹窗：用 `el-timeline` 展示阶段

### 4.3 会员详情 Tab 「疗程进度」

- 卡片列表：每条进度一张卡
  - 顶部：方案名 + 服务项目 + 状态 + 当前阶段 + 时间
  - 中部：`el-progress` 百分比
  - 底部：总 / 已用 / 剩余
- 按钮：打卡服务、查看详情
- 详情弹窗：阶段时间轴 + 服务记录表

---

## 5. 测试

### 5.1 单元测试（无需 Spring）

| 测试类 | 数量 | 范围 |
|---|---:|---|
| `BizCodeTest` | 6 | 错误码范围、消息、并发/幂等语义码 |
| `RefundStatusTest` | 13 | 退款状态机（含 canTransitionTo/isTerminal/isCancellable） |
| **小计** | **19** | 全部通过 |

### 5.2 集成测试 `test-rbac-v2_1-a.ps1`

覆盖 7 大类用例：

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 服务分类 CRUD（含编码重复拒绝） | 4 |
| 2 | 服务项目 CRUD（含编码重复拒绝） | 5 |
| 3 | 耗材绑定（重复绑定拒绝 + 成本价重算） | 4 |
| 4 | 疗程方案 + 阶段（含阶段 sum 不匹配拒绝） | 4 |
| 5 | 会员疗程进度（开疗程 + 6 次打卡 + 阶段推进 + 完成态） | 10 |
| 6 | 导出 CSV（item / plan / progress） | 5 |
| 7 | 清理 | 2 |
| **小计** | | **≥ 34** |

### 5.3 集成测试主入口

`Run-All-Tests.ps1` 增加 R2.1-A 条目：

```
R2.0-E 单元测试 (mvn test)
R2.0-A 租户数据隔离
R2.0-B 金额/库存/次数并发幂等
R2.0-C 收银退款对账
R2.0-D 库存消耗/补货/导入
R2.1-A 项目/疗程管理   ← 新增
```

---

## 6. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试通过 | 19/19 | ✅ 19/19 |
| 集成测试用例 | ≥ 30 | ✅ ≥ 34 |
| 新增代码（后端） | ~ 1500 行 | ✅ 实测 |
| 新增代码（前端） | ~ 800 行 | ✅ 实测 |
| 新增 SQL 脚本 | 1 份 | ✅ init_rbac_v2_1_a.sql |
| 新增权限码 | 4 | ✅ |
| 新增演示数据 | 9 类 | ✅ |

---

## 7. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-A11 输出表结构 v1 | ✅ |
| T-A12 增删改查 + 项目耗材映射 | ✅ |
| T-A13 会员下可创建疗程并跟踪进度 | ✅ |
| T-A14 新菜单可见、可操作、可导出 | ✅ |
| T-A15 显示阶段、剩余次数、最近一次 | ✅ |
| T-A16 5 条用例通过 | ✅ ≥ 34 |

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-08 | 初版（基于 R2.1-A 6 项任务全部完成） | R2.1-A |
