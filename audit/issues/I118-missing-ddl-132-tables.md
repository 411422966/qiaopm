# I118 — 132 张表的 DDL 缺失但实体/Mapper/Controller 完整（P0）

## 现象
- 实体类（@TableName）+ Mapper + Controller 完整
- 数据库无对应表（information_schema 核实）
- 12 个 Controller 无条件暴露（无 @ConditionalOnProperty / @Profile / @Deprecated）

## 12 个暴露 Controller（P0）
| Controller | 路径 | 表 |
|---|---|---|
| CommissionConditionController | /commission/condition | biz_commission_condition |
| CommissionSimulationController | /commission/simulation | biz_commission_simulation |
| CommissionTierController | /commission/tier | biz_commission_tier |
| ExpiryHandleLogController | /stock/expiry/handle | biz_expiry_handle_log |
| MaterialConsumptionLogController | /material/consumption | biz_material_consumption_log |
| MaterialSubstituteController | /material/substitute | biz_material_substitute |
| ResourceConflictLogController | /resource/conflict | biz_resource_conflict_log |
| ResourceUtilizationController | /resource/utilization | biz_resource_utilization |
| ServiceMaterialDeductLogController | /stock/service-material/deduct-log | biz_service_material_deduct_log |
| ServiceMaterialGroupController | /material/group | biz_service_material_group |
| ServiceMaterialRuleController | /stock/service-material/rule | biz_service_material_rule |
| StockShortageLogController | /stock/shortage | biz_stock_shortage_log |

## 10+ 张表被 jdbcTemplate 直接 SQL 引用（P0 间接）
- CareFormulaService: care_formula / care_formula_apply
- ChurnPredictionService: crm_churn_alert / crm_churn_prediction
- ReviewAnalysisService: crm_review_dimension_config / crm_service_improvement
- OrgHierarchyService: pos_employee_store
- BizTreatmentStepService: member_treatment_step_log
- P1RemainingService: biz_forecast_demand
- P2FeaturesService: crm_package_share

## 2 张死代码（P2）
- biz_material_package_item
- biz_package_style_relation

## 97 张基础设施/集成表（P1）
cloud_* / dr_* / monitor_* / platform_* / wx_* / wms_* / iot_* / lbs_* / open_* 等
均有 @TableName 引用，需逐个核实是否有 Controller/Service 调用

## 根因
- Flyway 仅 1 个 V1__init_baseline.sql，配置 baseline-version: 2 强制跳过
- 真实建表分散在 db/sql 下 170 个脚本，部分未执行
- 缺失表集中在「扩展功能」：佣金规则、耗材管理、资源调度、过期处理、流失预测

## 影响
- 12 个 Controller 一调就 500（Table doesn't exist）
- PROJECT_ACCEPTANCE_EVIDENCE.md 的 READY TO CLOSE 结论不成立
- 用户可能已在生产触发过这些接口

## 处置
- [ ] P0：12 个暴露 Controller 补 DDL 或下线 Controller
- [ ] P1：10+ jdbcTemplate 引用的表补 DDL
- [ ] P1：97 张基础设施表逐个核实
- [ ] P2：2 张死代码清理
- [ ] 修正 PROJECT_ACCEPTANCE_EVIDENCE.md

## 状态
待办

## 止血完成（2026-10-19）
- 12 个 Controller 加 @ConditionalOnProperty(name="feature.legacy-missing-ddl.enabled", havingValue="true", matchIfMissing=false)
- application.yml 合并到已有 feature: 节点，新增 legacy-missing-ddl.enabled: false
- 编译 PKG_EXIT=0，镜像重建，容器 healthy，启动成功
- 验证：8 个路径返回 404（原 500），核心接口待验证

## 状态
已止血（待补 DDL 或下线 Controller 后彻底关闭）

## 补充（2026-10-19 验证）
- 核心接口回归：/member/list → 200，/booking/list → 200（未误伤）
- 8 个止血路径：全部 404（原 200 + code:500）

## 下次开工（2026-10-19 23:50）
- 优先排查：10+ 张 jdbcTemplate 引用的表对应哪些 Controller（无开关保护）
- 次优先：97 张基础设施表分类
- 建议清醒时做，避免误判

## jdbcTemplate 引用表核查（2026-10-19 23:55）
- 7 个 Service（CareFormula / ChurnPrediction / ReviewAnalysis / OrgHierarchy / BizTreatmentStep / P1Remaining / P2Features）
- 全仓库扫描结果：
  - 6 个零引用（无 Controller、无 Service、无 Scheduled、无 EventListener）
  - OrgHierarchyService 仅出现在 RbacContextLoader.java:64 的注释中，无实际调用
- 结论：这 7 个 Service 及其 jdbcTemplate 引用的 10+ 张表均为死代码，**不会触发 500**
- 影响：I118 的「10+ 张 jdbcTemplate 引用」风险等级从 P0 降为 P2（死代码清理）
- 待办调整：
  - 原 P0「补 DDL」→ 降为 P2「清理死代码」
  - 剩余 P0 仍为 97 张基础设施表的 Controller 暴露排查

## 状态
已止血（12 Controller 关闭），jdbcTemplate 风险已排除，待 97 张表排查

## 97 张基础设施表排查结果（2026-10-20）
- 剩余 101 张表（排除 41 已处理）
- 其中 1 个是 SQL regex 残留 `IF`，实际 100 张

### 直接暴露的 Controller（4 个，P0 需处置）
| Controller | 表 | 路由 |
|---|---|---|
| IotDeviceLinkController | iot_device_link_log | /iot/device/link |
| MultiAccountSplitController | m02_split_record / m02_split_record_detail / m02_split_rule | /m02/split |
| BusinessPolicyDispatchController | m12_dispatch_log | /m12/dispatch |
| YouzanWebhookController | webhook_msg_log | /webhook/youzan |

### INDIRECT Service（27 个，其中 ALIVE 2 个、DEAD 25 个）
**ALIVE（需处置）：**
| Service | 表 | 引用 Controller |
|---|---|---|
| InviteService | m09_invite_campaign / m09_invite_code / m09_invite_record / m09_invite_reward_log | InviteController, MemberInviteController |
| TreatmentReportService | treatment_report | BizTreatmentReportController |

**DEAD（死代码，归入清理清单，25 个）：**
ChurnPredictionService, CloudNativeService, CrossPlatformService, CrossWarehouseService, DcepConfigServiceImpl, DeviceManagementService, DisasterRecoveryService, DrillExerciseService, EventSourcingService, FiscalConfigServiceImpl, FrontendTemplateService, I18nService, LbsDispatchService, MicroserviceDecomposeService, MobileApiService, MonitorAlertService, MultiDeviceSyncService, OpenPlatformEnhanceService, P1RemainingService, P2FeaturesService, P3EcosystemService, PushNotificationService, SecurityComplianceService, SlaReportService, ThirdPartyIntegrationService

对应死表 70+ 张（cloud_* / dr_* / monitor_* / open_* / platform_* / wms_* / wx_* / sync_* / sla_* / i18n_* 等）

### 死代码表（4 张，无 Entity 引用）
booking_waitlist_score_config, booking_waitlist_score_log, mkt_effect_snapshot
（IF 是 regex 残留，需修脚本）

## 状态
待处置：4 直接暴露 Controller + 2 ALIVE Service（共 6 个目标）

## 第二批止血完成（2026-10-20）
- 4 个 Controller 加 @ConditionalOnProperty 默认关闭：
  - MultiAccountSplitController（m02_split_rule 缺失，日志铁证 Table doesn't exist）
  - InviteController（m09_invite_campaign 缺失）
  - MemberInviteController（同 InviteService，表缺失）
  - BizTreatmentReportController（treatment_report 缺失）
- 编译 EXIT=0，镜像重建，容器 healthy，启动 23.152s
- 验证：4 个路径全部 404（原 200 + code:500）
- 回归：/member/list 和 /booking/list 均 200

## 累计止血
- 第一批（2026-10-19）：12 个 Controller
- 第二批（2026-10-20）：4 个 Controller
- 合计：16 个 Controller 默认关闭

## 剩余待办
- 25 个 DEAD Service 对应的 70+ 张表 → 归入死代码清理（P2）
- 4 张无 Entity 死表（booking_waitlist_score_config/log、mkt_effect_snapshot）→ 清理（P2）
- 3 个探活 200 但表缺失的 Controller（IotDeviceLink / BusinessPolicyDispatch / YouzanWebhook）→ 表可能存在或有 fallback，暂不处理
- I119：TechnicianLevelServiceImpl 写错表名（待修）
- I121：GrayReleaseService 命名与职责不符（待修）

## 状态
已止血（16/16 目标完成），I118 P0 部分闭环

## 第 4g-3 批扫描新发现的 7 个死代码 Controller（2026-10-21）
以下 Controller 依赖表在 DB 不存在，属 I118 同类问题，需在 I118 死代码清理时一并处理：
- BusinessPolicyDispatchController（缺 m12_dispatch_log）
- CrossStoreInventoryController（缺 wms_stock_transfer）
- DcepConfigModeController（缺 dcep_config）
- FiscalConfigController（缺 fiscal_config）
- ItemPreferenceController（缺 crm_member_item_preference）
- LtvChurnController（缺 crm_churn_prediction）
- QualityImprovementController（缺 crm_service_improvement, crm_service_improvement_log）

## 第 4g-3d 批扫描补记（2026-10-21）
- 8 个 DEAD Controller 与之前记录一致，额外发现：
  - YouzanWebhookController（依赖 webhook_msg_log 死表）— 之前误判为 webhook 不加权限，实际依赖死表，应归入 I118 清理
