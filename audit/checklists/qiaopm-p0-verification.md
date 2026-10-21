# QiaoPM 美业 SaaS — P0 模块核验清单

> 建立：2026-10-16
> 关联：I109~I114 基础设施治理已完成，本清单转入业务层 P0 核验
> 核验原则：每项必须留证据，结论三态：通过 / 不通过 / 待补

---

## 0. 权限与租户体系现状（只读扫描结论）

| 维度 | 现状 |
|---|---|
| Controller 总数 | 137 个 `*Controller.java`，224 处 `@RestController` |
| 认证 | `JwtInterceptor`（HandlerInterceptor） |
| 权限 | `PermissionAspect`（AOP，基于 `@RequirePermission`） |
| 数据范围 | `DataScopeInterceptor`（跨租户 / 跨门店前置拦截） |
| 权限注解 | 自研 `@RequirePermission`，非 Spring Security |
| TenantContextHolder 覆盖 | 87/137 Controller 含 tenant 关键字 |
| 疑似缺租户隔离 | 50/137 Controller 不含 tenant 关键字 |

---

## 1. P0-01 多租户 / 门店隔离

### 核验结果（2026-10-16）
- ✅ `DataScopeInterceptor` 设计良好（Header/Query/JSON body 三来源提取）
- ✅ `WebConfig` 中注册顺序正确（JwtInterceptor 先于 DataScopeInterceptor）
- ✅ 数据库层 `idx_tenant` 索引存在（V1__init_baseline.sql 多处）
- 🔴 **`DataScopeInterceptor` 仅覆盖 15 个路径前缀**，未覆盖：
  `/domestic/**` `/dcep/**` `/overseas-payment/**` `/payment/**` `/bnpl/**` `/huabei/**`
  `/ecommerce/**` `/plugin/**` `/marketplace/**` `/kiosk/**` `/invite/**`
  `/customer-care/**` `/price-list/**` `/price-sensitivity/**` `/review-analysis/**`
  `/dianping/**` `/lbs/**` 等
- ⚠️ 之前「50 个不含 tenant 的 Controller」扫描结果不可靠（PosController 实际含 TenantContextHolder 20 次）

### 结论
- [x] **通过**（I117 修复后重验，2026-10-17）
- [ ] 不通过
- [ ] 待补
### 证据
- `WebConfig.addInterceptors` 输出（2026-10-16）
- `DataScopeInterceptor` 白名单路径清单

---
## 2. P0-02 权限（RBAC）

### 核验结果（2026-10-16）
- Controller 覆盖：14 / 137 = **10.2%**
- 方法覆盖：139 / 1885 = **7.4%**
- `PosController`（收银主链路）：0 / 15 = **0%**
- 权限框架已实现，覆盖率严重不足

### 结论
- [ ] 通过
- [x] **不通过** → 登记 I115
- [ ] 待补

### 证据
- 扫描命令输出（2026-10-16）
- 14 个已覆盖 Controller 清单（见 I115）

---
## 3. P0-03 收银 / 支付安全

### 核验结果（2026-10-21）
| 核验点 | 结果 | 证据 |
|---|---|---|
| 金额 BigDecimal | ✅ | PosOrder/PosPayment/PosRefund/Wallet 等 7 实体全 BigDecimal；Controller 无 double/float |
| 退款 ≤ 可退余额 | ✅ | `PosRefundService`：`amount.compareTo(remain) > 0` 拦截 |
| 退款审批分级 | ✅ | `RefundAuditWorkflowService` 按金额阈值分级 |
| 退款幂等 | ✅ | `PosRefundController` 用 `idem.execute("pos.refund", ...)` |
| 退款事务 | ✅ | `PosRefundService` 11 处 `@Transactional` |
| 对账事务 | ✅ | `ReconciliationService` 2 处 `@Transactional` |
| 支付回调幂等 | ✅ | `PaymentWebhookLogServiceImpl`：验签 → 解析 eventId → 按 `gateway_event_id` 查重 → 处理 |
| 回调幂等 DB 兜底 | ✅ | `biz_payment_webhook_log.uk_event_id` 唯一索引（NON_UNIQUE=0） |
| `pos_payment.payment_no` 唯一索引 | ✅ | NON_UNIQUE=0 |
| 订单状态机 | ⚠️ | 注释明确 `1进行中 2已支付 3已退款 4已撤销`，Integer 硬编码（可维护性 P2，不影响正确性） |
| `payment_transaction_log.out_trade_no` | ⚠️ | 非唯一索引（NON_UNIQUE=1），该表为交易日志性质，依赖代码层幂等（P2 观察项） |

### 结论
- [x] **通过**（2026-10-21，I122 撤回为误报后重验）
- [ ] 不通过
- [ ] 待补

### 证据
- 实体/Service 扫描（2026-10-21）
- DB 表结构 / 索引查询（biz_payment_webhook_log / pos_payment / payment_transaction_log）
- Mapper SQL 核对（PaymentWebhookLogMapper.findByEventId）

---
## 4. P0-04 备份恢复

### 核验结果（2026-10-22）
| 核验点 | 结果 | 证据 |
|---|---|---|
| 备份脚本 | ✅ | `scripts/backup-db.sh`（RPO 1h，本地 7d / S3 30d / 归档 1y） |
| 恢复脚本 | ✅ | `scripts/restore-db.sh`（RTO 1h 目标，`--dry-run`，反误操作） |
| **恢复能力（实测）** | ✅ | 演练：RTO **41.2 秒**（目标 60 分钟），282/282 表，数据 100% CHECKSUM 一致 |
| 演练证据 | ✅ | `audit/evidence/i116-backup-drill-20261022/` |
| **备份自动化** | ❌ | `backup-db.sh` 从未运行，aws cli 未装，备份目录不存在，systemd timer 未部署 → **I123** |

### 结论
- [ ] 通过
- [x] **部分通过**（恢复能力验证 ✅ / 备份自动化未部署 ❌ → I123）
- [ ] 待补

### 证据
- 演练记录：`audit/issues/I116-backup-drill-evidence-gap.md`
- 演练证据：`audit/evidence/i116-backup-drill-20261022/full_backup.sql`（10.3 MB）
- 备份自动化缺口：`audit/issues/I123-backup-automation-not-deployed.md`

---
## 状态

待执行
