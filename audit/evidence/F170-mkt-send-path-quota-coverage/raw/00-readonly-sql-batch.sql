-- F170 · batch2-C 渠道发送路径配额覆盖 · 只读查询合集
-- 非正式工具：仅用于证据复现，不进入 audit/scripts/ 正式守门序列，不接入 CI。
-- 全部语句均为 SELECT / SHOW，只读；不含任何 INSERT / UPDATE / DELETE / DDL。
-- 执行方式（仓库根目录）：
--   cmd /c 'docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec --default-character-set=utf8mb4 --table < audit\evidence\F170-mkt-send-path-quota-coverage\raw\00-readonly-sql-batch.sql'
-- 输出按段拆分为 01 / 02 / 03 / 04 四个文件（同一 SQL 的不同分段单独执行留存）。

-- ===== 段 1：渠道配额配置现状 + 触达明细状态分布 =====
SELECT '--- mkt_channel_config 全部行 ---' AS section;
SELECT id, tenant_id, brand_id, channel_type, rate_limit_per_min, rate_limit_per_day, status, deleted
FROM mkt_channel_config ORDER BY tenant_id, channel_type, id;

SELECT '--- marketing_touch_log 状态分布 ---' AS section;
SELECT tenant_id, channel_type, status, COUNT(*) AS cnt,
       SUM(sent_at IS NULL) AS sent_at_null,
       SUM(retry_count) AS retry_sum
FROM marketing_touch_log
WHERE deleted = 0
GROUP BY tenant_id, channel_type, status
ORDER BY tenant_id, channel_type, status;

SELECT '--- 失败行(不计配额口径) 汇总 ---' AS section;
SELECT tenant_id, channel_type,
       COUNT(*) AS fail_rows,
       SUM(retry_count) AS retry_total,
       MAX(retry_count) AS retry_max
FROM marketing_touch_log
WHERE deleted = 0 AND status = 3
GROUP BY tenant_id, channel_type;

SELECT '--- 成功行按日 ---' AS section;
SELECT DATE(sent_at) AS d, tenant_id, channel_type, COUNT(*) AS ok_rows
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL
GROUP BY DATE(sent_at), tenant_id, channel_type
ORDER BY d DESC LIMIT 20;

SELECT '--- member_message 行数(站内信落地) ---' AS section;
SELECT COUNT(*) AS member_message_rows FROM member_message;

SELECT '--- overseas_booking 行数(EXT-19 预约) ---' AS section;
SELECT COUNT(*) AS overseas_booking_rows FROM overseas_booking;

-- ===== 段 2：发送速率 vs 配置额度（验证配额是否真的拦过） =====
SELECT '--- SMS 成功发送按分钟峰值(配置 per_min=3) ---' AS section;
SELECT MINUTE(sent_at) AS m, COUNT(*) AS cnt
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL AND channel_type = 'SMS'
GROUP BY HOUR(sent_at), MINUTE(sent_at)
ORDER BY cnt DESC LIMIT 10;

SELECT '--- 按小时峰值(配置 per_day=5) ---' AS section;
SELECT HOUR(sent_at) AS h, COUNT(*) AS cnt
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL AND channel_type = 'SMS'
GROUP BY HOUR(sent_at) ORDER BY cnt DESC LIMIT 10;

SELECT '--- 触达任务品牌分布 ---' AS section;
SELECT tenant_id, brand_id, channel_type, status, COUNT(*) AS tasks
FROM marketing_touch_task
WHERE deleted = 0
GROUP BY tenant_id, brand_id, channel_type, status
ORDER BY tenant_id, brand_id, channel_type;

SELECT '--- SMS 成功行对应任务品牌(关联口径复核) ---' AS section;
SELECT tt.brand_id, COUNT(*) AS sent_rows
FROM marketing_touch_log t
JOIN marketing_touch_task tt ON tt.id = t.task_id AND tt.deleted = 0
WHERE t.deleted = 0 AND t.sent_at IS NOT NULL AND t.channel_type = 'SMS'
GROUP BY tt.brand_id;

SELECT '--- kiosk_device 心跳现状 ---' AS section;
SELECT tenant_id, status, COUNT(*) AS devices,
       SUM(last_heartbeat IS NULL) AS never_heartbeat
FROM kiosk_device
GROUP BY tenant_id, status;

SELECT '--- crm_member 品牌/租户列(生日路径上下文) ---' AS section;
SHOW COLUMNS FROM crm_member LIKE 'brand%';
SHOW COLUMNS FROM crm_member LIKE 'tenant%';

-- ===== 段 3：各发送路径的品牌上下文可获得性 =====
SELECT '--- overseas_booking 结构(预约确认路径) ---' AS section;
SHOW COLUMNS FROM overseas_booking;

SELECT '--- birthday_member 规模 ---' AS section;
SELECT tenant_id, auto_send, COUNT(*) AS cnt
FROM birthday_member
GROUP BY tenant_id, auto_send;

SELECT '--- pos_store 结构(告警路径 store→brand) ---' AS section;
SHOW COLUMNS FROM pos_store;

SELECT '--- kiosk_device store_id 分布 ---' AS section;
SELECT tenant_id, store_id, COUNT(*) AS devices FROM kiosk_device GROUP BY tenant_id, store_id;

SELECT '--- IN_APP 有效配置 configJson(告警接收人来源) ---' AS section;
SELECT id, channel_type, brand_id, LEFT(config_json, 300) AS cfg
FROM mkt_channel_config WHERE deleted = 0 AND channel_type = 'IN_APP';

SELECT '--- 触达日志按 task 聚合(单任务发送量) ---' AS section;
SELECT task_id, COUNT(*) AS rows_cnt, SUM(sent_at IS NOT NULL) AS sent_cnt
FROM marketing_touch_log
WHERE deleted = 0
GROUP BY task_id ORDER BY rows_cnt DESC LIMIT 10;

SELECT '--- marketing_touch_task 品牌列 ---' AS section;
SHOW COLUMNS FROM marketing_touch_task LIKE 'brand%';
