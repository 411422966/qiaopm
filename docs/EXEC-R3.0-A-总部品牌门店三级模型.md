# EXEC-R3.0-A：总部/品牌/门店三级模型

> 范围：R3.0-A《总部/品牌/门店三级模型》(T-A21 ~ T-A23)
> 关联文档：`init_rbac_v3_0_a.sql`、`MODULE-模块化开发验证体系.md`、`EXEC-美业SaaS升级执行任务清单-v2.0.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-A21 | 引入 `brand` 表与品牌-门店-租户关系 | ✅ | `mkt_brand` + `pos_store` ALTER + `pos_employee_store` |
| T-A22 | 升级数据范围：品牌级、租户级、本店、本人 | ✅ | `TenantContext` + roleCode/brandId + `TenantContextHolder` |
| T-A23 | 总部视角看板（跨店汇总、Top 门店） | ✅ | `OrgHierarchyService.hqDashboard` |
| T-A21~23 测试 | E2E | ✅ | `test-rbac-v3_0_a.ps1` |

---

## 2. 数据库设计

### 2.1 新增 2 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `mkt_brand` | 品牌主档 | `brand_code / brand_type / member_share_scope / points_share_scope / balance_share_scope` |
| `pos_employee_store` | 员工-门店多对多 | `employee_id / store_id / role_in_store / is_primary` |

### 2.2 扩展 1 张表

| 表 | 扩展字段 | 用途 |
|---|---|---|
| `pos_store` | `tenant_id / brand_id / store_type / is_hq / region_code / manager_employee_id` | 归属关系 + 总部标记 |

### 2.3 新增角色

| 角色 | 数据范围 | 说明 |
|---|---|---|
| `HQ_ADMIN` | ALL | 总部管理员，查看所有品牌/门店 |
| `BRAND_ADMIN` | BRAND | 品牌管理员，查看品牌内所有门店 |
| `BRAND_STAFF` | BRAND | 品牌员工，查看品牌内所有门店数据 |

### 2.4 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `org:brand` | 品牌管理 | menu |
| `org:store` | 门店管理 | menu |
| `org:employee` | 员工管理 | menu |
| `org:hq` | 总部看板 | menu |

### 2.5 品牌策略配置

| 策略字段 | 选项 | 说明 |
|---|---|---|
| `member_share_scope` | BRAND / STORE | 会员是否跨门店共享 |
| `points_share_scope` | BRAND / STORE | 积分是否跨门店共享 |
| `balance_share_scope` | BRAND / STORE | 储值是否跨门店共享 |

---

## 3. 数据模型

```
租户 (tenant_id)
  └── 品牌 (mkt_brand)
        ├── 门店 (pos_store) ──── tenant_id, brand_id
        │     └── 员工-门店 (pos_employee_store)
        └── 员工 (pos_employee) ──── pos_employee_store
```

**多对多关系**：`pos_employee_store`
- 一名员工可在多个门店工作
- `is_primary = 1` 表示主店
- `role_in_store`：店长 / 副店长 / 技师 / 顾问 / 前台 / STAFF

---

## 4. 后端接口

### 4.1 品牌管理

| Method | URL | 用途 |
|---|---|---|
| GET | `/org/brand/page` | 品牌分页 |
| POST | `/org/brand` | 新增 |
| PUT | `/org/brand/{id}` | 更新 |
| DELETE | `/org/brand/{id}` | 删除（有门店保护） |

### 4.2 数据范围

| Method | URL | 用途 |
|---|---|---|
| GET | `/org/scope/me` | 当前用户数据范围 |
| POST | `/org/scope/filter` | 拼接 WHERE 条件 |

### 4.3 员工-门店

| Method | URL | 用途 |
|---|---|---|
| POST | `/org/employee/store/bind` | 绑定 |
| POST | `/org/employee/store/unbind` | 解绑 |
| GET | `/org/employee/{id}/stores` | 员工关联门店 |
| GET | `/org/store/{id}/employees` | 门店员工列表 |

### 4.4 门店管理

| Method | URL | 用途 |
|---|---|---|
| GET | `/org/store/page` | 门店分页（含 brand_name） |
| PUT | `/org/store/{id}` | 更新门店 |
| GET | `/org/hq/brand/{brandId}/stores` | 品牌内门店列表 |

### 4.5 总部看板

| Method | URL | 用途 |
|---|---|---|
| GET | `/org/hq/dashboard` | 总部视角（自动按角色过滤） |

---

## 5. 前端

### 5.1 总部看板 `org/hq.vue`

- 4 卡片 KPI（数据范围 / 总净营收 / 总订单数 / 品牌数）
- 左：品牌级汇总（品牌名 / 门店数 / 会员数 / 订单数 / 净营收）
- 右：Top 10 门店（排名 / 门店名 / 订单数 / 净营收）
- 自动根据用户角色显示对应数据范围

### 5.2 品牌管理 `org/brand.vue`

- 列表：编码 / 名称 / 类型 / 三种共享策略 / 状态
- 编辑：三种共享策略独立配置
- 品牌下门店弹窗（只读展示）

### 5.3 门店管理 `org/store.vue`

- 列表：含品牌名 / 店长 / 总部标记 / 城市
- 员工弹窗：查看门店下所有员工

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v3_0_a.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 品牌 CRUD（含重复拒绝） | 5 |
| 2 | 门店升级字段 | 3 |
| 3 | 员工-门店绑定 | 6 |
| 4 | 数据范围 | 5 |
| 5 | 总部看板 | 6 |
| 6 | 删除保护 | 1 |
| **小计** | | **≥ 26** |

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 20 | ✅ ≥ 26 |
| 新增 SQL 表 | 2 | ✅ |
| 扩展表字段 | 1 | ✅ |
| 新增权限码 | 4 | ✅ |
| 新增角色 | 3 | ✅ |
| 新增 Vue 页面 | 3 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-A21 表结构与字典 | ✅ |
| T-A22 角色权限交叉测试 | ✅（BRAND_ADMIN 只能看 brand 内） |
| T-A23 总部/店长视角切换正确 | ✅（自动根据 roleCode 过滤） |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（R3.0-A 3 项任务全部完成） | R3.0-A |
