-- M08-002 技师等级晋升机制 — 为 pos_employee 加 employee_level 字段
-- 启用 LevelEvaluationController 的前提条件

-- 仅 MySQL 8.0.29+ 支持 IF NOT EXISTS
ALTER TABLE pos_employee
  ADD COLUMN employee_level BIGINT DEFAULT NULL COMMENT '技师等级ID（关联 biz_technician_level.id）';

-- 索引：按等级统计 / 大数据量 LIKE 类查询
ALTER TABLE pos_employee
  ADD INDEX idx_employee_level (employee_level);

-- 初始化：所有在职技师的等级置 NULL（未定级 — 走 L1 实习技师 默认）
-- 如已有 L1（初级技师）的可在此处批量 UPDATE
-- UPDATE pos_employee e SET employee_level = (SELECT id FROM biz_technician_level WHERE level_code='L1' LIMIT 1)
-- WHERE e.deleted = 0 AND e.status = 1 AND e.employee_level IS NULL;
