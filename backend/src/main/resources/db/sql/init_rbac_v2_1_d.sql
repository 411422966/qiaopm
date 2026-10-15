-- ============================================
-- R2.1-D：营销与订单优惠联动 - 数据库初始化
-- 2026-08-09
-- ============================================

-- 1. 营销活动主表（满减/折扣/买赠/付费优惠券）
DROP TABLE IF EXISTS mkt_promotion;
CREATE TABLE mkt_promotion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    promotion_code VARCHAR(64) NOT NULL,
    promotion_name VARCHAR(128) NOT NULL,
    promotion_type VARCHAR(16) NOT NULL COMMENT 'FULL_REDUCE/DISCOUNT/GIFT/CASH_COUPON/POINTS_EXCHANGE',
    description VARCHAR(500),
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    threshold_amount DECIMAL(12,2) DEFAULT 0,
    reduce_amount DECIMAL(12,2) DEFAULT 0,
    discount_rate DECIMAL(3,2) DEFAULT 1.00,
    scope_type VARCHAR(16) DEFAULT 'ALL' COMMENT 'ALL/SERVICE/PRODUCT/CATEGORY',
    scope_ids VARCHAR(500) COMMENT 'JSON 列表',
    member_level_ids VARCHAR(500) COMMENT 'JSON 列表',
    stackable TINYINT DEFAULT 0,
    exclusive_codes VARCHAR(500) COMMENT 'JSON 列表',
    quota_total INT DEFAULT 0,
    quota_used INT DEFAULT 0,
    per_member_limit INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用 2结束',
    priority INT DEFAULT 0,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, promotion_code),
    INDEX idx_status (status, deleted),
    INDEX idx_period (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动';

-- 2. 优惠券批次
DROP TABLE IF EXISTS mkt_coupon_batch;
CREATE TABLE mkt_coupon_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    batch_no VARCHAR(64) NOT NULL,
    promotion_id BIGINT,
    coupon_name VARCHAR(128) NOT NULL,
    coupon_type VARCHAR(16) NOT NULL COMMENT 'CASH/PERCENT/POINTS',
    discount_amount DECIMAL(12,2) DEFAULT 0,
    discount_rate DECIMAL(3,2) DEFAULT 1.00,
    min_consume DECIMAL(12,2) DEFAULT 0,
    discount_scope VARCHAR(16) DEFAULT 'ALL',
    total_count INT DEFAULT 0,
    issued_count INT DEFAULT 0,
    used_count INT DEFAULT 0,
    effective_days INT DEFAULT 30,
    fixed_start_date DATE,
    fixed_end_date DATE,
    grant_type VARCHAR(16) DEFAULT 'GENERAL',
    status TINYINT DEFAULT 1 COMMENT '1草稿 2发放中 3已结束 4取消',
    expire_warning_days INT DEFAULT 3,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_batch (tenant_id, batch_no),
    INDEX idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券批次';

-- 3. 优惠券
DROP TABLE IF EXISTS mkt_coupon;
CREATE TABLE mkt_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    batch_id BIGINT NOT NULL,
    coupon_code VARCHAR(64) NOT NULL,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    coupon_type VARCHAR(16) NOT NULL,
    discount_amount DECIMAL(12,2) DEFAULT 0,
    discount_rate DECIMAL(3,2) DEFAULT 1.00,
    min_consume DECIMAL(12,2) DEFAULT 0,
    discount_scope VARCHAR(16) DEFAULT 'ALL',
    status TINYINT DEFAULT 1 COMMENT '1未使用 2已使用 3已过期 4已作废',
    used_order_id BIGINT,
    used_time DATETIME,
    used_amount DECIMAL(12,2),
    effective_date DATE NOT NULL,
    expire_date DATE NOT NULL,
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(64),
    idempotency_key VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, coupon_code),
    INDEX idx_member (member_id, status, deleted),
    INDEX idx_expire (status, expire_date),
    INDEX idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券';

-- 4. 订单优惠明细
DROP TABLE IF EXISTS mkt_order_discount;
CREATE TABLE mkt_order_discount (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    source_type VARCHAR(16) NOT NULL COMMENT 'PROMOTION/COUPON/POINTS/MANUAL',
    source_id BIGINT,
    source_name VARCHAR(128),
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    coupon_id BIGINT,
    points_used INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1已应用 2已撤销',
    refund_amount DECIMAL(12,2) DEFAULT 0,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order (order_id),
    INDEX idx_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单优惠明细';

-- 5. 积分规则
DROP TABLE IF EXISTS mkt_points_rule;
CREATE TABLE mkt_points_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    rule_name VARCHAR(64) NOT NULL,
    consume_rate DECIMAL(5,2) DEFAULT 1.00,
    recharge_multiplier DECIMAL(3,2) DEFAULT 1.00,
    promotion_multiplier DECIMAL(3,2) DEFAULT 1.00,
    points_to_cash DECIMAL(5,2) DEFAULT 0.01,
    max_use_percent DECIMAL(5,2) DEFAULT 30.00,
    enabled TINYINT DEFAULT 1,
    priority INT DEFAULT 0,
    effective_date DATE,
    expiry_date DATE,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant_enabled (tenant_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分规则';

-- 6. 积分流水
DROP TABLE IF EXISTS mkt_points_log;
CREATE TABLE mkt_points_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    change_type VARCHAR(16) NOT NULL COMMENT 'CONSUME/RECHARGE/EXCHANGE/MANUAL/REFUND',
    change_value INT NOT NULL COMMENT '正=加，负=减',
    points_after INT DEFAULT 0,
    related_id BIGINT,
    related_no VARCHAR(64),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id, deleted),
    INDEX idx_type (change_type),
    INDEX idx_related (related_type, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- 7. 营销看板（汇总快表）
DROP TABLE IF EXISTS mkt_effect_snapshot;
CREATE TABLE mkt_effect_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    promotion_id BIGINT NOT NULL,
    promotion_name VARCHAR(128),
    order_count INT DEFAULT 0,
    sales_total DECIMAL(12,2) DEFAULT 0,
    discount_total DECIMAL(12,2) DEFAULT 0,
    net_revenue DECIMAL(12,2) DEFAULT 0,
    points_given INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_promo (snapshot_date, promotion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销效果快照';

-- 8. RBAC 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:promotion', '营销活动', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 80;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:coupon', '优惠券', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:promotion') t LIMIT 1), 10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:points', '积分规则', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:promotion') t LIMIT 1), 11;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:effect', '营销效果', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:promotion') t LIMIT 1), 12;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('mkt:promotion','mkt:coupon','mkt:points','mkt:effect');

-- 9. 演示数据
INSERT IGNORE INTO mkt_promotion
(tenant_id, store_id, promotion_code, promotion_name, promotion_type, start_time, end_time, threshold_amount, reduce_amount, status, priority, operator_name)
VALUES
(1, 1, 'P-FR-100-10', '满 100 减 10', 'FULL_REDUCE', '2026-01-01 00:00:00', '2099-12-31 23:59:59', 100, 10, 1, 10, 'system'),
(1, 1, 'P-FR-300-30', '满 300 减 30', 'FULL_REDUCE', '2026-01-01 00:00:00', '2099-12-31 23:59:59', 300, 30, 1, 20, 'system'),
(1, 1, 'P-DIS-85', '全场 8.5 折', 'DISCOUNT', '2026-01-01 00:00:00', '2099-12-31 23:59:59', 0, 0, 1, 5, 'system');

INSERT IGNORE INTO mkt_points_rule (tenant_id, rule_name, consume_rate, recharge_multiplier, promotion_multiplier, points_to_cash, max_use_percent, enabled, operator_name)
VALUES (1, '标准积分规则', 1.00, 1.00, 1.00, 0.01, 30.00, 1, 'system');

-- 10. 验证
SELECT 'mkt_promotion' tbl, COUNT(*) cnt FROM mkt_promotion
UNION ALL SELECT 'mkt_coupon_batch', COUNT(*) FROM mkt_coupon_batch
UNION ALL SELECT 'mkt_coupon', COUNT(*) FROM mkt_coupon
UNION ALL SELECT 'mkt_points_rule', COUNT(*) FROM mkt_points_rule;
