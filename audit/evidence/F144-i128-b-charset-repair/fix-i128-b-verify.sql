-- I128-b 业务表乱码 · 修复后验证
-- 范围: biz_service_item / biz_service_category / biz_treatment_plan（4 表 5 列 101 行）
-- 排除: sys_permission（51 行命中系 U+00B7 中间点假阳性，非编码问题）
-- 用法: mysql -uroot -p --default-character-set=utf8mb4 < backup/i128-b/fix-i128-b-verify.sql
SET NAMES utf8mb4;
USE meiyetec;

-- ===== A 段：乱码残留计数（全部应为 0） =====
SELECT 'biz_service_item.service_name' AS col, COUNT(*) AS should_be_0 FROM meiyetec.biz_service_item WHERE HEX(service_name) REGEXP 'C[23][89AB][0-9A-F]'
UNION ALL SELECT 'biz_service_item.unit', COUNT(*) FROM meiyetec.biz_service_item WHERE HEX(unit) REGEXP 'C[23][89AB][0-9A-F]'
UNION ALL SELECT 'biz_service_category.category_name', COUNT(*) FROM meiyetec.biz_service_category WHERE HEX(category_name) REGEXP 'C[23][89AB][0-9A-F]'
UNION ALL SELECT 'biz_treatment_plan.plan_name', COUNT(*) FROM meiyetec.biz_treatment_plan WHERE HEX(plan_name) REGEXP 'C[23][89AB][0-9A-F]'
UNION ALL SELECT 'biz_treatment_plan.description', COUNT(*) FROM meiyetec.biz_treatment_plan WHERE HEX(description) REGEXP 'C[23][89AB][0-9A-F]';

-- ===== B 段：字节合法性回读（非法 UTF-8 会报 Truncated incorrect UTF-8） =====
-- biz_service_item.service_name
SELECT id, 'service_name' AS col, CONVERT(HEX(service_name) USING utf8mb4) AS value FROM meiyetec.biz_service_item WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22) ORDER BY id;
-- biz_service_item.unit
SELECT id, 'unit' AS col, CONVERT(HEX(unit) USING utf8mb4) AS value FROM meiyetec.biz_service_item WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22) ORDER BY id;
-- biz_service_category.category_name
SELECT id, 'category_name' AS col, CONVERT(HEX(category_name) USING utf8mb4) AS value FROM meiyetec.biz_service_category WHERE id IN (1, 2, 3, 4, 5) ORDER BY id;
-- biz_treatment_plan.plan_name
SELECT id, 'plan_name' AS col, CONVERT(HEX(plan_name) USING utf8mb4) AS value FROM meiyetec.biz_treatment_plan WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26) ORDER BY id;
-- biz_treatment_plan.description
SELECT id, 'description' AS col, CONVERT(HEX(description) USING utf8mb4) AS value FROM meiyetec.biz_treatment_plan WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26) ORDER BY id;

-- ===== C 段：中文值抽查（应可正常显示中文，无乱码特征） =====
SELECT id, service_code, service_name, unit FROM meiyetec.biz_service_item ORDER BY id;
SELECT id, category_code, category_name FROM meiyetec.biz_service_category ORDER BY id;
SELECT id, plan_code, plan_name, description FROM meiyetec.biz_treatment_plan ORDER BY id;

-- ===== D 段：行数守恒（修复不应增删行） =====
SELECT 'biz_service_item' AS t, COUNT(*) AS n FROM meiyetec.biz_service_item
UNION ALL SELECT 'biz_service_category', COUNT(*) FROM meiyetec.biz_service_category
UNION ALL SELECT 'biz_treatment_plan', COUNT(*) FROM meiyetec.biz_treatment_plan