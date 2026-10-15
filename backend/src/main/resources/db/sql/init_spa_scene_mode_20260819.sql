-- =============================================
-- SPA 场景模式（情侣/闺蜜/单人/家庭）
-- 2026-08-19
-- =============================================

USE meiyetec;

-- ---------------------------------------------
-- 1. 场景模式主表 biz_spa_scene_mode
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS biz_spa_scene_mode (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    store_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '门店ID',
    scene_code    VARCHAR(32)  NOT NULL COMMENT '场景编码 SINGLE/COUPLE/GIRLFRIEND/FAMILY',
    scene_name    VARCHAR(64)  NOT NULL COMMENT '场景名称',
    scene_icon    VARCHAR(128)          COMMENT '场景图标',
    cover_img     VARCHAR(256)          COMMENT '封面图',
    description   VARCHAR(512)          COMMENT '场景描述',
    guest_count   INT          NOT NULL DEFAULT 1 COMMENT '人数',
    duration_min  INT          NOT NULL DEFAULT 60 COMMENT '建议时长(分钟)',
    base_price    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '基础价格',
    tags          VARCHAR(256)          COMMENT '标签(逗号分隔)',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by    BIGINT                COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_store_code (tenant_id, store_id, scene_code, deleted),
    KEY idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPA场景模式主表';

-- ---------------------------------------------
-- 2. 场景-房间组合 biz_spa_scene_room_slot
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS biz_spa_scene_room_slot (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id     BIGINT       NOT NULL DEFAULT 0,
    store_id      BIGINT       NOT NULL DEFAULT 0,
    scene_id      BIGINT       NOT NULL COMMENT '场景模式ID',
    room_id       BIGINT       NOT NULL COMMENT '房间ID',
    room_name     VARCHAR(64)           COMMENT '房间名称快照',
    sort_no       INT          NOT NULL DEFAULT 1 COMMENT '房间序号(1=主房,2=次房…)',
    role_label    VARCHAR(32)           COMMENT '角色标签(主理疗房/次理疗房)',
    required_count INT         NOT NULL DEFAULT 1 COMMENT '所需房间数',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_scene (scene_id, deleted),
    KEY idx_room (room_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPA场景-房间组合';

-- ---------------------------------------------
-- 3. 扩展 biz_booking 加场景字段
-- ---------------------------------------------
ALTER TABLE biz_booking
    ADD COLUMN scene_mode_id   BIGINT          COMMENT 'SPA场景模式ID' AFTER room_id,
    ADD COLUMN scene_mode_code VARCHAR(32)     COMMENT '场景编码' AFTER scene_mode_id,
    ADD COLUMN guest_count     INT    DEFAULT 1 COMMENT '同行人数' AFTER scene_mode_code;

-- ---------------------------------------------
-- 4. 种子 4 个标准场景
-- ---------------------------------------------
INSERT IGNORE INTO biz_spa_scene_mode
    (tenant_id, store_id, scene_code, scene_name, scene_icon, description, guest_count, duration_min, base_price, tags, sort_no, status)
VALUES
    (0, 0, 'SINGLE',     '单人舒压',   '🧘', '适合独自放松，舒适单人房，专业技师 60 分钟芳香理疗', 1, 60, 298.00, '舒压,放松,单人', 1, 1),
    (0, 0, 'COUPLE',     '情侣双享',   '💑', '浪漫双人房，双人并肩同享 90 分钟 SPA，含玫瑰花瓣', 2, 90, 698.00, '情侣,浪漫,双人', 2, 1),
    (0, 0, 'GIRLFRIEND', '闺蜜派对',   '👯', '闺蜜专属双床房 + 美容茶点，120 分钟畅聊 SPA', 2, 120, 598.00, '闺蜜,派对,双床', 3, 1),
    (0, 0, 'FAMILY',     '家庭亲子',   '👨‍👩‍👧', '亲子家庭房，可容纳 2 大 1 小，温和舒压课程', 3, 60, 488.00, '亲子,家庭,温和', 4, 1);

-- ---------------------------------------------
-- 5. 验证
-- ---------------------------------------------
SELECT '=== 场景模式 ===' AS '';
SELECT id, scene_code, scene_name, guest_count, duration_min, base_price, status
FROM biz_spa_scene_mode WHERE deleted=0 ORDER BY sort_no;

SELECT '=== biz_booking 新增字段 ===' AS '';
SHOW COLUMNS FROM biz_booking LIKE 'scene_mode%';
SHOW COLUMNS FROM biz_booking LIKE 'guest_count';