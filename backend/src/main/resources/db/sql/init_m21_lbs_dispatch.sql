-- =============================================================================
-- Phase 3 · 上门服务 LBS 路径优化（v2 — 路线算法 + 区域 + 技能 + 日志）
-- 创建日期: 2026-09-05
-- 关联模块: M01 预约排班 + Phase 3 创新化
-- 关联实体: lbs_technician / lbs_service_order（已有） / lbs_technician_skill（新）
--            / lbs_service_zone（新）/ lbs_dispatch_log（新）/ lbs_dispatch_config（新）
--            / lbs_route_plan（新）/ lbs_location_history（新）
--
-- 设计目标:
--   1. 增强 LBS 派单能力（从单订单 Haversine → 区域约束 + 技能匹配 + 多订单路径优化）
--   2. 派单决策可解释（dispatch_log 写完整打分明细）
--   3. 路线规划可审计（route_plan + location_history）
--   4. 策略可配置（dispatch_config 按租户 + 门店）
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1. lbs_technician 扩展：技能标签 JSON / 当前路线容量 / 服务区域 ID
-- ----------------------------------------------------------------------------
ALTER TABLE lbs_technician
    ADD COLUMN IF NOT EXISTS skill_tags VARCHAR(512) DEFAULT NULL COMMENT '技能标签 JSON（数组）',
    ADD COLUMN IF NOT EXISTS skill_levels JSON DEFAULT NULL COMMENT '技能等级 {"美甲":5,"美睫":3}',
    ADD COLUMN IF NOT EXISTS home_base_lat DECIMAL(10,6) DEFAULT NULL COMMENT '出发地纬度（家/门店）',
    ADD COLUMN IF NOT EXISTS home_base_lng DECIMAL(10,6) DEFAULT NULL COMMENT '出发地经度',
    ADD COLUMN IF NOT EXISTS zone_id BIGINT DEFAULT NULL COMMENT '默认服务区域 ID',
    ADD COLUMN IF NOT EXISTS max_orders_per_day INT DEFAULT 8 COMMENT '单日最大订单数',
    ADD COLUMN IF NOT EXISTS avg_service_minutes INT DEFAULT 90 COMMENT '平均单订单服务时长',
    ADD COLUMN IF NOT EXISTS travel_speed_kmh DECIMAL(4,1) DEFAULT 25.0 COMMENT '平均时速（km/h）',
    ADD COLUMN IF NOT EXISTS work_start TIME DEFAULT '09:00:00' COMMENT '工作开始时间',
    ADD COLUMN IF NOT EXISTS work_end   TIME DEFAULT '21:00:00' COMMENT '工作结束时间';

-- ----------------------------------------------------------------------------
-- 2. lbs_service_order 扩展：派单分 / 拒绝原因 / 路线顺序
-- ----------------------------------------------------------------------------
ALTER TABLE lbs_service_order
    ADD COLUMN IF NOT EXISTS dispatch_score DECIMAL(6,2) DEFAULT NULL COMMENT '派单综合分（0-100）',
    ADD COLUMN IF NOT EXISTS dispatch_breakdown JSON DEFAULT NULL COMMENT '派单维度明细',
    ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(255) DEFAULT NULL COMMENT '未派/拒绝原因',
    ADD COLUMN IF NOT EXISTS route_seq INT DEFAULT NULL COMMENT '技师路线中的顺序号',
    ADD COLUMN IF NOT EXISTS eta_minutes INT DEFAULT NULL COMMENT '预计到达分钟数（从技师当前位置）',
    ADD COLUMN IF NOT EXISTS scheduled_end DATETIME DEFAULT NULL COMMENT '预计服务结束时间';

-- ----------------------------------------------------------------------------
-- 3. lbs_technician_skill — 技师技能矩阵
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_technician_skill;
CREATE TABLE lbs_technician_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1,
    technician_id BIGINT NOT NULL COMMENT 'lbs_technician.id（不是 pos_employee.id）',
    skill_code VARCHAR(64) NOT NULL COMMENT '技能编码：MANICURE/PEDICURE/EYELASH/...',
    skill_name VARCHAR(64) NOT NULL COMMENT '技能名称',
    skill_level TINYINT NOT NULL DEFAULT 3 COMMENT '等级 1-5（5=专家）',
    certified TINYINT NOT NULL DEFAULT 0 COMMENT '是否持证',
    years_of_experience DECIMAL(4,1) DEFAULT 0.0 COMMENT '从业年限',
    note VARCHAR(255) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tech_skill (tenant_id, store_id, technician_id, skill_code, deleted),
    INDEX idx_skill (skill_code),
    INDEX idx_level (skill_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门技师技能矩阵';

-- ----------------------------------------------------------------------------
-- 4. lbs_service_zone — 服务区域（圆形 / 多边形）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_service_zone;
CREATE TABLE lbs_service_zone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1,
    zone_code VARCHAR(64) NOT NULL COMMENT '区域编码',
    zone_name VARCHAR(64) NOT NULL COMMENT '区域名称（如：朝阳区/CBD）',
    zone_type VARCHAR(16) NOT NULL DEFAULT 'CIRCLE' COMMENT 'CIRCLE=圆形 / POLYGON=多边形',
    center_lat DECIMAL(10,6) DEFAULT NULL COMMENT '中心点纬度（CIRCLE 必填）',
    center_lng DECIMAL(10,6) DEFAULT NULL COMMENT '中心点经度',
    radius_km DECIMAL(5,2) DEFAULT NULL COMMENT '半径 km（CIRCLE 必填）',
    polygon_json JSON DEFAULT NULL COMMENT '多边形顶点 [[lng,lat],...]（POLYGON 必填）',
    service_fee_per_km DECIMAL(5,2) DEFAULT 2.00 COMMENT '上门服务费 ¥/km',
    base_fee DECIMAL(6,2) DEFAULT 0.00 COMMENT '基础上门费',
    min_fee DECIMAL(6,2) DEFAULT 0.00 COMMENT '上门费下限',
    max_fee DECIMAL(6,2) DEFAULT 999.00 COMMENT '上门费上限',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT DEFAULT 0,
    operator_name VARCHAR(64),
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_zone (tenant_id, store_id, zone_code, deleted),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门服务区域（支持圆形 / 多边形）';

-- ----------------------------------------------------------------------------
-- 5. lbs_dispatch_log — 派单决策日志（explainable AI）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_dispatch_log;
CREATE TABLE lbs_dispatch_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1,
    order_id BIGINT NOT NULL COMMENT 'lbs_service_order.id',
    order_no VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL/PREVIEW/REASSIGN',
    candidates_json JSON DEFAULT NULL COMMENT '候选技师+评分 JSON',
    chosen_technician_id BIGINT DEFAULT NULL COMMENT '最终选中的技师（NULL=未派）',
    chosen_score DECIMAL(6,2) DEFAULT NULL COMMENT '选中综合分',
    breakdown_json JSON DEFAULT NULL COMMENT '5 维度明细',
    decision VARCHAR(16) NOT NULL DEFAULT 'DISPATCHED' COMMENT 'DISPATCHED/REJECTED',
    reject_reason VARCHAR(255) DEFAULT NULL COMMENT '拒派原因',
    duration_ms INT DEFAULT 0 COMMENT '算法耗时',
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_order (tenant_id, order_id),
    INDEX idx_tech (chosen_technician_id),
    INDEX idx_create (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单决策日志（可解释 AI 审计）';

-- ----------------------------------------------------------------------------
-- 6. lbs_dispatch_config — 派单策略配置
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_dispatch_config;
CREATE TABLE lbs_dispatch_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1 COMMENT '0=租户级',
    -- 5 维度权重
    weight_distance DECIMAL(4,2) NOT NULL DEFAULT 0.35 COMMENT '距离权重',
    weight_skill   DECIMAL(4,2) NOT NULL DEFAULT 0.25 COMMENT '技能匹配权重',
    weight_rating  DECIMAL(4,2) NOT NULL DEFAULT 0.15 COMMENT '评分权重',
    weight_load    DECIMAL(4,2) NOT NULL DEFAULT 0.15 COMMENT '负荷权重（越轻越高）',
    weight_time    DECIMAL(4,2) NOT NULL DEFAULT 0.10 COMMENT '时间窗匹配权重',
    -- 路线策略
    route_strategy VARCHAR(16) NOT NULL DEFAULT 'NEAREST_NEIGHBOR' COMMENT 'NEAREST_NEIGHBOR/GREEDY/MANUAL',
    optimize_total_travel TINYINT NOT NULL DEFAULT 1 COMMENT '是否优化总行驶距离',
    max_orders_per_tech INT NOT NULL DEFAULT 8 COMMENT '单技师单日最大订单数',
    -- 区域策略
    strict_zone_match TINYINT NOT NULL DEFAULT 0 COMMENT '严格区域匹配（0=可跨区域 1=必须区域内）',
    fallback_to_nearest TINYINT NOT NULL DEFAULT 1 COMMENT '区域内无技师时降级到最近',
    -- 时间窗
    default_service_minutes INT NOT NULL DEFAULT 90 COMMENT '默认服务时长（分钟）',
    buffer_minutes INT NOT NULL DEFAULT 15 COMMENT '相邻订单缓冲时间',
    -- 评分阈值
    min_dispatch_score DECIMAL(5,2) NOT NULL DEFAULT 40.00 COMMENT '派单最低分（低于则拒派）',
    -- 业务开关
    enabled TINYINT NOT NULL DEFAULT 1,
    operator_name VARCHAR(64),
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_store (tenant_id, store_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单策略配置（5 维度权重）';

-- ----------------------------------------------------------------------------
-- 7. lbs_route_plan — 路线规划
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_route_plan;
CREATE TABLE lbs_route_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1,
    plan_no VARCHAR(64) NOT NULL COMMENT '路线编号',
    technician_id BIGINT NOT NULL COMMENT 'lbs_technician.id',
    plan_date DATE NOT NULL COMMENT '路线日期',
    start_lat DECIMAL(10,6) DEFAULT NULL COMMENT '起点纬度',
    start_lng DECIMAL(10,6) DEFAULT NULL COMMENT '起点经度',
    total_orders INT DEFAULT 0 COMMENT '订单数',
    total_distance_km DECIMAL(7,2) DEFAULT 0 COMMENT '总里程 km',
    total_travel_minutes INT DEFAULT 0 COMMENT '总行驶分钟',
    total_service_minutes INT DEFAULT 0 COMMENT '总服务分钟',
    eta_complete DATETIME DEFAULT NULL COMMENT '预计完成时间',
    algorithm VARCHAR(32) DEFAULT 'NEAREST_NEIGHBOR' COMMENT '使用的算法',
    optimize_gain_km DECIMAL(7,2) DEFAULT 0 COMMENT '优化节省 km（对比随机）',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/COMPLETED/CANCELLED',
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan (tenant_id, plan_no),
    INDEX idx_tech_date (technician_id, plan_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门路线规划';

-- ----------------------------------------------------------------------------
-- 8. lbs_location_history — 位置上报历史（用于回溯 / 路线优化）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lbs_location_history;
CREATE TABLE lbs_location_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    technician_id BIGINT NOT NULL,
    lat DECIMAL(10,6) NOT NULL,
    lng DECIMAL(10,6) NOT NULL,
    accuracy_m DECIMAL(8,2) DEFAULT NULL COMMENT '精度 m',
    speed_kmh DECIMAL(5,1) DEFAULT NULL COMMENT '时速',
    battery_pct TINYINT DEFAULT NULL COMMENT '电量百分比',
    source VARCHAR(16) DEFAULT 'APP' COMMENT 'APP/MANUAL/SIMULATE',
    reported_at DATETIME NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tech_time (technician_id, reported_at),
    INDEX idx_reported (reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师位置上报历史';

-- ----------------------------------------------------------------------------
-- 9. 默认配置
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO lbs_dispatch_config
    (tenant_id, store_id, weight_distance, weight_skill, weight_rating, weight_load, weight_time,
     route_strategy, optimize_total_travel, max_orders_per_tech, strict_zone_match,
     fallback_to_nearest, default_service_minutes, buffer_minutes, min_dispatch_score,
     enabled, operator_name, remark)
VALUES
    (1, 1, 0.35, 0.25, 0.15, 0.15, 0.10,
     'NEAREST_NEIGHBOR', 1, 8, 0, 1, 90, 15, 40.00,
     1, 'system',
     '默认权重：距离 35% + 技能 25% + 评分 15% + 负荷 15% + 时间窗 10%');

-- 默认区域（覆盖北京 CBD 中心 10km 范围）
INSERT IGNORE INTO lbs_service_zone
    (tenant_id, store_id, zone_code, zone_name, zone_type, center_lat, center_lng, radius_km,
     service_fee_per_km, base_fee, min_fee, max_fee, enabled, operator_name, remark)
VALUES
    (1, 1, 'BJ-CBD-10K', '北京 CBD 10公里圈', 'CIRCLE', 39.904200, 116.407400, 10.00,
     2.00, 30.00, 30.00, 200.00, 1, 'system', '天安门为中心 10km 范围');

-- ----------------------------------------------------------------------------
-- 10. RBAC 权限
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:booking:lbs-dispatch', '上门服务 LBS 派单', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:booking') t LIMIT 1),
       20;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','DISPATCHER')
  AND p.permission_code = 'biz:booking:lbs-dispatch';

-- ----------------------------------------------------------------------------
-- 11. 验证
-- ----------------------------------------------------------------------------
SELECT 'lbs_dispatch_config' AS tbl, COUNT(*) AS cnt FROM lbs_dispatch_config
UNION ALL SELECT 'lbs_service_zone', COUNT(*) FROM lbs_service_zone
UNION ALL SELECT 'lbs_route_plan', COUNT(*) FROM lbs_route_plan
UNION ALL SELECT 'lbs_dispatch_log', COUNT(*) FROM lbs_dispatch_log
UNION ALL SELECT 'lbs_technician_skill', COUNT(*) FROM lbs_technician_skill
UNION ALL SELECT 'lbs_location_history', COUNT(*) FROM lbs_location_history;