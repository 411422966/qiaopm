# EXEC-R3.0-B：跨店会员与核销

> 范围：R3.0-B《跨店会员与核销》(T-B21 ~ T-B23)
> 关联文档：`init_rbac_v3_0_b.sql`、`MODULE-模块化开发验证体系.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-B21 | 跨店会员规则：会员共享/积分共享/储值策略 | ✅ | `crm_cross_store_rule` + `crm_member_store` + `/crm/cross/rule` |
| T-B22 | 跨店核销策略：支持/拒绝，结算规则 | ✅ | `crm_cross_store_redemption` + `/crm/cross/redemption` |
| T-B23 | 跨店核销 UI 与提示 | ✅ | `crm/cross-rule.vue` + `crm/cross-record.vue` + 跨店识别弹窗 |

---

## 2. 数据库设计

### 2.1 新增 3 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `crm_cross_store_rule` | 跨店规则（按品牌或租户通用） | `package_cross_mode / balance_cross_mode / cross_fee_rate / cross_fee_type / cross_max_amount` |
| `crm_cross_store_redemption` | 跨店核销流水 | `source_store_id / target_store_id / redemption_type / cross_fee / net_amount / rule_id / rule_snapshot` |
| `crm_member_store` | 会员-门店多对多 | `relation_type ORIGIN/CROSS` + `visit_count` |

### 2.2 跨店模式

| 字段 | 选项 | 说明 |
|---|---|---|
| `package_cross_mode` | BRAND品牌内 / CHAIN连锁内 / ANY任意门店 / DISABLED禁止 | 套餐/项目核销范围 |
| `balance_cross_mode` | BRAND品牌内 / STORE仅本门店 / DISABLED禁止 | 储值使用范围 |

### 2.3 跨店手续费

| 类型 | 说明 |
|---|---|
| NONE | 不收 |
| PERCENT | 按原金额 × rate% |
| FIXED | 固定金额 |

### 2.4 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `crm:cross-rule` | 跨店规则 | menu |
| `crm:cross-record` | 跨店核销 | menu |

---

## 3. 跨店识别与核销流程

```
收银员输入手机号
    ↓
/crm/cross/member/lookup
    ↓
查 crm_member WHERE phone = ?
    ↓
判定：isCross = (member.store_id ≠ 当前门店)
    ↓
匹配规则：
   1. brand_id 级（按会员归属门店 brand）
   2. 租户通用级（brand_id IS NULL）
   取 enabled=1, priority DESC, LIMIT 1
    ↓
返回 {member, isCross, relatedStores, matchedRule, rulePreview}
    ↓
UI 弹窗提示"跨店识别 + 规则预览"
    ↓
确认 → /crm/cross/redemption
   - 校验每种类型（PACKAGE/BALANCE/POINTS/COUPON/DISCOUNT）
   - 计算 cross_fee
   - 校验 cross_max_amount
   - 插入 crm_cross_store_redemption
   - 同步 crm_member_store（新建/累加 visit_count）
```

---

## 4. 后端接口

### 4.1 跨店规则

| Method | URL | 用途 |
|---|---|---|
| GET | `/crm/cross/rule/page` | 规则分页 |
| POST | `/crm/cross/rule` | 新增 |
| PUT | `/crm/cross/rule/{id}` | 更新 |
| DELETE | `/crm/cross/rule/{id}` | 删除 |

### 4.2 跨店识别

| Method | URL | 用途 |
|---|---|---|
| GET | `/crm/cross/member/lookup?phone=` | 按手机号识别会员 |

### 4.3 跨店核销

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/cross/redemption` | 提交跨店核销 |
| POST | `/crm/cross/redemption/{id}/revoke` | 撤销 |
| GET | `/crm/cross/redemption/page` | 核销记录分页 |

### 4.4 会员-门店关联

| Method | URL | 用途 |
|---|---|---|
| GET | `/crm/cross/member-store` | 会员/门店关联查询 |

---

## 5. 前端

### 5.1 跨店规则 `crm/cross-rule.vue`

- 列表：规则名 / 品牌范围 / 套餐模式 / 积分 / 储值模式 / 优惠券 / 手续费 / 优先级 / 状态
- 弹窗：每种模式独立配置（BRAND/CHAIN/ANY/DISABLED/STORE）
- 实时启用/禁用

### 5.2 跨店核销 `crm/cross-record.vue`

- 列表：订单号 / 会员 / 核销类型 / 归属→核销门店 / 原金额 / 手续费 / 实收 / 状态
- 撤销按钮（仅未撤销可点）
- 跨店识别弹窗：输入手机号 → 展示会员信息 + 跨店告警 + 规则预览表 + 关联门店列表

### 5.3 跨店识别弹窗（核心 UI）

```
┌──────────────────────────────┐
│ 跨店会员识别                   │
│ 手机号：[13900001234   ] [查询]│
│ ─────────────────────────────  │
│ 会员：张三   会员号：M001      │
│ 手机：13900001234              │
│ 积分：1500 / 储值：¥500       │
│ ─────────────────────────────  │
│ ⚠️ 当前为跨店识别              │
│ ─────────────────────────────  │
│ 跨店规则预览                   │
│   套餐/项目核销    [允许] BRAND│
│   积分共享         [允许]      │
│   储值使用         [允许] BRAND│
│   折扣共享         [允许]      │
│   跨店手续费       [允许] 2%   │
│ ─────────────────────────────  │
│ 会员关联门店                   │
│   总部店  美业品牌  归属  10次 │
│   分店A  美业品牌  关联   3次  │
└──────────────────────────────┘
```

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v3_0_b.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 跨店规则 CRUD | 4 |
| 2 | 跨店识别（手机号 + 不存在） | 4 |
| 3 | 跨店核销（PACKAGE/BALANCE/POINTS） | 9 |
| 4 | 会员-门店关联 | 2 |
| 5 | 清理 | 1 |
| **小计** | | **≥ 20** |

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 20 | ✅ ≥ 20 |
| 新增 SQL 表 | 3 | ✅ |
| 新增权限码 | 2 | ✅ |
| 新增 Vue 页面 | 2 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-B21 规则可配置 | ✅（6 种 cross_mode × cross_fee） |
| T-B22 跨店核销流水准确 | ✅（自动写入 source→target + fee 计算） |
| T-B23 提示和审计可见 | ✅（跨店弹窗 + 记录列表 + 撤销） |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（R3.0-B 3 项任务全部完成） | R3.0-B |
