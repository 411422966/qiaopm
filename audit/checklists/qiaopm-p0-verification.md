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

### 核验点
- [ ] `TenantContextHolder.get()` 在 JwtInterceptor 后正确写入并 finally clear
- [ ] `DataScopeInterceptor` 对白名单外所有路径生效
- [ ] 数据库层 `tenant_id` 索引存在
- [ ] Service 层查询显式带 `tenant_id` 条件
- [ ] 跨租户 API 返回 403 TENANT_DENIED
- [ ] 跨门店 API 返回 403 STORE_DENIED

### 待核验清单（50 个不含 tenant 的 Controller）
高风险抽取：
- [ ] `DomesticPayController`
- [ ] `DcepConfigController` / `DcepSmartContractController`
- [ ] `CrmFollowUpPlanController` / `CrmFollowUpTaskController`
- [ ] `MemberTagController` / `MemberInviteController` / `MemberLifecycleController`
- [ ] `ResourceScheduleController` / `ResourceConflictLogController`
- [ ] `PriceSensitivityController`
- [ ] `SpaFallbackController`

### 结论
- [ ] 通过
- [ ] 不通过
- [ ] 待补

---

## 2. P0-02 权限（RBAC）

### 核验点
- [ ] `RbacContextLoader` 正确加载用户 / 租户 / 权限码 / 数据范围
- [ ] `@RequirePermission` 覆盖率（多少 Controller 方法实际加了注解）
- [ ] `SUPER_ADMIN` 通配 `*` 仅限内部账号
- [ ] 离职账号被禁用后 JWT 立即失效
- [ ] 权限变更后已签发 token 是否重新加载
- [ ] 越权调用返回 403 PERMISSION_DENIED

### 结论
- [ ] 通过
- [ ] 不通过
- [ ] 待补

---

## 3. P0-03 收银 / 支付安全

### 核验对象（12 个 Controller）
`PosController` / `PosRefundController` / `RefundAuditController` /
`WalletController` / `ReconciliationController` / `DomesticPayController` /
`OverseasPaymentController` / `OverseasPaymentGatewayController` /
`PaymentRouteRuleController` / `PaymentWebhookController` /
`DcepHardwareWalletController` / `DcepSubWalletController`

### 核验点
- [ ] 金额、优惠分摊使用 BigDecimal
- [ ] 支付回调幂等（同一 transaction_id 只处理一次）
- [ ] 退款金额 ≤ 原订单金额
- [ ] 退款与提成冲减在同一事务
- [ ] 对账差异可追溯
- [ ] 订单状态机完整

### 结论
- [ ] 通过
- [ ] 不通过
- [ ] 待补

---

## 4. P0-04 备份恢复

### 核验点
- [ ] 数据库每日自动备份
- [ ] 备份文件异机存储
- [ ] 恢复演练至少一次并记录
- [ ] RPO ≤ 24h、RTO ≤ 4h 达标
- [ ] Git 仓库远程同步（已做，I109）
- [ ] 对象存储有备份策略

### 结论
- [ ] 通过
- [ ] 不通过
- [ ] 待补

---

## 状态

待执行
