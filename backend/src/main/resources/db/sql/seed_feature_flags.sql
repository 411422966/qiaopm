-- ============================================
-- AI-006：Feature Flag 种子数据
-- 12 个具体灰度实验规则，覆盖所有产品域
-- 用途：feature_flag 表初始化 / 迁移后补充
-- 依赖：feature_flag 表（ai_ddl.sql 已创建）
-- ============================================

-- -------------------------------------------------------
-- P2-MONEY-001：BNPL 先买后付（Klarna / Afterpay）
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'bnpl-klarna-enabled', 'BNPL-Klarna 支付通道', '开启后用户可在结账页选择 Klarna 分期付款（仅在 bnpl.klarna.client-id 配好后生效）', 1, 20, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'p2-money');

INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'bnpl-afterpay-enabled', 'BNPL-Afterpay 支付通道', '开启后用户可在结账页选择 Afterpay 分期付款（仅在 bnpl.afterpay.merchant-id 配好后生效）', 0, 0, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'p2-money');

-- -------------------------------------------------------
-- IOT-006：IoT MQTT 设备联调
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'iot-mqtt-enabled', 'IoT-MQTT 实时通道', '开启后 IoT 设备使用 MQTT 5.0 协议接入（HiveMQ Cloud）；关闭则降级 HTTP 轮询', 0, 10, NULL, NULL, 'STORE_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'iot-team');

-- -------------------------------------------------------
-- EXT-22：Google Maps Place Details（计费接口）
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'google-maps-place-details', 'Google Maps Place Details 计费接口', '开启后调用 Google Places API Place Details（11 fields，触发 3 个 SKU，约 $0.025/次）；关闭则降级 mock 数据', 0, 0, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'ext-ext-team');

-- -------------------------------------------------------
-- EXT-23：Plugin Runtime 插件运行时
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'plugin-runtime-enabled', 'Plugin Runtime 插件运行时', '开启后 pf4j 插件体系激活，业务代码可调用 PluginRuntimeService.isEnabledForTenant 校验', 1, 100, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'ext-ext-team');

-- -------------------------------------------------------
-- FISCAL-001：财税开票（百望真实对接）
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'fiscal-baiwang-enabled', '财税-百望开票网关', '开启后 fiscal.mode=baiwang，对接真实百望税盘；关闭则 mock 模式（不触发真实开票）', 0, 0, NULL, NULL, 'STORE_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'fiscal-team');

-- -------------------------------------------------------
-- P0-MONEY-001：DCEP 数字货币支付
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'dcep-mode-production', 'DCEP 生产模式', '开启后 app.dcep.mode=prod，禁止 Mock 行为，必须对接真实银行 SDK；关闭则 dev/mock 模式（开发演示用）', 0, 0, NULL, NULL, 'STORE_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'p0-money-team');

-- -------------------------------------------------------
-- MKT-01：生日自动祝福（CRM）
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'birthday-greeting-auto', '生日自动祝福', '开启后系统每日扫描即将生日的会员，按 mkt_channel_config 渠道配置自动发送祝福（SMS/EMAIL/WECHAT）', 1, 100, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'crm-team');

-- -------------------------------------------------------
-- POS-008：退款审计链
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'pos-refund-audit-chain', 'POS 退款多级审计', '开启后每笔退款需经过「操作员提交 → 店长审批 → 区域主管复核」三级审计链；关闭则单级直接退款', 0, 0, NULL, NULL, 'STORE_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'pos-team');

-- -------------------------------------------------------
-- COMP-001：技师提成模拟
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'commission-simulation', '技师提成模拟计算', '开启后店长可在「提成规则」页发起模拟，输入服务项目/金额/技师，实时预览各规则下的提成金额', 1, 100, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'commission-team');

-- -------------------------------------------------------
-- R3-0-B：跨店套餐核销
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'cross-store-redemption', '跨店套餐核销', '开启后会员购买的套餐可在非同品牌门店核销（受 crm_cross_store_rule 规则约束）；关闭则仅限购买门店', 0, 0, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'crm-team');

-- -------------------------------------------------------
-- INTEL-001：LTV 预测模型
-- -------------------------------------------------------
INSERT IGNORE INTO feature_flag (tenant_id, flag_key, flag_name, description, enabled, rollout_percent, whitelist, blacklist, user_rule, start_at, end_at, owner)
VALUES (1, 'ltv-prediction-enabled', 'LTV 客户生命周期价值预测', '开启后系统基于 RFM + 行为数据周期性计算会员 LTV 分值，并在 CRM 看板展示流失预警', 0, 10, NULL, NULL, 'USER_ID', '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'intel-team');

-- -------------------------------------------------------
-- 验证查询
-- -------------------------------------------------------
SELECT
    COUNT(*) AS total_flags,
    SUM(enabled = 1) AS enabled_count,
    SUM(rollout_percent > 0 AND rollout_percent < 100) AS partial_rollout,
    SUM(enabled = 1 AND rollout_percent = 100) AS full_rollout,
    SUM(enabled = 0) AS disabled_count
FROM feature_flag;

SELECT flag_key, flag_name, enabled, rollout_percent, owner, start_at, end_at
FROM feature_flag
ORDER BY owner, flag_key;
