SELECT COUNT(*) AS row_count FROM mkt_channel_config;
SELECT id, tenant_id, channel_type, channel_name, brand_id, rate_limit_per_min, rate_limit_per_day, retry_max, status, last_health_at, last_health_msg FROM mkt_channel_config ORDER BY id;
