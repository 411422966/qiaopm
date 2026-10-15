# 美业 SaaS 菜单角色映射表

> 状态：v0.1 | 2026-08-16 | 已执行

## 一、角色定义

| role_code | 角色名 | 数据范围 | 说明 |
|---|---|---|---|
| CLERK | 店员 | STORE | 一线操作员 |
| MANAGER | 店长 | STORE | 门店管理者 |
| BOSS | 老板 | STORE | 企业主 |
| OPERATOR | 运营 | STORE | 运营专员 |
| ADMIN | 系统管理员 | ALL | 技术管理员 |
| SUPER_ADMIN | 超级管理员 | ALL | 内置角色 |
| PLATFORM_ADMIN | 平台管理员 | ALL | 内置角色 |

## 二、菜单角色映射矩阵

| 菜单模块 | CLERK 店员 | MANAGER 店长 | BOSS 老板 | OPERATOR 运营 | ADMIN 管理员 |
|---|---|---|---|---|---|
| 首页 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 收银结算 | ✅ | ✅ | ✅ | – | ✅ |
| 预约管理 | ✅ | ✅ | ✅ | – | ✅ |
| 会员管理 | ✅ | ✅ | ✅ | – | ✅ |
| 储值管理 | ✅ | ✅ | ✅ | – | ✅ |
| 套餐次卡 | ✅ | ✅ | ✅ | – | ✅ |
| 库存管理 | ✅ | ✅ | ✅ | – | ✅ |
| 基础资料 | ✅ | ✅ | ✅ | – | ✅ |
| 员工提成 | – | ✅ | ✅ | – | ✅ |
| 数据报表 | – | ✅ | ✅ | ✅ | ✅ |
| 营销工具 | – | ✅ | ✅ | ✅ | ✅ |
| 客户运营 | – | – | ✅ | ✅ | ✅ |
| 组织架构 | – | – | ✅ | – | ✅ |
| 医美合规 | – | ✅ | ✅ | – | ✅ |
| 系统设置 | – | – | ✅ | – | ✅ |
| 研发工具 | – | – | – | – | ✅ |

> 注：✅= 显示该模块，–= 不显示

## 三、路由权限码绑定

| 路由路径 | 权限码 | 所在角色 |
|---|---|---|
| `/dashboard` | 无（白名单） | 全部 |
| `/member` | `member:list` | CLERK+ |
| `/wallet/recharge` | `wallet:list` | CLERK+ |
| `/wallet/transactions` | `wallet:list` | CLERK+ |
| `/package` | `package:list` | CLERK+ |
| `/booking/*` | `booking:list` / `booking:schedule:*` | CLERK+ |
| `/pos/*` | `pos:*` | CLERK+ |
| `/stock/*` | `stock:list` / `stock:*` | CLERK+ |
| `/commission/*` | `commission:*` | MANAGER+ |
| `/market/*` | `market:*` / `mkt:*` | MANAGER+ |
| `/report/*` | `report:*` | MANAGER+ / OPERATOR |
| `/crm/*` | `crm:*` | BOSS+ / OPERATOR |
| `/org/*` | `org:*` | BOSS+ |
| `/mh/*` | `mh:*` | MANAGER+ |
| `/sys/*` | `setting` / `sys:*` | BOSS+ |
| `/dev/*` | `dev:access` | ADMIN 仅 |

## 四、SQL 执行记录

执行文件：`backend/src/main/resources/db/sql/migrate_r3_0_c_menu_role.sql`

- 创建角色：CLERK / MANAGER / BOSS / OPERATOR / ADMIN
- 绑定数据范围：全部为 STORE，ADMIN 为 ALL
- 菜单权限码按角色绑定
- 菜单命名优化更新

## 五、菜单命名优化

| 原标题 | 新标题 |
|---|---|
| 商品/耗材 | 商品耗材 |
| LTV + Churn 模型训练 | LTV流失预测 |
| 护肤方案 + 共享套餐 | 护肤方案共享套餐 |
| 电子价目表 + 卡项套餐 | 电子价目表卡项套餐 |
| P1 套件（10 项） | 套件合集 |
| P2 套件（7 项） | 套件合集 |
| P3 套件（4 项） | 套件合集 |
| 补齐套件（4 项） | 套件合集 |

## 六、重复路由清理

- `/market/coupon` → 复用 `@/views/mkt/coupon.vue`
- `/market/points` → 复用 `@/views/mkt/points.vue`
- `/market/activity` → 复用 `@/views/mkt/promotion.vue`
- `views/market/` 目录已删除

## 七、变更记录

| 日期 | 变更内容 |
|---|---|
| 2026-08-16 | 初始版本：创建 5 个预置角色 + 菜单绑定 + 命名优化 + 路由清理 |
