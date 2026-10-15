-- ===================================================================
-- M01-04 跨店预约 — 预约表加 storeId + 冲突查询加强制校验
-- ===================================================================
--
-- 适用版本：基于 biz_booking（含 tenantId，不含 store_id）
-- 兼容 MySQL 5.7+ / 8.0+
--
-- 变更：
--   1. biz_booking 加 store_id 字段（所属门店，外键 → pos_store.id）
--   2. 冲突检查：员工冲突 / 房间冲突 均以 storeId 为准（跨店不算冲突）
--   3. 不改已有数据：已存在 booking 的 store_id 回填为 1（默认门店）
--
-- 使用：
--   SOURCE /your/sql/path/migrate_m01_04_booking_store_id.sql;
--
-- 回滚：
--   ALTER TABLE biz_booking DROP COLUMN IF EXISTS store_id;
--   -- 危险：已有数据的 store_id 会丢失，请先备份

SET NAMES utf8mb4;

-- -------------------------------------------------------------------
-- 1. 加 store_id 列
-- -------------------------------------------------------------------
ALTER TABLE `biz_booking`
    ADD COLUMN `store_id` BIGINT DEFAULT NULL COMMENT '所属门店ID（挂载的物理门店）' AFTER `tenant_id`;

-- -------------------------------------------------------------------
-- 2. 回填已存在预约的 store_id（取 pos_employee.store_id）
-- -------------------------------------------------------------------
UPDATE `biz_booking` AS b
  LEFT JOIN `pos_employee` AS e ON e.id = b.employee_id
SET b.store_id = IFNULL(e.store_id, 1)
WHERE b.store_id IS NULL
  AND b.deleted = 0;

-- 未找到技师的（employee_id IS NULL）回填默认值 1
UPDATE `biz_booking`
SET store_id = 1
WHERE store_id IS NULL AND deleted = 0;

-- -------------------------------------------------------------------
-- 3. 验证
-- -------------------------------------------------------------------
SELECT '=== M01-04 migration completed ===' AS info;
SELECT 'biz_booking.store_id added' AS step;

SHOW COLUMNS FROM biz_booking LIKE 'store_id';

-- 验证回填
SELECT
    COUNT(*) AS total_bookings,
    SUM(CASE WHEN store_id IS NOT NULL THEN 1 ELSE 0 END) AS with_store_id,
    SUM(CASE WHEN store_id IS NULL THEN 1 ELSE 0 END) AS without_store_id
FROM biz_booking WHERE deleted = 0;

-- 按门店统计
SELECT
    COALESCE(store_id, 0) AS store_id,
    COUNT(*) AS booking_cnt
FROM biz_booking
WHERE deleted = 0
GROUP BY store_id
ORDER BY booking_cnt DESC;
