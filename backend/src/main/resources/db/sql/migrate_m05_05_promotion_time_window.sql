-- P0-M05: 限时充值活动（time-bounded wallet promotion）
ALTER TABLE wallet_promotion
    ADD COLUMN IF NOT EXISTS start_time DATETIME NULL COMMENT '限时活动开始时间（NULL=常驻）',
    ADD COLUMN IF NOT EXISTS end_time   DATETIME NULL COMMENT '限时活动结束时间（NULL=常驻）',
    ADD INDEX idx_wallet_promotion_time (start_time, end_time);
