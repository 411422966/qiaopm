# Post-Audit Phase 2.2 汇总报告 — CRITICAL P0 代码修复 6 项

| 维度 | 值 |
|---|---|
| **批次** | Post-Audit Phase 2.2（A1-A6 一轮内完成）|
| **修复时间** | 2026-09-17 22:00 - 22:50 (UTC+8) |
| **修复总数** | 6 / 6 (含 WebConfig 补全 1 项) |
| **独立 commit** | 7（5 code + 1 code补全 + 1 docs）|
| **后端验证** | ✅ 全部 PASS（mvn compile + curl 6 项）|
| **状态** | ✅ 全部 OPEN → FIXED |

---

## 1. Commit 时间线

| 顺序 | Commit | 内容 | Issue |
|---|---|---|---|
| 1 | `eb5ff6e` | fix(auth) I037 webhook path exclude | I037 |
| 2 | `2dc684f` | docs I037 + A1 report | I037 |
| 3 | `e7802d3` | fix(mkt) I070 grantBatch table fix | I070 |
| 4 | `b691cea` | docs I070 + A2 report | I070 |
| 5 | **`776e8be`** | fix(crm) I008 dashboard catch → log.error | I008 |
| 6 | **`0b5607e`** | fix(crm) I011 update 移除 selectById | I011 |
| 7 | **`f0c73d8`** | fix(intl) I047 IG webhook catch → Result.error | I047 |
| 8 | **`3d94c56`** | fix(mkt) I071 markRead rows 校验 + tenantId | I071 |
| 9 | **`939fd73`** | docs I008/I011/I047/I071 + A3-A6 reports | (A3-A6 docs) |
| 10 | **`fad68a1`** | fix(auth) I037 补 `/intl-booking/webhook/**` exclude | I037 (补) |

---

## 2. 修复矩阵（CRITICAL P0）

| Issue | 文件 | +行 | -行 | commit | 方案 |
|---|---|---|---|---|---|
| **I037** | `WebConfig.java` | +5 | -1 | `eb5ff6e` + `fad68a1` | excludePathPatterns 补全 |
| **I070** | `MktCouponController.java` | +2 | -1 | `e7802d3` | member → crm_member |
| **I008** | `CrmIntegrationService.java` | +2 | -1 | `776e8be` | catch ignore → log.error |
| **I011** | `CrmIntegrationService.java` | +3 | -1 | `0b5607e` | 移除 selectById |
| **I047** | `IntlBookingService.java` | +5 | -2 | `f0c73d8` | catch warn → log.error + Result.error |
| **I071** | `MarketingAutomationService.java` | +11 | -2 | `3d94c56` | rows 校验 + tenantId 过滤 |

---

## 3. 运行时验证（curl 6 项）

| Issue | endpoint | 修复前 | 修复后 | 期望 |
|---|---|---|---|---|
| I037 | `POST /webhook/youzan?kdt_id=TEST_KDT` | 401（JWT 拦截，永远走不到 controller）| **401（"缺少 X-Youzan-Sign 头" — 你赞签名层）** | ✅ 通过 |
| I070 | `POST /mkt/coupon/batch/1/grant` | 500（Table 'meiyetec.member' doesn't exist）| **409 业务冲突"券码已发放"** | ✅ 通过 |
| I008 | `GET /crm-integration/dashboard` | 200 + data=null（静默吞异常）| **200 + data={totalIntegrations:4,totalSyncLogs:0} + log.error on exception** | ✅ 通过 |
| I011 | `POST /crm-integration/2` body `{shopName,syncDirection}` | 500（"数据库异常"，DB 已变 = 数据漂移）| **200 + "已更新"** | ✅ 通过 |
| I047 | `POST /intl-booking/webhook/instagram` (proper messaging payload) | 200 NOOP（静默丢数据）| **500 + log.error（Meta 重试可见）** | ✅ 通过 |
| I071 | `POST /mkt/conversion/mark-read` body `{id:999999}` | 200 success（Fake Success）| **404 NOT_FOUND"触达记录不存在或非待读状态"** | ✅ 通过 |

### 3.1 关键修复对照（Issue 关联）

| Issue | Phase 5 实证状态 | Post-Audit 修复后 |
|---|---|---|
| I037 | 永远 401（webhook 完全不可达）| ✅ 200/你赞层 401（JWT 不再拦截）|
| I070 | 永远 500（TableNotFound）| ✅ 409（业务正常）|
| I008 | 静默吞异常 + F010 tick 7 次失败 | ✅ log.error 上报（含 stacktrace + tenantId）|
| I011 | API 500 + DB 已变（数据漂移）| ✅ API 200 + DB 已变（无漂移）|
| I047 | 静默 NOOP + Meta 不重试 | ✅ 500 + Meta 可见（可重试）|
| I071 | Fake Success（rows=0 也返 200）| ✅ 404 NOT_FOUND（修复）|

---

## 4. mvn compile 验证

```powershell
$ mvn compile
[INFO] BUILD SUCCESS
exit_code: 0   (5 次连续)
```

---

## 5. 关联文档

| 文档 | 路径 |
|---|---|
| A1 报告 | `audit/post-audit/phase-2.2-A1-report.md` |
| A2 报告 | `audit/post-audit/phase-2.2-A2-report.md` |
| **A3 报告** | `audit/post-audit/phase-2.2-A3-report.md` |
| **A4 报告** | `audit/post-audit/phase-2.2-A4-report.md` |
| **A5 报告** | `audit/post-audit/phase-2.2-A5-report.md` |
| **A6 报告** | `audit/post-audit/phase-2.2-A6-report.md` |
| **本汇总** | `audit/post-audit/phase-2.2-summary.md` |

| Issue 文件 | 路径 |
|---|---|
| I008 | `audit/issues/I008-dashboard-catch-swallow.md` |
| I011 | `audit/issues/I011-crm-fake-failure.md` |
| **I047（新文件）** | `audit/issues/I047-intl-ig-webhook-catch-swallow.md` |
| I071 | `audit/issues/I071-mkt-conversion-markread-fake-success.md` |

---

## 6. 已知遗留 / 待办（Post-Audit 2.3+）

| 编号 | 内容 | 影响 |
|---|---|---|
| **I038** | overseas_booking 表缺失 | A5 验证触发 500 — 修复 I047 后异常正确上报，但**根因**需 2.3 阶段迁移 |
| **I002** | crm_integration.sync_token 列缺失 | 仍触发 F010 tick 失败（I008 catch 仅上报 log.error，**根因**未修）|
| **I039** | MKT 4 张表缺失（market_activity + 3） | A6 测试用了 fallback（id=999999 不存在），未触达真路径 |
| **I052** | mvn test 编译失败 | 启动后端需 `-Dmaven.test.skip=true`（Phase 7 修）|
| **A1 WebConfig 补全** | `/intl-booking/webhook/**` 已加（`fad68a1`）| 同步检查其他遗漏路径（如 `/crm/webhook/**` `/marketing/webhook/**`）|

---

## 7. 下一步建议（2.3 批次：DB 迁移）

按用户决策 D2 修复顺序：

| 批次 | 内容 | 优先级 |
|---|---|---|
| **2.3-A** | 9 张表迁移（解锁 30+ Feature 的 MISSING）| **P0**（含 I038 overseas_booking）|
| 2.3-B | 4 张 MKT 表 DDL（I063）+ expireSweep audit（I060）| P0 |
| 2.3-C | ChannelDispatcher NPE 修复（I061+I075+I076）| P1 |
| 2.3-D | journeyPage COUNT args（I073）+ conversionRoi 空数据兜底（I072）| P1 |

---

**Phase 2.2 全部完成。CRITICAL P0 代码修复 6 项 + WebConfig 补全 1 项，共 7 个独立 commit 全部进入主干。**
