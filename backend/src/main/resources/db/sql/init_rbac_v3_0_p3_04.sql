-- ============================================
-- P3 · T-P04：动态定价（按技师/时段/项目）
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 定价规则（按技师/时段/项目/会员等级）
-- ============================================
DROP TABLE IF EXISTS mkt_dynamic_pricing_rule;
CREATE TABLE mkt_dynamic_pricing_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    -- 类型
    rule_type VARCHAR(32) NOT NULL COMMENT 'TECHNICIAN按技师/TIME_SLOT按时段/PROJECT按项目/MEMBER_LEVEL按会员等级/STORE按门店',
    -- 触发对象
    target_type VARCHAR(32) NOT NULL COMMENT 'TECHNICIAN/TIME_SLOT/PROJECT/MEMBER_LEVEL/STORE',
    target_id BIGINT,
    target_code VARCHAR(64) COMMENT '技师编号/时段编码/项目编号',
    target_name VARCHAR(128),
    -- 规则
    pricing_mode VARCHAR(16) NOT NULL COMMENT 'PERCENT百分比/FIXED固定/MULTIPLIER倍数',
    pricing_value DECIMAL(8,2) NOT NULL COMMENT '正值=加价，负值=折扣',
    -- 条件
    min_amount DECIMAL(12,2) COMMENT '最低消费金额',
    max_amount DECIMAL(12,2) COMMENT '最高消费金额',
    -- 时段
    time_start TIME,
    time_end TIME,
    day_of_week VARCHAR(20) COMMENT '1,2,3,4,5,6,7 (周一到周日)',
    -- 优先级
    priority INT DEFAULT 0,
    -- 状态
    enabled TINYINT DEFAULT 1,
    -- 时段
    valid_from DATE,
    valid_to DATE,
    -- 描述
    description VARCHAR(500),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, rule_code),
    INDEX idx_target (target_type, target_id, enabled, deleted),
    INDEX idx_priority (priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态定价规则';

-- ============================================
-- 2. 定价计算日志
-- ============================================
DROP TABLE IF EXISTS mkt_pricing_log;
CREATE TABLE mkt_pricing_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    -- 上下文
    project_id BIGINT,
    project_name VARCHAR(128),
    technician_id BIGINT,
    technician_name VARCHAR(64),
    member_id BIGINT,
    member_level_id BIGINT,
    store_id BIGINT,
    -- 时段
    pricing_time DATETIME,
    -- 输入
    base_amount DECIMAL(12,2),
    -- 命中规则
    hit_rule_id BIGINT,
    hit_rule_code VARCHAR(64),
    -- 结果
    final_amount DECIMAL(12,2),
    discount_amount DECIMAL(12,2),
    rules_json TEXT COMMENT '命中的所有规则 JSON',
    -- 收银台可见
    checkout_visible TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project (project_id, create_time),
    INDEX idx_member (member_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价计算日志';

-- ============================================
-- 3. 演示规则
-- ============================================
INSERT IGNORE INTO mkt_dynamic_pricing_rule
(tenant_id, brand_id, rule_code, rule_name, rule_type, target_type, target_id, target_code, target_name,
 pricing_mode, pricing_value, priority, enabled, valid_from, valid_to, description, operator_name)
VALUES
(1, 1, 'PRI-DIS10', '周二周三 8 折', 'TIME_SLOT', 'TIME_SLOT', NULL, 'TUE_WED',
 '周二周三', 'PERCENT', -10.00, 90, 1, '2026-01-01', '2026-12-31', '周二周三全单 8 折', 'system'),
(1, 1, 'PRI-EVEUP20', '周末晚 18-21 时加价 20%', 'TIME_SLOT', 'TIME_SLOT', NULL, 'WEEKEND_EVE',
 '周末晚高峰', 'PERCENT', 20.00, 80, 1, '2026-01-01', '2026-12-31', '周五-周日 18-21 时', 'system'),
(1, 1, 'PRI-NOVUP15', '新手技师 +15%', 'TECHNICIAN', 'TECHNICIAN', NULL, 'JUNIOR',
 '新手', 'PERCENT', 15.00, 60, 1, '2026-01-01', '2026-12-31', '入职<1 年的技师', 'system'),
(1, 1, 'PRI-CHEFUP30', '首席技师 +30%', 'TECHNICIAN', 'TECHNICIAN', NULL, 'CHIEF',
 '首席', 'PERCENT', 30.00, 70, 1, '2026-01-01', '2026-12-31', '首席/院长级', 'system'),
(1, 1, 'PRI-VIPDOWN15', '黑金会员 9.5 折', 'MEMBER_LEVEL', 'MEMBER_LEVEL', NULL, 'BLACK',
 '黑金', 'PERCENT', -5.00, 50, 1, '2026-01-01', '2026-12-31', '黑金及以上会员', 'system'),
(1, 1, 'PRI-PROJ500OFF', '项目满 500 减 50', 'PROJECT', 'PROJECT', NULL, 'ALL',
 '所有项目', 'FIXED', -50.00, 40, 1, '2026-01-01', '2026-12-31', '满 500 减 50', 'system');

-- ============================================
-- 4. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:pricing', '动态定价', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 30;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','MARKETING')
  AND p.permission_code = 'mkt:pricing';

-- ============================================
-- 5. 验证
-- ============================================
SELECT 'mkt_dynamic_pricing_rule' tbl, COUNT(*) cnt FROM mkt_dynamic_pricing_rule WHERE deleted=0
UNION ALL SELECT 'mkt_pricing_log', COUNT(*) FROM mkt_pricing_log;