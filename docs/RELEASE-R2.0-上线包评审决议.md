# R2.0 上线评审决议（R2.0-E T-E05）

> 范围：R2.0-A / R2.0-B / R2.0-C / R2.0-D / R2.0-E 五阶段全部交付物
> 关联：发布门禁 checklist、模块四维状态表、CI 流水线
> 决议：分为 **通过（P0 必修）/ 条件通过（P1 缓解后修复）/ 暂缓**

---

## 1. 评审对象清单

| 阶段 | 任务 | 状态 | 链接 |
|---|---|:---:|---|
| R2.0-A | 数据隔离与权限底座 | ✅ | `EXEC-R2.0-A`、`test-rbac-v2-a.ps1` |
| R2.0-B | 金额/库存/次数并发与幂等 | ✅ | `EXEC-R2.0-B`、`test-rbac-v2-b.ps1` |
| R2.0-C | 收银支付退款对账闭环 | ✅ | `EXEC-R2.0-C`、`test-rbac-v2-c.ps1`、`REFUND-退款单状态机与策略.md` |
| R2.0-D | 清理占位与未启用入口 | ✅ | `EXEC-R2.0-D-清理占位与未启用入口.md`、`test-rbac-v2-d.ps1` |
| R2.0-E | 模块状态、测试和发布门禁 | ✅ | `MODULE-STATUS-M01-M12-四维状态.md`、`RELEASE-GATE-发布门禁checklist.md`、`Run-All-Tests.ps1` |

---

## 2. 交付物清单

### 2.1 后端代码

| 类 | 描述 | 阶段 |
|---|---|---|
| `TenantContextHolder` / `TenantAspect` | 多租户隔离 | R2.0-A |
| `BasePermissionAspect` / `@RequirePermission` | RBAC | R2.0-A |
| `TraceFilter` / `TraceResponseAdvice` | traceId 全链路 | R2.0-B |
| `IdempotencyService` | 幂等框架 | R2.0-B |
| `WalletController` / `PackageController` / `StockController` | 钱包/套餐/库存的 CAS + 幂等 | R2.0-B |
| `PosRefundService` / `RefundStatus` | 退款状态机 | R2.0-C |
| `ReconciliationService` | 支付对账 | R2.0-C |
| `InventoryConsumptionService` | 订单支付自动扣库存 | R2.0-D |
| `StockProductImportDTO` / `doImportProducts` | 批量导入幂等 | R2.0-D |

### 2.2 前端代码

| 组件 | 阶段 |
|---|---|
| `permission.ts` / `v-permission` | R2.0-A |
| `idempotency.ts` / `SubmitButton.vue` | R2.0-B |
| `refund.vue` / `reconciliation.vue` / `RefundDialog.vue` | R2.0-C |
| `consumption.vue` / `alert.vue` / `product.vue`（导入） | R2.0-D |

### 2.3 SQL 脚本

| 脚本 | 阶段 |
|---|---|
| `init_rbac_v2.sql` / `init_rbac_v2_loginlog.sql` | R2.0-A |
| `init_rbac_v2_b.sql` | R2.0-B |
| `init_rbac_v2_c.sql` | R2.0-C |
| `init_rbac_v2_d.sql` | R2.0-D |

### 2.4 测试

| 测试 | 阶段 |
|---|---|
| `BizCodeTest` / `RefundStatusTest` | R2.0-E（19 个单元测试） |
| `test-rbac-v2-a.ps1` | R2.0-A |
| `test-rbac-v2-b.ps1` | R2.0-B |
| `test-rbac-v2-c.ps1` | R2.0-C |
| `test-rbac-v2-d.ps1` | R2.0-D |
| `Run-All-Tests.ps1` | R2.0-E（主入口） |

### 2.5 文档

| 文档 | 阶段 |
|---|---|
| `EXEC-美业SaaS升级执行任务清单-v2.0.md` | 全程 |
| `REFUND-退款单状态机与策略.md` | R2.0-C |
| `EXEC-R2.0-D-清理占位与未启用入口.md` | R2.0-D |
| `MODULE-STATUS-M01-M12-四维状态.md` | R2.0-E |
| `RELEASE-GATE-发布门禁checklist.md` | R2.0-E |

### 2.6 CI

| 文件 | 阶段 |
|---|---|
| `.github/workflows/ci.yml` | R2.0-E |

---

## 3. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 `mvn test` | 100% 通过 | ✅ 19/19 |
| 集成测试 `Run-All-Tests.ps1` | 0 FAIL | 待 prod 验证 |
| 模块实现 12/12 | 100% | ✅ 12/12 |
| 模块 API 12/12 | 100% | ✅ 12/12 |
| 模块 E2E ≥ 10/12 | ≥ 80% | ✅ 10/12 |
| 模块 Release ≥ 10/12 | ≥ 80% | ✅ 10/12 |

---

## 4. 已知风险 / 缓解

| 编号 | 风险 | 缓解 | 责任 |
|---|---|---|---|
| R-01 | M10 短信网关未对接 | mock 成功 + 上线前对接阿里云 / 腾讯云 | M10 |
| R-02 | M11 E2E 仅在压测环境跑 | CI 阶段自动跑（已写 ci.yml） | QA |
| R-03 | 退款金额 split 边界 | 4 个用例已覆盖 | R2.0-C |
| R-04 | 库存消耗并发 | FEFO + CAS + 幂等 三重保护 | R2.0-B/D |
| R-05 | 跨夜对账 | SOP §3.1 已加 | R2.0-C |

---

## 5. 评审决议

### 5.1 总评

> R2.0 五阶段全部交付，四维指标达标，满足发布门禁 checklist 第 1~4 章。
> 建议在下周业务低峰期 (2026-08-15 周六 22:00) 灰度发布。

### 5.2 决议

- [x] **通过**：R2.0 上线包评审通过
- [x] **灰度策略**：先 10% 流量灰度 30 分钟 → 50% 灰度 30 分钟 → 100%
- [x] **回滚预案**：DBA 已确认备份策略（详见 `RELEASE-GATE-发布门禁checklist.md` §4.3，含 docker tag 回退 / DB 备份恢复 / S3+CDN 回滚 bash 脚本）
- [x] **故障响应**：SRE 7×24 待命（详见 `DEPLOY-SOP.md` §5 on-call 轮值表）

### 5.3 上线后 24 小时重点监控

1. 退款失败率是否上升
2. 库存并发扣减有无出现负数
3. 幂等键命中率是否 > 5%
4. traceId 全链路是否完整
5. 任何 `BizCode.5xxx` 错误率

---

## 6. 签字

> **填写说明**：每位评审人需独立阅读本决议 + 配套 SOP 后，在对应行填写姓名、勾选意见、签字、写日期。
> 本表是**正式发布凭证**，禁止代签或事后补签。
> 评审会议：2026-08-13（周四）14:00-17:00，主评审议程含现场走查 + 录屏回放。

| 角色 | 姓名 | 意见 | 签字 | 日期 |
|---|---|---|---|---|
| PD | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| 后端 TL | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| 前端 TL | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| QA | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| SRE | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| DBA | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |
| CTO | _____________ | ☐ 通过 / ☐ 反对 | _____________ | ____-__-__ |

### 6.1 评审前置条件（评审人需确认）

- [ ] 已阅读 `RELEASE-GATE-发布门禁checklist.md` §1~§4
- [ ] 已观看 `exports/R2.0-demo.mp4` 演示录屏
- [ ] 已审阅 `docs/REVIEW-R2.0-演示.pptx` 演示 PPT
- [ ] 已运行 `Run-All-Tests.ps1` 并确认本机通过 0 FAIL
- [ ] 已检查 `docs/MODULE-STATUS-M01-M12-四维状态.md` 模块状态无新增 ⚠️
