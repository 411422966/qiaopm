-- ============================================
-- EXT-17：Google Maps 真实集成
--  1. google_maps_config — 配置表（参考 mkt_channel_config 结构）
--  2. google_maps_geocode_log — 地理编码日志（用于计费审计）
-- 2026-09-05
-- ============================================

-- 1. 配置表
DROP TABLE IF EXISTS google_maps_config;
CREATE TABLE google_maps_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    -- API 凭据
    api_key VARCHAR(255) NOT NULL COMMENT 'Google Maps API Key',
    -- 区域 / 语言
    default_language VARCHAR(8) DEFAULT 'zh-CN' COMMENT '默认语言（zh-CN / en）',
    default_region VARCHAR(8) DEFAULT 'cn' COMMENT '默认区域（cn / us / ...）',
    -- 限流
    quota_daily INT DEFAULT 10000 COMMENT '每日配额（参考 GCP 限额）',
    rate_limit_per_min INT DEFAULT 60,
    -- 启用
    enabled TINYINT DEFAULT 1,
    -- 健康检查
    last_health_at DATETIME,
    last_health_msg VARCHAR(255),
    -- 其它配置（JSON：地图样式 / 默认缩放 / 自定义区域）
    config_json TEXT,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_brand (tenant_id, brand_id, deleted),
    INDEX idx_tenant (tenant_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Google Maps 配置';

-- 2. 地理编码日志（每日计费 + 命中率分析）
DROP TABLE IF EXISTS google_maps_geocode_log;
CREATE TABLE google_maps_geocode_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    address VARCHAR(500) NOT NULL COMMENT '请求地址',
    formatted_address VARCHAR(500) COMMENT '规范化地址',
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    place_id VARCHAR(128),
    status VARCHAR(32) COMMENT 'OK / ZERO_RESULTS / OVER_QUERY_LIMIT / ...',
    error_message VARCHAR(255),
    duration_ms INT,
    request_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_request (tenant_id, request_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Google Maps 地理编码日志';

-- 3. 验证
SELECT
    (SELECT COUNT(*) FROM google_maps_config)        AS cfg_count,
    (SELECT COUNT(*) FROM google_maps_geocode_log)   AS log_count;
