-- ============================================
-- POINTS Feature 修复（幂等版）
-- 2026-08-26
-- ============================================

-- 1. market_point_record
DROP PROCEDURE IF EXISTS migrate_p1;
DELIMITER //
CREATE PROCEDURE migrate_p1()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'market_point_record'
                   AND column_name = 'tenant_id') THEN
        ALTER TABLE market_point_record
            ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER store_id,
            ADD INDEX idx_mpr_tenant_id (tenant_id);
    END IF;

    -- 2. market_point_rule（如果表存在）
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'market_point_rule') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = 'market_point_rule'
                       AND column_name = 'tenant_id') THEN
            ALTER TABLE market_point_rule
                ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER store_id,
                ADD INDEX idx_mprr_tenant_id (tenant_id);
        END IF;
    END IF;

    -- 3. point_goods
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'point_goods') THEN
        CREATE TABLE point_goods (
            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
            tenant_id BIGINT DEFAULT 1,
            store_id BIGINT DEFAULT 1,
            name VARCHAR(128) NOT NULL,
            category VARCHAR(32) DEFAULT 'GENERAL',
            image_url VARCHAR(255),
            points_price INT NOT NULL,
            cash_price DECIMAL(12,2) DEFAULT 0.00,
            stock INT DEFAULT 0,
            sold_count INT DEFAULT 0,
            description TEXT,
            status TINYINT DEFAULT 1,
            operator_id BIGINT,
            operator_name VARCHAR(64),
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted TINYINT DEFAULT 0,
            INDEX idx_pg_tenant_id (tenant_id),
            INDEX idx_pg_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
    END IF;

    -- 4. mkt_points_log
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'mkt_points_log') THEN
        CREATE TABLE mkt_points_log (
            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
            tenant_id BIGINT DEFAULT 1,
            member_id BIGINT NOT NULL,
            member_name VARCHAR(64),
            change_type VARCHAR(32) NOT NULL,
            change_value INT NOT NULL,
            points_after INT NOT NULL,
            related_id BIGINT,
            related_no VARCHAR(64),
            related_type VARCHAR(32),
            operator_id BIGINT,
            operator_name VARCHAR(64),
            remark VARCHAR(255),
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            deleted TINYINT DEFAULT 0,
            INDEX idx_mpl_tenant_id (tenant_id),
            INDEX idx_mpl_member_id (member_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
    END IF;

    -- 5. point_order
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'point_order') THEN
        CREATE TABLE point_order (
            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
            tenant_id BIGINT DEFAULT 1,
            store_id BIGINT DEFAULT 1,
            order_no VARCHAR(64) NOT NULL,
            member_id BIGINT NOT NULL,
            member_name VARCHAR(64),
            goods_id BIGINT NOT NULL,
            goods_name VARCHAR(128),
            points_cost INT NOT NULL,
            cash_paid DECIMAL(12,2) DEFAULT 0.00,
            status VARCHAR(16) DEFAULT 'PENDING',
            delivery_info VARCHAR(500),
            operator_id BIGINT,
            operator_name VARCHAR(64),
            remark VARCHAR(255),
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted TINYINT DEFAULT 0,
            INDEX idx_po_tenant_id (tenant_id),
            INDEX idx_po_member_id (member_id),
            INDEX idx_po_order_no (order_no)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
    END IF;

END//
DELIMITER ;
CALL migrate_p1();
DROP PROCEDURE migrate_p1;

-- 6. 回填 tenant_id
UPDATE market_point_record r
    INNER JOIN crm_member m ON r.member_id = m.id
SET r.tenant_id = m.tenant_id
WHERE r.tenant_id IS NULL;