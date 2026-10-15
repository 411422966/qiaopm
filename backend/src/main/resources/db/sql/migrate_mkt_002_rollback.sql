-- ============================================================
-- MKT-002-ROLLBACK · 回滚预案 (仅 DBA 手工执行)
--
-- 触发条件:
--   - 生产部署后 90 天内 (2026-12-09 前) 发现 market_activity
--     仍有遗留读取, 或下游系统依赖该表
--
-- 前置条件:
--   - market_activity__archived_20260910 表存在
--   - 即 migrate_mkt_002_drop_market_activity.sql 未被应用, 或
--     应用后归档表尚未被 DBA 清理
--
-- 执行步骤:
--   1. 停服 (避免 DROP/RENAME 时有新连接)
--   2. 执行本脚本
--   3. 验证: SELECT * FROM market_activity LIMIT 10;
--   4. 启服
--
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_mkt_002_rollback;
DELIMITER //
CREATE PROCEDURE migrate_mkt_002_rollback()
BEGIN
    DECLARE v_archived_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_archived_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'market_activity__archived_20260910';

    IF v_archived_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '[MKT-002-ROLLBACK] 归档表不存在, 无法回滚';
    ELSE
        -- 若 market_activity 已存在 (部分迁移场景), 先 DROP
        DROP TABLE IF EXISTS market_activity;

        -- 还原表名
        RENAME TABLE market_activity__archived_20260910 TO market_activity;

        SELECT '[MKT-002-ROLLBACK] market_activity 已恢复' AS info;
    END IF;
END//
DELIMITER ;
CALL migrate_mkt_002_rollback();
DROP PROCEDURE migrate_mkt_002_rollback;
