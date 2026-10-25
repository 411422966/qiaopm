# Issue Register

> 重建于 2026-10-16（原 register 受 I112 编码损坏，见 I112），最近更新 2026-10-25（补登 I115~I126）
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
| I115 | I115 — 权限注解覆盖率不足（P0） | Fixed | [I115-permission-coverage-gap.md](I115-permission-coverage-gap.md) |
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

## 备注

- **I119**：代码+数据已修复，运行时晋升验证待补。
