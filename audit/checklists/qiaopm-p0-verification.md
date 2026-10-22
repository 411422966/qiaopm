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

### 核验结果（2026-10-21 最终）
| 核验点 | 结果 | 证据 |
|---|---|---|
| `@RequirePermission` 总数 | ✅ | 139 → 865（+726） |
| Controller 覆盖 | ✅ | 14/137 → 109/138 |
| 方法覆盖率 | ✅ | 7.4% → 86.3%（+78.9 pp） |
| 剩余 29 个 Controller | ✅ | 3 公开 / 3 工具 / 8 死代码 / 15 已覆盖 |
| `PosController`（收银主链路） | ✅ | 0/15 → 15/15 |

### 结论
- [x] **通过**（I115 全部 15 批完成，2026-10-21）
- [ ] 不通过
- [ ] 待补

### 遗留（不阻塞 P0-02 关闭）
- 角色权限分配（`sys_role_permission`）：生产前必须给 BOSS/MANAGER/CLERK/OPERATOR 分配，否则普通员工功能 403
- 镜像重建：运行中的容器仍是旧代码，需 `mvn package` + `docker compose build api` 才能让 865 个注解生效

### 证据
- I115 全量记录：`audit/issues/I115-permission-coverage-gap.md`（15 个有效批次）
- 权限框架：`security/PermissionAspect.java` + `annotation/RequirePermission.java`

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
| 备份脚本 | ✅ | `scripts/backup-db-docker.ps1`（docker exec mysqldump，cmd /c 直重定向） |
| 恢复脚本 | ✅ | `scripts/restore-db.sh`（RTO 1h 目标，`--dry-run`，反误操作） |
| **恢复能力（实测）** | ✅ | 演练：RTO **41.2 秒**（目标 60 分钟），282/282 表，数据 100% CHECKSUM 一致 |
| **备份自动化** | ✅ | Windows 任务计划 `QiaoPM-DB-Backup-Daily` 每日 02:00，首次触发 LastTaskResult=0 |
| 演练证据 | ✅ | `audit/evidence/i116-backup-drill-20261022/` |
| 自动备份产物 | ✅ | `D:\QiaoPM\backup\meiyetec_YYYYMMDD_HHMMSS.sql`（9.85 MB / 281 张表），保留 7 天 |

### 结论
- [x] **通过**（恢复能力 + 备份自动化均验证）
- [ ] 不通过
- [ ] 待补

### 遗留（不阻塞 P0-04 关闭）
- S3 上传：未做（容器内无 aws cli），本地落盘满足 RPO ≤ 24h
- 失败告警：未做 → I124（P2）

### 证据
- 恢复演练：`audit/issues/I116-backup-drill-evidence-gap.md`
- 备份自动化：`audit/issues/I123-backup-automation-not-deployed.md`
- 演练证据：`audit/evidence/i116-backup-drill-20261022/`

---

## 状态

待执行
