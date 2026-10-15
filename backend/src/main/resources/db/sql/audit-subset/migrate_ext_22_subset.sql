-- ============================================================
-- EXT-22 子集：MySQL 8.0 兼容版
-- 来源：migrate_ext_22_google_maps_place_details.sql
-- 适配说明：
--   原脚本使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS
--   该语法仅 MariaDB 支持，MySQL 8.0 不支持（实测 MySQL 8.0.46 报 1064）
--   本子集已删除 IF NOT EXISTS，前置 SHOW COLUMNS 已确认 4 列均不存在
-- 对应功能：/place/details 接口 + 批量 geocode + 有赞 webhook 签名
-- ============================================================

ALTER TABLE google_maps_config
  ADD COLUMN enable_place_details TINYINT DEFAULT 0 COMMENT '1=enable Place Details billed interface, default 0';

ALTER TABLE ecommerce_platform
  ADD COLUMN app_id       VARCHAR(64)  DEFAULT NULL COMMENT 'Youzan AppID (distinct from appKey)' AFTER app_secret,
  ADD COLUMN kdt_id       VARCHAR(64)  DEFAULT NULL COMMENT 'Youzan shop ID for webhook URL' AFTER app_id,
  ADD COLUMN sign_secret  VARCHAR(255) DEFAULT NULL COMMENT 'webhook signing secret for X-Youzan-Sign' AFTER kdt_id;
