# EXEC-R3.0-C：评价回访与客诉

> 范围：R3.0-C《评价回访与客诉》(T-C21 ~ T-C24)
> 关联文档：`init_rbac_v3_0_c.sql`、`MODULE-模块化开发验证体系.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-C21 | `crm_follow_up_task` + 回访计划（疗程后/生日/到期） | ✅ | `crm_follow_up_task` + `crm_follow_up_plan` + 3 个默认计划 |
| T-C22 | `crm_complaint_ticket` 工单状态机 | ✅ | 7 状态 + `crm_complaint_log` 完整轨迹 |
| T-C23 | 评价模板 + 微信小程序触达 | ✅ | `crm_review` + `crm_review_template` + 低分自动建工单 |
| T-C24 | 客户运营菜单 + 回访任务面板 | ✅ | 6 个 Vue 页 + 客户运营看板 |

---

## 2. 数据库设计

### 2.1 新增 6 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `crm_follow_up_task` | 回访任务 | `task_type / planned_at / sla_expire_at / status / result_type / rating` |
| `crm_follow_up_plan` | 回访计划模板 | `trigger_event / trigger_offset_days / subject_template / sla_minutes` |
| `crm_review` | 评价（综合 + 4 维度） | `overall_rating / service_rating / skill_rating / environment_rating / price_rating / tags` |
| `crm_review_template` | 评价模板 | `dimensions / tags_pool / touch_channel / touch_delay_minutes` |
| `crm_complaint_ticket` | 投诉工单 | `complaint_type / level / status / handler_id / sla_expire_at / solution_type` |
| `crm_complaint_log` | 投诉日志（状态机轨迹） | `action_type / from_status / to_status / content` |

### 2.2 任务类型

| task_type | 触发时机 |
|---|---|
| POST_TREATMENT | 疗程完成后 |
| BIRTHDAY | 生日当天 |
| EXPIRE | 套餐到期前 N 天 |
| COMPLAINT | 投诉回访 |
| CUSTOM | 自定义 |

### 2.3 投诉状态机（7 状态）

```
1待分配 ──assign──> 2已分配 ──process──> 3处理中 ──reply──> 4等待客户 ──resolve──> 5已解决 ──close──> 6已关闭
            │                       │                            │
            │                       └──escalate──> 7已升级 <─────┘
            └──escalate──> 7已升级
```

### 2.4 解决方案

| solution_type | 说明 |
|---|---|
| REFUND | 退款（含 refund_amount） |
| EXCHANGE | 更换 |
| APOLOGY | 道歉 |
| COMPENSATION | 赔偿 |
| NONE | 无需处理 |

### 2.5 权限码

| 权限码 | 名称 |
|---|---|
| `crm:follow-up` | 回访任务 |
| `crm:follow-plan` | 回访计划 |
| `crm:review` | 客户评价 |
| `crm:review-tpl` | 评价模板 |
| `crm:complaint` | 投诉工单 |

---

## 3. 关键流程

### 3.1 回访计划触发

```
@Scheduled / 手动触发
    ↓
POST /crm/care/follow/plan/{planId}/trigger
    ↓
按 planType 拉会员：
   - POST_TREATMENT：N 天前完成的订单
   - BIRTHDAY：今天生日
   - EXPIRE：N 天内到期
    ↓
幂等检查（plan_id + member_id + source_no）
    ↓
批量插入 crm_follow_up_task (status=1)
    ↓
返回 generatedCount
```

### 3.2 投诉状态机推进

```
POST /crm/care/complaint  → status=1 待分配
    ↓
POST /assign → status=2 已分配（写日志）
    ↓
POST /process → status=3 处理中（写日志）
    ↓
POST /reply → status=4 等待客户（写日志）
    ↓
POST /resolve → status=5 已解决（写日志 + solution）
    ↓
POST /close → status=6 已关闭（写日志 + satisfaction）
```

### 3.3 低分自动建工单

```
POST /crm/care/review {overallRating: 1~2}
    ↓
INSERT crm_review (status=2)
    ↓
if (overallRating <= 2 && memberId != null)
    ↓
自动创建 crm_complaint_ticket
   - level: 1 星 = 4 紧急 / 2 星 = 2 较严重
   - source_type: REVIEW
   - status: 1 待分配
   - SLA: 24h
```

---

## 4. 后端接口

### 4.1 回访任务

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/care/follow/task` | 创建任务 |
| GET | `/crm/care/follow/task/page` | 分页查询 |
| GET | `/crm/care/follow/task/{id}` | 任务详情 |
| POST | `/crm/care/follow/task/{id}/start` | 开始 |
| POST | `/crm/care/follow/task/{id}/finish` | 完成 |
| POST | `/crm/care/follow/task/{id}/close` | 关闭 |
| POST | `/crm/care/follow/task/{id}/assign` | 分配 |

### 4.2 回访计划

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/care/follow/plan` | 新增计划 |
| GET | `/crm/care/follow/plan/list` | 计划列表 |
| POST | `/crm/care/follow/plan/{id}/trigger` | 触发生成 |

### 4.3 投诉工单

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/care/complaint` | 创建工单 |
| GET | `/crm/care/complaint/{id}` | 详情（含日志） |
| GET | `/crm/care/complaint/page` | 分页 |
| POST | `/crm/care/complaint/{id}/assign` | 分配 |
| POST | `/crm/care/complaint/{id}/process` | 处理 |
| POST | `/crm/care/complaint/{id}/reply` | 回复 |
| POST | `/crm/care/complaint/{id}/resolve` | 解决 |
| POST | `/crm/care/complaint/{id}/close` | 关闭 |
| POST | `/crm/care/complaint/{id}/escalate` | 升级 |

### 4.4 评价

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/care/review` | 填写评价 |
| GET | `/crm/care/review/page` | 评价列表 |
| POST | `/crm/care/review/{id}/reply` | 商家回复 |
| POST | `/crm/care/review/{id}/hide` | 隐藏 |

### 4.5 评价模板

| Method | URL | 用途 |
|---|---|---|
| POST | `/crm/care/review/tpl` | 新增模板 |
| GET | `/crm/care/review/tpl/list` | 模板列表 |

### 4.6 客户运营看板

| Method | URL | 用途 |
|---|---|---|
| GET | `/crm/care/ops/overview` | 运营汇总 |

---

## 5. 前端

| 页面 | 路由 | 功能 |
|---|---|---|
| 客户运营看板 | `/crm/care/overview` | 待办/逾期 KPI + 低分预警 |
| 回访任务 | `/crm/care/follow-task` | 任务列表 + 分配/开始/完成/关闭 |
| 回访计划 | `/crm/care/follow-plan` | 计划模板 + 一键触发 |
| 投诉工单 | `/crm/care/complaint` | 工单列表 + 7 状态机操作 + 日志时间线 |
| 客户评价 | `/crm/care/review` | 评价列表 + 维度评分 + 回复 |
| 评价模板 | `/crm/care/review-tpl` | 模板配置 |

### 工单详情弹窗（核心 UI）

```
┌────────────────────────────────────────┐
│ 工单详情 TC1234567890                   │
│ ┌────────────────┬─────────────────┐    │
│ │ 工单号: ...    │ 状态: 处理中   │    │
│ │ 会员: 张三    │ 级别: 较严重    │    │
│ │ 类型: 服务    │ 处理人: 店长A   │    │
│ │ 主题: ...                       │    │
│ │ 内容: ...                       │    │
│ └────────────────┴─────────────────┘    │
│                                          │
│ 状态机日志                              │
│  ✓ CREATE  — → 1                        │
│  ✓ ASSIGN  1 → 2  (店长A)              │
│  ✓ REPLY   2 → 3  (店长A)              │
│  · ...                                   │
└────────────────────────────────────────┘
```

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v3_0_c.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 回访任务 CRUD + 状态流转 | 9 |
| 2 | 回访计划 + 触发 | 4 |
| 3 | 投诉工单状态机（5 步全流程） | 13 |
| 4 | 升级 + 重复 close 拒绝 | 2 |
| 5 | 评价（含低分自动建工单） | 5 |
| 6 | 评价模板 | 3 |
| 7 | 客户运营看板 | 4 |
| **小计** | | **≥ 40** |

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 30 | ✅ ≥ 40 |
| 新增 SQL 表 | 6 | ✅ |
| 新增权限码 | 5 | ✅ |
| 新增 Vue 页面 | 6 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-C21 自动创建任务并可关闭 | ✅（4 种 planType 触发 + 4 种状态） |
| T-C22 投诉闭环 SLA 监控 | ✅（sla_expire_at + 看板逾期统计） |
| T-C23 评价页可填写并记录 | ✅（4 维度评分 + 标签 + 回复 + 低分自动工单） |
| T-C24 任务分配与提醒 | ✅（分配弹窗 + 看板 SLA 红字告警） |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（R3.0-C 4 项任务全部完成） | R3.0-C |
