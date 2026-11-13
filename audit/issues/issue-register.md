# Issue Register

> 重建于 2026-10-16（原 register 受 I112 编码损坏，见 I112），最近更新 2026-11-13（补登 I156/I157，登记 I158）
> 数据来源：audit/issues/I*.md 单文件自动提取
> 状态列为启发式匹配，如与文件不一致，以文件为准

| Issue ID | 标题 | 状态 | 文件 |
|---|---|---|---|
| I001 | I001 — EXT-24 .mdc (SoT) vs 代码实现冲突 | Unknown | [I001-ext24-sot-conflict.md](I001-ext24-sot-conflict.md) |
| I004 | I004 — 重构 Backlog（低优先级） | Unknown | [I004-refactor-backlog.md](I004-refactor-backlog.md) |
| I006 | I006 — CRM Dashboard Shadow Feature | Unknown | [I006-crm-dashboard-shadow.md](I006-crm-dashboard-shadow.md) |
| I007 | I007 — CRM Update UI Shadow Feature | Unknown | [I007-crm-update-shadow.md](I007-crm-update-shadow.md) |
| I008 | I008 — CRM Dashboard Catch 吞异常（Fake Success 风险） | Fixed | [I008-dashboard-catch-swallow.md](I008-dashboard-catch-swallow.md) |
| I009 | I009 — @Transactional rollbackFor 缺失 | Unknown | [I009-transactional-rollbackfor.md](I009-transactional-rollbackfor.md) |
| I010 | I010 — Flyway V1__init_baseline.sql 编码不匹配（中文注释乱码） | Fixed | [I010-flyway-v1-encoding.md](I010-flyway-v1-encoding.md) |
| I011 | I011 — CRM Update API Fake Failure（API 报错但 DB 已写） | Fixed | [I011-crm-fake-failure.md](I011-crm-fake-failure.md) |
| I012 | I012 — Mock 适配器激活条件冲突（默认 NPE 风险） | Fixed | [I012-mock-adapter-condition-conflict.md](I012-mock-adapter-condition-conflict.md) |
| I013 | I013 — syncSchedule 字段 dead code（定义但无任何代码读取） | Fixed | [I013-sync-schedule-dead-code.md](I013-sync-schedule-dead-code.md) |
| I016 | I016 — 同步日志 tab 缺失 platform 列（API 过滤维度未渲染） | Open | [I016-sync-log-missing-platform-column.md](I016-sync-log-missing-platform-column.md) |
| I037 | I037 — Webhook 路径未在 JWT excludePathPatterns（生产环境永远 401） | Fixed | [I037-webhook-path-not-excluded.md](I037-webhook-path-not-excluded.md) |
| I047 | I047 — IG Webhook Catch 吞异常（Fake Success — I008 同模式） | Fixed | [I047-intl-ig-webhook-catch-swallow.md](I047-intl-ig-webhook-catch-swallow.md) |
| I052 | I052 — mvn test 编译失败（CI 阻塞） | Unknown | [I052-mvn-test-compilation-failure.md](I052-mvn-test-compilation-failure.md) |
| I070 | I070 — `MktCouponController.grantBatch` 用错表名 `member` 而非 `crm_member`（发券链路必失败 500） | Fixed | [I070-mkt-coupon-grant-member-table.md](I070-mkt-coupon-grant-member-table.md) |
| I071 | I071 — `MktConversionController.markRead` 假成功（Fake Success 沿用 I008 模式） | Fixed | [I071-mkt-conversion-markread-fake-success.md](I071-mkt-conversion-markread-fake-success.md) |
| I072 | I072 — `MarketingAutomationService.conversionRoi` 空数据 / taskId 过滤时 500 数据库异常 | Unknown | [I072-mkt-roi-500.md](I072-mkt-roi-500.md) |
| I073 | I073 — `MarketingAutomationService.journeyPage` COUNT 查询 args 共享导致 SQLException（POST+GET 双形式同中招） | Unknown | [I073-mkt-journey-page-count-bug.md](I073-mkt-journey-page-count-bug.md) |
| I074 | I074 — `rateLimitPerDay` API 响应 null vs DB 列默认 5000 不一致（I062 setter 缺失延伸 — 契约漂移） | Unknown | [I074-mkt-rate-limit-per-day-contract-drift.md](I074-mkt-rate-limit-per-day-contract-drift.md) |
| I087 | I087 — MockEcommerceAdapter 同类条件冲突 Bug | Fixed | [I087-mock-ecommerce-adapter-bug.md](I087-mock-ecommerce-adapter-bug.md) |
| I088 | I088 · 全后端 @Transactional 未指定 rollbackFor（199 处遗留） | Fixed | [I088-transactional-rollbackfor-global.md](I088-transactional-rollbackfor-global.md) |
| I093 | I093 — ecommerce_* dev 测试残留数据清理 | Closed | [I093-cleanup-ecommerce-test-data.md](I093-cleanup-ecommerce-test-data.md) |
| I095 | I095 — @Transactional catch 块吞异常导致事务不回滚 | Fixed | [I095-transactional-catch-swallow.md](I095-transactional-catch-swallow.md) |
| I096 | I096 — 4 个 Java 源文件 UTF-8 字节序列损坏（mvn compile 失败） | Closed | [I096-utf-source-file-corrupt.md](I096-utf-source-file-corrupt.md) |
| I097 | I097 — 11aa1b1 合并 commit 设计缺陷（编码修复与业务替换不可分离） | Fixed | [I097-commit-design-fix.md](I097-commit-design-fix.md) |
| I098 | I098 — prod 配置文件缺友好 fail-fast 消息 | Open | [I098-prod-config-friendly-failfast.md](I098-prod-config-friendly-failfast.md) |
| I099 | I099 — 备份恢复缺口（修复前） | Fixed | [I099-backup-gap.md](I099-backup-gap.md) |
| I100 | I100 — 应用回滚缺口（修复前） | Fixed | [I100-rollback-gap.md](I100-rollback-gap.md) |
| I102 | I102 — Flyway I010 根因阻塞 prod 4 容器 Up（Gate 3 批次 1.4a 临时绕路） | Fixed | [I102-flyway-i010-blocks-prod-startup.md](I102-flyway-i010-blocks-prod-startup.md) |
| I103 | I103 — PluginRuntimeTest 编译失败 | Open | [I103-plugin-runtime-test-compile-fail.md](I103-plugin-runtime-test-compile-fail.md) |
| I104 | I104 — 监控采集侧空白（P0-B 未覆盖） | Fixed | [I104-monitoring-gap.md](I104-monitoring-gap.md) |
| I105 | I105 — docker-compose.yml 幽灵 SQLite 覆盖事件 | Fixed | [I105-docker-compose-sqlite-overwrite.md](I105-docker-compose-sqlite-overwrite.md) |
| I106 | I106 — meiyetec-net 硬编码 name | Fixed | [I106-compose-network-hardcoded-name.md](I106-compose-network-hardcoded-name.md) |
| I107 | I107 — AlertManager wechat-webhook URL 硬编码 | Fixed | [I107-alertmanager-webhook-url-hardcoded.md](I107-alertmanager-webhook-url-hardcoded.md) |
| I108 | I108 — 07-scan-transaction.ps1 误报 javadoc 注释中的 @Transactional | Fixed | [I108-transaction-scan-false-positive.md](I108-transaction-scan-false-positive.md) |
| I109 | I109 — Git objects/30 NTFS 损坏导致仓库重建 | Mitigated | [I109-git-objects-corruption-ntfs.md](I109-git-objects-corruption-ntfs.md) |
| I110 | I110 — 每周 Git 仓库体检 | Open | [I110-weekly-git-fsck.md](I110-weekly-git-fsck.md) |
| I111 | I111 — 每月 chkdsk D: /scan | Open | [I111-monthly-chkdsk-scan.md](I111-monthly-chkdsk-scan.md) |
| I112 | I112 — issue-register.md 中文永久损坏 | Open | [I112-issue-register-mojibake.md](I112-issue-register-mojibake.md) |
| I113 | I113 — compile_impls.ps1 使用 -Encoding ascii 写文件 | Open | [I113-ascii-encoding-risk.md](I113-ascii-encoding-risk.md) |
| I114 | I114 — Java class 覆盖 .git/objects 的根因排查 | Fixed | [I114-class-overwrites-git-objects.md](I114-class-overwrites-git-objects.md) |
| I115 | I115 — 权限注解覆盖率不足（P0） | PARTIAL | [I115-permission-coverage-gap.md](I115-permission-coverage-gap.md) |
| I116 | I116 — 备份恢复演练证据缺失（P0） | Closed | [I116-backup-drill-evidence-gap.md](I116-backup-drill-evidence-gap.md) |
| I117 | I117 — DataScopeInterceptor 覆盖路径不全（P0） | Open | [I117-datascope-coverage-gap.md](I117-datascope-coverage-gap.md) |
| I118 | I118 — 132 张表的 DDL 缺失但实体/Mapper/Controller 完整（P0） | Open | [I118-missing-ddl-132-tables.md](I118-missing-ddl-132-tables.md) |
| I119 | I119 — TechnicianLevel 晋升 UPDATE 写错表（sys_user → pos_employee） | Open | [I119-technician-level-update-wrong-table.md](I119-technician-level-update-wrong-table.md) |
| I120 | I120 — Entity 字段与 DB 字段命名漂移（MyBatis-Plus 驼峰转换不一致） | Open | [I120-entity-column-name-drift.md](I120-entity-column-name-drift.md) |
| I121 | I121 — GrayReleaseService 命名与职责不符（Controller 伪装成 Service） | Open | [I121-service-named-controller.md](I121-service-named-controller.md) |
| I122 | I122 — payment_webhook_log 表不存在，支付回调必 500（P0） | Open | [I122-payment-webhook-log-table-missing.md](I122-payment-webhook-log-table-missing.md) |
| I123 | I123 — 备份自动化从未运行（脚本存在但未部署） | Fixed | [I123-backup-automation-not-deployed.md](I123-backup-automation-not-deployed.md) |
| I124 | I124 — 权限码体系分裂（点号 vs 冒号），前端菜单与路由守卫系统性错配 | Open | [I124-authz-code-system-split.md](I124-authz-code-system-split.md) |
| I125 | I125 — Blockchain Record / Chain 菜单已上线但前端无路由（影子功能） | Open | [I125-blockchain-shadow-route.md](I125-blockchain-shadow-route.md) |
| I126 | I126 — 运行中的容器镜像落后源码 4 天 / 14 天，运行态验证结论与源码不一致 | Open | [I126-runtime-image-drift.md](I126-runtime-image-drift.md) |
| I127 | I127 — 备份文件编码损坏：PowerShell 5.1 重定向造成 UTF-16LE + GBK 双重转码 | Fixed | [I127-backup-file-encoding-corruption.md](I127-backup-file-encoding-corruption.md) |
| I128 | I128 — 库内 CJK 文本编码污染：RBAC 三表 15 行中文不可读 | Open | [I128-db-cjk-encoding-pollution.md](I128-db-cjk-encoding-pollution.md) |
| I129 | I129 — pos_order / crm_package 缺列导致收银台与套餐列表必 500 | Open | [I129-missing-columns-pos-crm.md](I129-missing-columns-pos-crm.md) |
| I130 | I130 — MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，历史迁移脚本中存在多处使用 | Open | [I130-mysql-add-column-if-not-exists-incompatible.md](I130-mysql-add-column-if-not-exists-incompatible.md) |
| I131 | I131 — PackageItem 实体缺 deleted 字段（软删除隐患），Controller 从不写 createBy | Open | [I131-packageitem-missing-deleted-and-createby.md](I131-packageitem-missing-deleted-and-createby.md) |
| I132 | I132 — 收银台前端未传入 tenderedAmount，找零功能未接入 UI | Open | [I132-pos-checkout-missing-tendered-ui.md](I132-pos-checkout-missing-tendered-ui.md) |
| I137 | I137 — 前端路由守卫码与后端权限注解系统性错配：服务项目越权写入 + 套餐按钮假打通 + BOM 端点 404（🔴 高） | Open | [I137-guard-code-fake-through.md](I137-guard-code-fake-through.md) |
| I140 | I140 — AiEnhanceService 数据血缘漂移：pos_order_item.service_id 指向 biz_service，代码却 JOIN biz_service_item | Fixed | [I140-ai-enhance-blood-line-drift.md](I140-ai-enhance-blood-line-drift.md) |
| I141 | I141 — biz_service.category_id 全表 NULL，AI 推荐会员偏好类别链路永远为空 | Open | [I141-biz-service-category-null.md](I141-biz-service-category-null.md) |
| I142 | I142 — LtvTrainingService 引用 4 张不存在的表，LTV 训练 ETL 全链路必然失败 | Open | [I142-ltv-training-missing-tables.md](I142-ltv-training-missing-tables.md) |
| I143 | I143 — AI 推荐缓存反序列化为桩代码，命中缓存返回空数组 | Fixed | [I143-ai-recommend-cache-parse-stub.md](I143-ai-recommend-cache-parse-stub.md) |
| I145 | I145 — market_coupon 缺少 issued_count 计数列，atomicGrant 无法在 UPDATE 内原子自增 | Open | [I145-market-coupon-issued-count.md](I145-market-coupon-issued-count.md) |
| I148 | I148 — `atomicGrant` 发放闸门稳定性与口径缺陷：matched-rows 隐式依赖驱动默认值（生产 URL 未审计）/ 配额计数未带 tenant_id 跨租户互相扣配额 / 闸门与写入分离并发可超发 | Open | [I148-atomic-grant-stability-and-tenant-scope.md](I148-atomic-grant-stability-and-tenant-scope.md) |
| I149 | I149 — M09 邀请奖励发放链路业务缺陷：未达门槛后 status=3 但补发只取 status=2 / 会员不存在静默 return 致漏发无痕迹 / 脱敏手机号参与唯一键致撞键 | Open | [I149-invite-reward-chain-biz-defects.md](I149-invite-reward-chain-biz-defects.md) |
| I150 | I150 — `ltv_training_feature.total_package_balance` 口径错误：存的是「剩余次数」，字段语义是「余额（金额）」 | Open | [I150-ltv-total-package-balance-should-be-amount.md](I150-ltv-total-package-balance-should-be-amount.md) |
| I151 | I151 — LTV GMV 未扣减部分退款（`pos_order.refunded_amount` 被忽略） | Open | [I151-ltv-gmv-no-partial-refund-deduction.md](I151-ltv-gmv-no-partial-refund-deduction.md) |
| I152 | I152 — `crm_order` / `crm_order_item` 引用外溢：13 个文件 21 处 SQL 指向不存在的表 | Open | [I152-crm-order-reference-outflow.md](I152-crm-order-reference-outflow.md) |
| I153 | I153 — `biz_service.category_id` 全表为 NULL：服务分类未回填，5 个分类无法关联 | Open | [I153-biz-service-category-null.md](I153-biz-service-category-null.md) |
| I154 | I154 — LTV/Churn 训练链路 6 个代码缺陷：治理看板不可见 / 下线端点缺失 / Churn 标签反向 / 重要性错位 / 均值计算错 / 运行日志不关联 | PARTIAL | [I154-ltv-training-chain-defects.md](I154-ltv-training-chain-defects.md) |
| I155 | I155 — 预测端完全不消费训练出来的模型：部署成功的模型是"空挂"，两套模型注册表并存 | Open | [I155-prediction-side-ignores-trained-model.md](I155-prediction-side-ignores-trained-model.md) |
| I156 | I156 — computeChurn 与 batchPredictChurn 双写 crm_churn_prediction 无互斥 | Open | [I156-compute-churn-batch-predict-duplicate.md](I156-compute-churn-batch-predict-duplicate.md) |
| I157 | I157 — LTV 年化系数分母下限只有 1 天，新客 LTV 虚高 | Open | [I157-ltv-annualization-new-member-inflation.md](I157-ltv-annualization-new-member-inflation.md) |
| I158 | I158 — `repurchase_prob_30d` 分档只看频率不看样本量，置信度与证据强度反向 | Open | [I158-repurchase-prob-evidence-inversion.md](I158-repurchase-prob-evidence-inversion.md) |
| TD-006 | TD-006 — 调查类探针表（_t_*）在 MySQL 库中可能遗留，需定期清理 | Open | [TD-006-probe-tables-leftover.md](TD-006-probe-tables-leftover.md) |

## 备注

- **I119**：代码+数据已修复，运行时晋升验证待补。
- **I115**：BizServiceItemService 22 个端点零 @RequirePermission，实测 operator01 可越权写入。详见 I137-P0。
- **I127**：事故件 `before-batch1-20261025-180825.sql` 已改名为 `.UNUSABLE` 保留为证据，不恢复；根因（PS 5.1 `>` 重定向）已消除，正确备份方式（容器内 dump + `docker cp`）已验证可恢复。
- **I130**（`I130-menu-toplevel-guard-code-fix.md`，与本表 I130 行 `I130-mysql-add-column-if-not-exists-incompatible.md` **编号重名、内容不同**，以文件为准）：§5.2 mkt 粗码缺失已 Fixed（batch2-B）；§5.3 manager01 `/market/*` 菜单断裂已 Fixed（batch2-B）。batch2-B 于 2026-11-09 执行，新增 20 个权限码 + 12 条菜单行 + 245 条角色绑定，仅动 `sys_permission` / `sys_role_permission` / `sys_menu_permission`，未改前后端代码；证据见 `audit/evidence/F149-mkt-perm/`。本条 issue 状态由 MISSING / PARTIAL-NON-BLOCKER 调整为 **PARTIAL**（营销域已修复，其余非营销域 23 个缺失守卫码与 4 条菜单断裂仍 Open，归属 I124-c）。
