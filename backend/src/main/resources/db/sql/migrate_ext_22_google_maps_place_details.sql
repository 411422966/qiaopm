-- ============================================================
-- EXT-22：Google Maps — Place Details 计费开关 + 电商平台签名字段
-- 对应功能：/place/details 接口 + 批量 geocode + 有赞 webhook 签名
-- ============================================================

ALTER TABLE google_maps_config
  ADD COLUMN IF NOT EXISTS enable_place_details TINYINT DEFAULT 0 COMMENT '1=启用 Place Details（计费接口），默认 0';

ALTER TABLE ecommerce_platform
  ADD COLUMN IF NOT EXISTS app_id       VARCHAR(64)  DEFAULT NULL COMMENT '开放平台 AppID（有赞：区别于 appKey）' AFTER app_secret,
  ADD COLUMN IF NOT EXISTS kdt_id       VARCHAR(64)  DEFAULT NULL COMMENT '店铺 ID（有赞 kdtId，签名 / 回调 URL 用）' AFTER app_id,
  ADD COLUMN IF NOT EXISTS sign_secret  VARCHAR(255) DEFAULT NULL COMMENT '消息签名密钥（webhook 验签 X-Youzan-Sign）' AFTER kdt_id;

-- 兼容旧 MySQL（< 8.0.29 不支持 IF NOT EXISTS）：ALTER 前先 SHOW COLUMNS 检查
