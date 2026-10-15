-- ============================================================
-- P0-T02 · 新建 sys_config 表 + 初始化配置数据
-- ============================================================
-- 背景：
--   LogRetentionTask 每小时/每天触发，调用
--   SysConfigServiceImpl.getInt("log.retention-days")
--   但 sys_config 表从未被任何 init SQL 创建 → BadSqlGrammarException
--   "Table 'meiyetec.sys_config' doesn't exist"
--
-- 解决：
--   1. 按 SysConfig 实体（@TableName("sys_config")）建表
--      字段必须与 MyBatis-Plus selectList(*) 输出一致
--      → id, config_key, config_value, config_group,
--        description, sort_order, created_at, updated_at
--   2. 插入两条系统级默认值：
--        log.retention-days       = 90
--        idempotency.retention-hours = 24
--
-- 范围：仅 sys_config 表，其它表一律不碰
--
-- 安全：
--   - CREATE TABLE IF NOT EXISTS 幂等
--   - INSERT IGNORE 幂等
--   - 末尾含 SELECT 验证
-- ============================================================

USE meiyetec;

-- ============================================================
-- 1. 建表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key    VARCHAR(64)  NOT NULL                COMMENT '配置 key，唯一',
    config_value  VARCHAR(512) NULL                    COMMENT '配置 value（字符串存储，数字由 Service 端解析）',
    config_group  VARCHAR(64)  NULL     DEFAULT 'system' COMMENT '分组',
    description   VARCHAR(255) NULL                    COMMENT '说明',
    sort_order    INT          NULL     DEFAULT 0      COMMENT '排序',
    created_at    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP        COMMENT '创建时间',
    updated_at    DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表 (P0-T02 新建)';

-- ============================================================
-- 2. 初始化数据（INSERT IGNORE 保证幂等）
-- ============================================================
INSERT IGNORE INTO sys_config (config_key, config_value, config_group, description, sort_order)
VALUES
    ('log.retention-days',          '90', 'system', '日志保留天数（LogRetentionTask 使用，1-3650）', 10),
    ('idempotency.retention-hours', '24', 'system', '幂等键保留小时数',                              20);

-- ============================================================
-- 3. 验证
-- ============================================================
SELECT '=== AFTER T02: sys_config ===' AS step;
SELECT COUNT(*) AS row_count FROM sys_config;
SELECT id, config_key, config_value, config_group, description, sort_order
  FROM sys_config
 ORDER BY sort_order;
