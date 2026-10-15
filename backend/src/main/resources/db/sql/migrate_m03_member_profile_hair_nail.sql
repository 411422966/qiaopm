-- ============================================
-- M03 会员档案 — 扩展美发 / 美甲 子行业画像
-- 来源：PRD 缺口清单「M03 肤质/发质/甲面专属档案」
-- 日期：2026-09-06
-- 目标：在 crm_member_profile 增加 hair_type / nail_type 两列
-- 兼容：MySQL 8.0.29+ 使用 ADD COLUMN IF NOT EXISTS；旧版本先 SHOW COLUMNS 探测
-- ============================================

-- 1. 美容子行业已有 skin_type；本迁移仅追加美发 / 美甲子行业画像

ALTER TABLE crm_member_profile
    ADD COLUMN IF NOT EXISTS hair_type VARCHAR(50) DEFAULT NULL
        COMMENT '发质类型（美发子行业画像）'
        AFTER skin_type;

ALTER TABLE crm_member_profile
    ADD COLUMN IF NOT EXISTS nail_type VARCHAR(50) DEFAULT NULL
        COMMENT '甲面类型（美甲子行业画像）'
        AFTER hair_type;

-- 2. 验证
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
    FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'crm_member_profile'
     AND COLUMN_NAME IN ('skin_type', 'hair_type', 'nail_type')
   ORDER BY ORDINAL_POSITION;
