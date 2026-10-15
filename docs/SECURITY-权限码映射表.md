# SECURITY-权限码映射表（v1）

> 对应 `EXEC-美业SaaS升级执行任务清单-v2.0.md` 中 **R2.0-A T-A01** 产出物。
> 状态：v1 初稿（评审通过后冻结）。

---

## 1. 命名规范

### 1.1 格式

```
{模块}:{资源}:{动作}
```

- **模块**：2~4 个字母，对应 `M01~M12` 模块代号（不区分大小写）。
- **资源**：业务实体英文复数（`members` / `orders` / `members.balance`）。
- **动作**：固定动词 `view` / `create` / `update` / `delete` / `export` / `import` / `audit`。

示例：
- `crm:member:view`
- `pos:order:refund`
- `stock:inventory:export`

### 1.2 特殊后缀

| 后缀 | 含义 |
|---|---|
| `:own` | 仅本人数据（如店员看自己的提成） |
| `:team` | 本门店/本团队 |
| `:brand` | 本品牌 |
| `:all`  | 全租户（与 `dataScope=ALL` 配合） |

示例：`crm:member:view.own`

### 1.3 通配

- `*:view` 表示对所有资源的查看权限（极少使用，建议废弃）
- `module:*` 表示对整个模块的所有权限（如 `stock:*`）

---

## 2. 模块清单（v1 收敛到 10 个模块）

| 模块码 | 模块名 | 主要资源 |
|---|---|---|
| `auth`    | 登录认证 | user, role, permission |
| `crm`     | 会员 | member, memberTag, memberLevel |
| `booking` | 预约 | booking, bookingResource |
| `package` | 套餐 | package, packageOrder |
| `pos`     | 收银 | order, orderItem, payment, refund |
| `stock`   | 库存 | product, inventory, inventoryRecord |
| `commission` | 提成 | rule, record, settlementBatch |
| `marketing`  | 营销 | campaign, coupon, points |
| `report`  | 报表 | dashboard, salesReport, memberReport |
| `system`  | 系统 | menu, config, log |

---

## 3. 权限码全量清单

### 3.1 auth（登录认证）

| 权限码 | 名称 | 说明 |
|---|---|---|
| `auth:user:view` | 用户查看 | 用户列表 |
| `auth:user:create` | 用户创建 | 新增用户 |
| `auth:user:update` | 用户编辑 | 编辑用户 |
| `auth:user:delete` | 用户删除 | 删除用户 |
| `auth:user:resetPwd` | 重置密码 | |
| `auth:role:view` | 角色查看 | |
| `auth:role:create` | 角色创建 | |
| `auth:role:update` | 角色编辑 | |
| `auth:role:delete` | 角色删除 | |
| `auth:role:assign` | 分配角色 | |

### 3.2 crm（会员）

| 权限码 | 名称 |
|---|---|
| `crm:member:view` | 会员查看 |
| `crm:member:create` | 会员新增 |
| `crm:member:update` | 会员编辑 |
| `crm:member:delete` | 会员删除 |
| `crm:member:export` | 会员导出 |
| `crm:member:recharge` | 会员充值 |
| `crm:member:level:update` | 调整等级 |
| `crm:memberTag:view` | 标签查看 |
| `crm:memberTag:manage` | 标签管理 |

### 3.3 booking（预约）

| 权限码 | 名称 |
|---|---|
| `booking:booking:view` | 预约查看 |
| `booking:booking:create` | 预约创建 |
| `booking:booking:update` | 预约改期 |
| `booking:booking:cancel` | 预约取消 |
| `booking:resource:view` | 资源查看 |
| `booking:resource:manage` | 资源管理 |

### 3.4 package（套餐）

| 权限码 | 名称 |
|---|---|
| `package:product:view` | 套餐查看 |
| `package:product:manage` | 套餐管理 |
| `package:order:view` | 套餐订单查看 |
| `package:order:refund` | 套餐退单 |

### 3.5 pos（收银）

| 权限码 | 名称 |
|---|---|
| `pos:order:view` | 订单查看 |
| `pos:order:create` | 开单收银 |
| `pos:order:refund` | 整单退款 |
| `pos:order:partialRefund` | 部分退款 |
| `pos:payment:create` | 收款 |
| `pos:payment:reverse` | 冲正 |
| `pos:report:daily` | 日结 |
| `pos:report:export` | 导出 |

### 3.6 stock（库存）

| 权限码 | 名称 |
|---|---|
| `stock:product:view` | 商品查看 |
| `stock:product:manage` | 商品管理 |
| `stock:inventory:view` | 库存查看 |
| `stock:inventory:in` | 入库 |
| `stock:inventory:out` | 出库 |
| `stock:inventory:check` | 盘点 |
| `stock:inventory:export` | 导出 |

### 3.7 commission（提成）

| 权限码 | 名称 |
|---|---|
| `commission:rule:view` | 规则查看 |
| `commission:rule:manage` | 规则管理 |
| `commission:record:view` | 提成查看 |
| `commission:record:view.own` | 仅看本人 |
| `commission:batch:create` | 批次生成 |
| `commission:batch:audit` | 批次审核 |
| `commission:batch:pay` | 批次发放 |

### 3.8 marketing（营销）

| 权限码 | 名称 |
|---|---|
| `marketing:campaign:view` | 活动查看 |
| `marketing:campaign:manage` | 活动管理 |
| `marketing:coupon:issue` | 发券 |
| `marketing:points:adjust` | 积分调整 |

### 3.9 report（报表）

| 权限码 | 名称 |
|---|---|
| `report:dashboard:view` | 看板 |
| `report:sales:view` | 销售报表 |
| `report:member:view` | 会员报表 |
| `report:r2:export` | R2 报表导出（含水印） |

### 3.10 system（系统）

| 权限码 | 名称 |
|---|---|
| `system:menu:manage` | 菜单管理 |
| `system:tenant:manage` | 租户管理 |
| `system:log:view` | 审计日志 |
| `system:config:manage` | 系统配置 |

---

## 4. 角色默认权限矩阵（v1）

| 角色 | dataScope | 默认权限 |
|---|---|---|
| `SUPER_ADMIN` | ALL | `*`（全权限） |
| `STORE_ADMIN` | STORE | `auth:user:view`、`auth:user:create`、`crm:*`（自身门店）、`pos:*`、`stock:*`、`report:*`、`booking:*`、`package:*` |
| `EMPLOYEE` | SELF | `crm:member:view`、`pos:order:create`、`commission:record:view.own` |

> 注：员工 `dataScope=SELF` 时，`tenant_line` 自动按 `tenant_id` 过滤，
> 业务层 `DataScopeInterceptor` 还会校验 `storeIds`。

---

## 5. 数据范围语义

| dataScope | SQL 拼接策略 |
|---|---|
| `ALL`   | 不加 store 条件 |
| `BRAND` | `WHERE brand_id = ?` |
| `STORE` | `WHERE store_id IN (ctx.storeIds)` |
| `SELF`  | `WHERE store_id = ctx.user.store_id AND (owner = user_id OR ...)` |

实现见：
- `security/RbacContextLoader.java`（上下文组装）
- `security/DataScopeInterceptor.java`（Controller 层兜底）
- `config/MybatisPlusConfig.java`（`TenantLineInnerInterceptor`，自动注入 `tenant_id`）

---

## 6. 评审清单（评审通过后打勾）

- [ ] 权限码命名无歧义
- [ ] 模块划分与代码 `M01~M12` 对齐
- [ ] 角色默认权限矩阵与 `sys_role` 初始化脚本一致
- [ ] 数据范围 4 种语义有 SQL 拼接样例
- [ ] 与前端 `v-permission` 指令编码一致
