-- ============================================
-- WALLET Feature 修复
-- 创建 wallet_promotion 表（充值优惠规则表）
-- 2026-08-25
-- ============================================

CREATE TABLE IF NOT EXISTS wallet_promotion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID，NULL 表示全局规则',
    recharge_amount DECIMAL(12,2) NOT NULL COMMENT '充值门槛金额',
    gift_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '赠送金额',
    rule_name VARCHAR(64) DEFAULT NULL COMMENT '规则名称，如"充100送10"',
    sort_order INT DEFAULT 0 COMMENT '排序（升序）',
    status TINYINT DEFAULT 1 COMMENT '状态：0=禁用 1=启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status),
    INDEX idx_recharge (recharge_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值优惠规则表';

-- 默认种子数据（4 条全局规则，与原硬编码保持一致）
INSERT INTO wallet_promotion (tenant_id, recharge_amount, gift_amount, rule_name, sort_order, status)
VALUES
    (NULL, 100.00,  10.00, '充100送10',  1, 1),
    (NULL, 500.00,  60.00, '充500送60',  2, 1),
    (NULL, 1000.00, 150.00, '充1000送150', 3, 1),
    (NULL, 2000.00, 350.00, '充2000送350', 4, 1)
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);
