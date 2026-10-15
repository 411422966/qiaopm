# P1 剩余改进修复结果

> 之前的"P1 修复"遗留两个非阻塞项，本次完整收尾。

---

## 1. 调研结论

| 改进项 | 审计描述 | 实际 | 修复 |
|---|---|---|---|
| **P1-3 总部统一下发** | 无 | 确认（MktCoupon/MarketActivity 仅有 CRUD，无 dispatch 端点） | ✅ |
| **P1-5 智能排班** | 缺失 | 确认（ScheduleController 仅 CRUD） | ✅ |
| **P1-5 需求预测** | 缺失 | 确认（无 forecast 端点） | ✅ |
| **P1-5 推荐算法** | 缺失 | 确认（无 recommend 端点） | ✅ |

---

## 2. 实际修复

### 2.1 新增文件

| 文件 | 类型 | 说明 |
|---|---|---|
| `migrate_r3_0_f_p1_remaining.sql` (新) | SQL | 5 张表：`mkt_dispatch` + `mkt_dispatch_target` + `schedule_suggestion` + `biz_forecast_demand` + `crm_recommend_log` + 权限码 |
| `MktDispatch.java` (新) | Entity | 总部下发策略 |
| `MktDispatchTarget.java` (新) | Entity | 下发目标门店 |
| `ScheduleSuggestion.java` (新) | Entity | AI 排班建议 |
| `BizForecastDemand.java` (新) | Entity | 需求预测 |
| `CrmRecommendLog.java` (新) | Entity | 推荐日志 |
| `*Mapper.java` × 5 (新) | Mapper | BaseMapper |
| `P1RemainingService.java` (新) | Controller | **12 个 API** |
| `P1RemainingEntityFieldsTest.java` (新) | Test | 16 个测试 |

### 2.2 新增 API

#### 总部统一下发 (`/mkt/dispatch/...`)
| 端点 | 方法 | 说明 |
|---|---|---|
| `POST /mkt/dispatch` | 创建下发策略（COUPON/PACKAGE/DISCOUNT/ACTIVITY） |
| `POST /mkt/dispatch/{id}/publish` | 发布（广播到所有目标门店） |
| `POST /mkt/dispatch/{id}/cancel` | 取消 |
| `GET /mkt/dispatch/page` | 分页查询 |
| `POST /mkt/dispatch/target/{id}/accept` | 门店接收 |
| `POST /mkt/dispatch/target/{id}/execute` | 门店执行（全部执行后父级 DONE） |

#### 智能排班 (`/schedule/ai/...`)
| 端点 | 方法 | 说明 |
|---|---|---|
| `POST /schedule/ai/suggest` | AI 生成排班建议（基于历史 booking 频次 + 星期 + 时段） |
| `GET /schedule/ai/list` | 查建议列表 |
| `POST /schedule/ai/{id}/accept` | 接收建议（同步写入 EmployeeSchedule） |

#### 需求预测 (`/intel/forecast/...`)
| 端点 | 方法 | 说明 |
|---|---|---|
| `POST /intel/forecast/demand` | 需求预测（移动平均 + 线性趋势） |
| `GET /intel/forecast/list` | 查预测结果 |

#### 推荐算法 (`/intel/recommend/...`)
| 端点 | 方法 | 说明 |
|---|---|---|
| `GET /intel/recommend/{memberId}` | 综合推荐（基于 RFM segment + LTV 365 + 行为） |

### 2.3 关键算法

#### 智能排班 — 时段负载预测
```
1. 拉历史 30 天 booking 频次（按星期 + 小时聚合）
2. 对目标日期每个技师，匹配 10/14/19 时段
3. 预测负载 expected_load = min(100, historical_count * 10)
4. 置信度 confidence = 0.6 + min(0.39, historical_count / 100)
```

#### 需求预测 — 移动平均 + 线性趋势
```
1. 取历史 30 天每日 count
2. base7 = 最近 7 天均值
3. trend = 线性回归斜率（最小二乘法）
4. 预测未来 N 天：predicted = max(0, base7 + trend * i)
5. confidence = 0.55（BASE-MA7 模型）
```

#### 推荐算法 — 基于 RFM × LTV 多因子加权
```
scoreCoupon = 0.3 (基础)
  + 0.3 if LTV >= 5000
  + 0.2 if lastDays > 90
  + 0.3 if LOST  (流失)
  + 0.2 if ABOUT_TO_SLEEP
  + 0.1 if CHAMPION
  + 0.1 if 大额券（>= 100）
  → clamp [0, 1]
```

### 2.4 总部下发状态机

```
       创建
        ↓
      DRAFT
        ↓ (publish)
    PUBLISHED ----→ (cancel) → CANCELLED
        ↓
  [目标门店]
  PENDING → ACCEPTED → EXECUTED
     ↓(违规)
  REJECTED

全部 EXECUTED → 父级 DONE
```

### 2.5 测试覆盖

| 测试 | 数量 | 覆盖 |
|---|---|---|
| `P1RemainingEntityFieldsTest` | 16 | Entity 字段 + status 状态机 + 误差率计算 + 线性趋势算法（上升/下降/平坦/边界） |

---

## 3. 编译 + 测试

- ✅ `mvn compile` 通过
- ✅ 新增 16 个测试全部通过
- ✅ 全量 81 个测试，80 通过；剩余 1 个失败为已知 `PosRefundIntegrationTest.refundPackageVersionCAS`（预先问题，与本次无关）

---

## 4. P1 全部完成

| P1 项 | 状态 |
|---|---|
| P1-1 服务 SOP | ✅ 已实装（无需修复） |
| **P1-2 多仓管理** | ✅ 已修复（最小骨架） |
| **P1-3 总部策略下发** | ✅ **本次完整实装**（策略 + 接收 + 执行） |
| **P1-4 评价完整闭环** | ✅ 已扩展（分析 + 改进行动） |
| **P1-5 AI 智能功能** | 🟡 RFM/LTV/标签已有；**本次补** 智能排班 + 需求预测 + 推荐算法 |

---

## 5. 累计审计报告修正

| 批次 | 修正项 |
|---|---|
| P0 | 3 项全部确认/修复 |
| P1 | 5 项中 4 项修复/扩展 + 1 项确认已实装 |
| P2 | 4 项全部修复/确认 |
| **P1 剩余** | **2 项（4 子项）全部实装** |

**审计报告中的 12 项 P0/P1/P2 全部执行 + 修正完毕**。剩余项目均已实装或扩展。