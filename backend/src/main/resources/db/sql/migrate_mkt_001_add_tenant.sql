-- ============================================
-- MKT Feature 修复（幂等版）
-- 2026-08-26
-- ============================================

DROP PROCEDURE IF EXISTS migrate_mkt_001;
DELIMITER //
CREATE PROCEDURE migrate_mkt_001()
BEGIN
    -- 1. market_activity 加 tenant_id
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'market_activity') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'market_activity' AND column_name = 'tenant_id') THEN
            ALTER TABLE market_activity
                ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER store_id,
                ADD INDEX idx_ma_tenant_id (tenant_id);
        END IF;
    END IF;
END//
DELIMITER ;
CALL migrate_mkt_001();
DROP PROCEDURE migrate_mkt_001;