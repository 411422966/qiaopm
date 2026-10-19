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
