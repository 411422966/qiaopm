-- ============================================
-- R3.0-B：跨店会员与核销
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 跨店共享规则（按品牌或全局）
-- ============================================
DROP TABLE IF EXISTS crm_cross_store_rule;
CREATE TABLE crm_cross_store_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT COMMENT '品牌（NULL=租户级通用规则）',
    rule_name VARCHAR(64) NOT NULL,
    -- 会员
    member_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店识别（手机号匹配）',
    -- 套餐/项目核销
    package_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店核销套餐',
    package_cross_mode VARCHAR(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌内 / CHAIN链内 / ANY任意门店 / DISABLED禁止',
    -- 积分
    points_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店积分共享',
    -- 储值
    balance_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店储值共享',
    balance_cross_mode VARCHAR(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌内 / STORE仅本门店 / DISABLED禁止',
    -- 折扣
    discount_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店折扣共享',
    -- 优惠券
    coupon_cross_enabled TINYINT DEFAULT 1 COMMENT '跨店优惠券',
    -- 跨店手续费
    cross_fee_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '跨店手续费比例% 0=不收',
    cross_fee_type VARCHAR(16) DEFAULT 'NONE' COMMENT 'NONE不收/PERCENT按比例/FIXED固定',
    -- 限额
    cross_max_amount DECIMAL(12,2) DEFAULT 0 COMMENT '跨店单笔限额 0=不限',
    -- 状态
    enabled TINYINT DEFAULT 1,
    priority INT DEFAULT 0,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant_brand (tenant_id, brand_id, enabled, deleted),
    INDEX idx_priority (priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨店共享规则';

-- ============================================
-- 2. 跨店核销记录
-- ============================================
DROP TABLE IF EXISTS crm_cross_store_redemption;
CREATE TABLE crm_cross_store_redemption (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64),
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    member_phone VARCHAR(20),
    -- 跨店信息
    source_store_id BIGINT NOT NULL COMMENT '会员归属门店',
    target_store_id BIGINT NOT NULL COMMENT '实际核销门店',
    -- 类型
    redemption_type VARCHAR(16) NOT NULL COMMENT 'PACKAGE套餐/PROJECT项目/BALANCE储值/POINTS积分/COUPON券/DISCOUNT折扣',
    source_id BIGINT COMMENT '对应的套餐/项目ID',
    source_name VARCHAR(128),
    -- 金额
    original_amount DECIMAL(12,2) DEFAULT 0 COMMENT '原消费金额',
    cross_fee DECIMAL(12,2) DEFAULT 0 COMMENT '跨店手续费',
    net_amount DECIMAL(12,2) DEFAULT 0 COMMENT '实际入账金额',
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1已核销 2已撤销',
    -- 规则命中
    rule_id BIGINT COMMENT '命中的跨店规则ID',
    rule_snapshot VARCHAR(500) COMMENT '规则快照',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order (order_id),
    INDEX idx_member (member_id),
    INDEX idx_target_store (target_store_id, status, deleted),
    INDEX idx_source_store (source_store_id, status, deleted),
    INDEX idx_type (redemption_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨店核销记录';

-- ============================================
-- 3. 会员绑定多门店记录（一个会员可关联多个门店）
-- ============================================
DROP TABLE IF EXISTS crm_member_store;
CREATE TABLE crm_member_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    brand_id BIGINT,
    relation_type VARCHAR(16) DEFAULT 'CROSS' COMMENT 'ORIGIN归属门店/CROSS跨店关联',
    bind_type VARCHAR(16) DEFAULT 'PHONE' COMMENT 'PHONE手机号/CARD会员卡/MANUAL人工',
    first_active_at DATETIME,
    last_active_at DATETIME,
    visit_count INT DEFAULT 0,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1活跃 0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_member_store (member_id, store_id),
    INDEX idx_store (store_id, status),
    INDEX idx_member (member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员-门店关联';

-- ============================================
-- 4. 演示规则
-- ============================================
INSERT IGNORE INTO crm_cross_store_rule
(tenant_id, brand_id, rule_name, member_cross_enabled, package_cross_enabled, package_cross_mode,
 points_cross_enabled, balance_cross_enabled, balance_cross_mode,
 discount_cross_enabled, coupon_cross_enabled,
 cross_fee_rate, cross_fee_type, cross_max_amount, enabled, priority, operator_name)
VALUES
(1, 1, '美业品牌默认跨店规则', 1, 1, 'BRAND', 1, 1, 'BRAND', 1, 1, 0.00, 'NONE', 0, 1, 100, 'system'),
(1, NULL, '租户级通用规则（禁止跨店）', 1, 0, 'DISABLED', 1, 0, 'STORE', 1, 0, 0.00, 'NONE', 0, 1, 1, 'system');

-- ============================================
-- 5. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:cross-rule', '跨店规则', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 30;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:cross-record', '跨店核销', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 31;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('crm:cross-rule','crm:cross-record');

-- ============================================
-- 6. 验证
-- ============================================
SELECT 'crm_cross_store_rule' tbl, COUNT(*) cnt FROM crm_cross_store_rule WHERE deleted=0
UNION ALL SELECT 'crm_cross_store_redemption', COUNT(*) FROM crm_cross_store_redemption WHERE deleted=0
UNION ALL SELECT 'crm_member_store', COUNT(*) FROM crm_member_store WHERE deleted=0;
