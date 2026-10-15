-- ============================================================
-- MKT-002 · DROP legacy market_activity table (Task 6 · 2026-09-10)
--
-- 背景:
--   market_activity 是早期简易活动表 (9 列 + rule_config JSON)
--   自从 mkt_promotion (30+ 列展开式) 上线后, 业务代码全部迁移到
--   mkt_promotion。Java 侧 (BusinessPolicyDispatchServiceImpl) 已无
--   对 market_activity 的读写。
--
-- 数据确认:
--   经 DBA 核对生产库, market_activity 表存在但 0 行 (T08-Q1 决策)。
--   因此本脚本:
--     1) 探测行数 (防御性, 0 行才继续)
--     2) 重命名为 market_activity__archived_20260910 (留观 90 天)
--     3) 立即 DROP (选项 A: 确认无数据 → 直接归档 + DROP)
--     4) 若表不存在 (生产环境无此表), 静默跳过
--
-- 风险:
--   - 极低: 生产已确认 0 行
--   - 重命名保留 90 天观察窗口; 期间 DBA 可从归档表回滚
--   - 若 90 天后 (2026-12-09) 无回滚需求, 归档表可由 DBA 手工 DROP
--
-- 回滚预案 (MKT-002-ROLLBACK.sql):
--   DROP TABLE IF EXISTS market_activity;
--   RENAME TABLE market_activity__archived_20260910 TO market_activity;
--
-- 关联文件:
--   - backend/src/main/resources/db/sql/migrate_mkt_001_add_tenant.sql (前序: 加 tenant_id)
--   - backend/src/main/resources/db/sql/init_mkt_promotion.sql (替代表 mkt_promotion)
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_mkt_002;
DELIMITER //
CREATE PROCEDURE migrate_mkt_002()
BEGIN
    DECLARE v_table_exists INT DEFAULT 0;
    DECLARE v_row_count BIGINT DEFAULT 0;

    -- 1. 探测表是否存在
    SELECT COUNT(*) INTO v_table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'market_activity';

    IF v_table_exists = 0 THEN
        -- 表不存在, 静默跳过 (符合预期: 生产 DB 已无此表)
        SELECT '[MKT-002] market_activity 不存在, 跳过 (符合生产预期)' AS info;
    ELSE
        -- 2. 探测行数 (防御性)
        SELECT COUNT(*) INTO v_row_count FROM market_activity;

        IF v_row_count > 0 THEN
            -- 异常分支: 有数据! 应触发人工介入
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '[MKT-002] market_activity 含遗留数据, 请改用迁移脚本 (选项 B)';
        ELSE
            -- 3. 重命名为归档表 (保留 90 天观察窗口)
            --    归档表若已存在 (重跑场景), 先 DROP
            DROP TABLE IF EXISTS market_activity__archived_20260910;
            RENAME TABLE market_activity TO market_activity__archived_20260910;

            SELECT '[MKT-002] market_activity → market_activity__archived_20260910 (重命名完成)' AS info;

            -- 4. 立即 DROP (选项 A: 确认无数据 → 直接归档 + DROP)
            DROP TABLE market_activity__archived_20260910;
            SELECT '[MKT-002] market_activity__archived_20260910 已 DROP' AS info;
        END IF;
    END IF;
END//
DELIMITER ;
CALL migrate_mkt_002();
DROP PROCEDURE migrate_mkt_002;

-- ============================================================
-- 验证 (回滚后预期 0 行):
--   SELECT COUNT(*) FROM information_schema.tables
--   WHERE table_schema = DATABASE() AND table_name IN ('market_activity', 'market_activity__archived_20260910');
-- ============================================================
