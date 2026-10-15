# EXEC-R2.1-B：资源管理与候补爽约

> 范围：R2.1-B《资源管理与候补爽约》(T-B11 ~ T-B17)
> 关联文档：`init_rbac_v2_1_b.sql`、`MODULE-模块化开发验证体系.md`、`EXEC-美业SaaS升级执行任务清单-v2.0.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-B11 | `booking_resource` / `booking_resource_link` / `booking_booking_resource` / `booking_waitlist` / `booking_no_show_rule` / `member_credit` / `member_credit_log` | ✅ | `init_rbac_v2_1_b.sql` |
| T-B12 | 资源 CRUD + 资源与项目绑定 | ✅ | `BookingResourceService` |
| T-B13 | 预约校验升级：多资源冲突、可用时段推荐 | ✅ | `/biz/booking/resource/check-conflict` + `/available-slots` |
| T-B14 | 候补队列：取消/爽约后自动补位 | ✅ | `/waitlist/auto-fill` |
| T-B15 | 爽约规则：扣费、信用分、锁定 N 天 | ✅ | `/no-show-rule/trigger` + `member_credit` |
| T-B16 | 资源管理页、候补队列页、爽约统计页 | ✅ | 3 个 Vue 页 |
| T-B17 | 资源/候补 E2E 与导出 | ✅ | `test-rbac-v2_1-b.ps1` |

---

## 2. 数据库设计

### 2.1 新增 7 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `booking_resource` | 资源字典（ROOM/BED/DEVICE） | `resource_code / resource_type / capacity / available_from / available_to` |
| `booking_resource_link` | 资源-项目 绑定 | `resource_id / service_item_id / is_required / priority` |
| `booking_booking_resource` | 预约实际占用资源（多资源） | `booking_id / resource_id / quantity` |
| `booking_waitlist` | 候补队列 | `member_id / service_item_id / preferred_date / preferred_time_from / priority / status` |
| `booking_waitlist_notify` | 候补通知日志 | `waitlist_id / notify_type / target / response` |
| `booking_no_show_rule` | 爽约规则 | `no_show_count_threshold / lock_days / credit_penalty / fee_penalty` |
| `member_credit_log` | 会员信用分变更日志 | `change_type / change_value / credit_after / fee_penalty / locked_until` |
| `member_credit` | 会员信用汇总 | `credit / no_show_count / total_bookings / locked_until` |

### 2.2 演示数据

| 表 | 数据量 | 备注 |
|---|---|---|
| `booking_resource` | 5 | VIP1/2、标准3号床、足疗床1号、射频仪A |
| `booking_resource_link` | 8+ | 自动绑定到现有 5 个服务项目 |
| `booking_no_show_rule` | 2 | 标准规则 + 严格规则 |
| `member_credit` | 已 init | 已有 crm_member 全部初始化 = 100 分 |

### 2.3 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `biz:booking:resource` | 预约资源管理 | menu |
| `biz:booking:waitlist` | 候补队列 | menu |
| `biz:booking:no-show` | 爽约管理 | menu |

---

## 3. 后端接口

### 3.1 资源 + 绑定

| Method | URL | 用途 |
|---|---|---|
| GET | `/biz/booking/resource/types` | 资源类型字典 |
| GET | `/biz/booking/resource/page` | 资源分页 |
| GET | `/biz/booking/resource/list` | 资源列表 |
| GET | `/biz/booking/resource/{id}` | 资源详情（含绑定项目） |
| POST | `/biz/booking/resource` | 新增 |
| PUT | `/biz/booking/resource/{id}` | 更新 |
| DELETE | `/biz/booking/resource/{id}` | 删除（被占用时拒绝） |
| POST | `/biz/booking/resource/{id}/link` | 绑定项目 |
| DELETE | `/biz/booking/resource/{id}/link/{serviceItemId}` | 解绑 |
| GET | `/biz/booking/resource/available?serviceItemId=` | 服务的可选资源 |
| GET | `/biz/booking/resource/check-conflict` | 检查冲突 |
| GET | `/biz/booking/resource/available-slots` | 计算可选时段 |
| GET | `/biz/booking/resource/export` | 导出 CSV |

### 3.2 候补队列

| Method | URL | 用途 |
|---|---|---|
| POST | `/biz/booking/waitlist` | 新增候补 |
| GET | `/biz/booking/waitlist/page` | 候补分页 |
| POST | `/biz/booking/waitlist/{id}/cancel` | 取消候补 |
| POST | `/biz/booking/waitlist/auto-fill` | 自动补位（按优先级 + 时段匹配） |
| GET | `/biz/booking/waitlist/export` | 导出 CSV |

### 3.3 爽约规则 + 信用

| Method | URL | 用途 |
|---|---|---|
| GET | `/biz/booking/no-show-rule/list` | 规则列表 |
| POST | `/biz/booking/no-show-rule` | 新增规则 |
| PUT | `/biz/booking/no-show-rule/{id}` | 更新规则 |
| DELETE | `/biz/booking/no-show-rule/{id}` | 删除规则 |
| POST | `/biz/booking/no-show-rule/trigger` | 触发爽约（扣分+扣费+锁定） |
| GET | `/biz/booking/member-credit?memberId=` | 会员信用查询 |
| GET | `/biz/booking/no-show/stats` | 爽约统计 |

---

## 4. 关键算法

### 4.1 多资源冲突检测

```sql
SELECT b.id, b.booking_no, ...
FROM biz_booking b
JOIN booking_booking_resource br ON br.booking_id = b.id AND br.deleted = 0
WHERE br.resource_id = :resourceId
  AND b.deleted = 0
  AND b.booking_date = :date
  AND b.status IN (1, 2, 3)            -- 待确认/已确认/已签到
  AND TIME(b.start_time) < :endTime
  AND TIME(b.end_time)   > :startTime
```

### 4.2 可用时段推荐

从资源 `available_from` 到 `available_to` 按 30 分钟步长遍历，
排除已占用时段，得到可选 start 时刻。

### 4.3 自动补位

```sql
SELECT id, member_name, member_phone, priority
FROM booking_waitlist
WHERE deleted = 0
  AND status = 1                           -- 等待
  AND service_item_id = :sid
  AND preferred_date = :date
  AND preferred_time_from < :endTime
  AND preferred_time_to   > :startTime
  AND (employee_id IS NULL OR employee_id = :eid)
ORDER BY priority DESC, create_time ASC
LIMIT 5
```

写入 `booking_waitlist_notify` + 更新 `status = 2, notify_count++`。

### 4.4 爽约处理

```
1. 取 sort_order 最小、enabled=1 的规则
2. member_credit：
   - no_show_count = 旧值 + 1
   - credit = max(0, 旧值 - credit_penalty)
   - locked_until = IF(count >= threshold, NOW() + lock_days, NULL)
3. member_credit_log：
   - change_type = 'NO_SHOW'
   - change_value = -credit_penalty
   - credit_after = 新值
   - fee_penalty = 规则配置的扣费
   - locked_until = 触发锁定时写入
   - remark = 累计说明
```

所有更新使用 `INSERT ... ON DUPLICATE KEY UPDATE` 兜底兼容首次触发。

---

## 5. 前端

### 5.1 资源管理页 `booking/resource.vue`

- 5 卡片 KPI（总数/启用/房间/床位/设备）
- 顶栏：搜索 / 类型 / 状态 / 新增 / 导出
- 列表：编码 / 名称 / 类型 / 容量 / 位置 / 时段 / 状态 / 操作
- 编辑弹窗：含 `availableFrom/To`（time-picker）+ 容量 + 类型
- 绑定项目弹窗：动态加载服务项目列表

### 5.2 候补队列页 `booking/waitlist.vue`

- 5 状态卡片（等待/已通知/已转预约/已取消/已过期）
- 顶栏：搜索 / 状态 / 新增 / 自动补位 / 导出
- 列表：候补号 / 会员 / 电话 / 项目 / 技师 / 期望日期 / 状态 / 通知次数
- 自动补位弹窗：选项目 + 日期 + 时段 → 服务端按优先级排序通知

### 5.3 爽约管理页 `booking/no-show.vue`

- 5 卡片 KPI（累计爽约/当前锁定/低信用/调整总次数/最近7天）
- Tab 1：规则配置（CRUD）
- Tab 2：会员信用查询（按 memberId 拉 credit + 日志）
- 触发爽约：弹窗输入会员ID/预约ID → 立即扣分

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v2_1-b.ps1`

覆盖 7 大类：

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 资源类型字典 | 1 |
| 2 | 资源 CRUD + 冲突检测 + 可用时段 | 5 |
| 3 | 资源-项目绑定 | 3 |
| 4 | 候补队列 + 自动补位 + 取消 | 5 |
| 5 | 爽约规则 + 触发 + 阈值锁定 + 信用查询 | 8 |
| 6 | 导出 CSV | 4 |
| 7 | 清理 | 3 |
| **小计** | | **≥ 29** |

### 6.3 集成测试主入口

`Run-All-Tests.ps1` 已加入 R2.1-B 条目。

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 25 | ✅ ≥ 29 |
| 新增 SQL 表 | 7 | ✅ |
| 新增权限码 | 3 | ✅ |
| 新增 Vue 页面 | 3 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-B11 表结构与初始化 | ✅ |
| T-B12 资源可设置容量、可用时段 | ✅ |
| T-B13 复杂预约冲突明确 errorCode | ✅ |
| T-B14 候补成员按优先级收到提醒 | ✅ |
| T-B15 规则可配置，扣费可追溯 | ✅ |
| T-B16 全部可视、可操作 | ✅ |
| T-B17 5 条用例通过 | ✅ ≥ 29 |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（基于 R2.1-B 7 项任务全部完成） | R2.1-B |
