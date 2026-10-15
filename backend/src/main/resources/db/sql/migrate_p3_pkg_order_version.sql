USE meiyetec;

-- =============================================
-- 修复 crm_package_order 缺少 version 列（PACKAGE_REMAINING_TIMES_CAS 乐观锁）
-- =============================================
ALTER TABLE crm_package_order
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER update_time;

-- 验证
SELECT '=== crm_package_order version 列已添加 ===' AS '';
SHOW COLUMNS FROM crm_package_order LIKE 'version';