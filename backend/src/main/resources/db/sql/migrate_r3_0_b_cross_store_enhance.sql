-- Cross store rule enhancement fields
-- 2026-08-22

ALTER TABLE crm_cross_store_rule
    ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT 'Rule remark' AFTER rule_name;

ALTER TABLE crm_cross_store_rule
    ADD COLUMN start_time DATETIME DEFAULT NULL COMMENT 'Start time' AFTER remark,
    ADD COLUMN end_time DATETIME DEFAULT NULL COMMENT 'End time' AFTER start_time;

ALTER TABLE crm_cross_store_rule
    ADD COLUMN hit_count BIGINT DEFAULT 0 COMMENT 'Hit count' AFTER priority;

SELECT id, rule_name, remark, start_time, end_time, priority, hit_count FROM crm_cross_store_rule LIMIT 5;
