-- ============================================================================
-- P0-T12 · DCEP pay_dcep_channel env_mode 物理约束迁移
--
-- 目的：
--   - pay_dcep_channel.env_mode 限定为合法集合 MOCK/SANDBOX/PROD
--   - 演示（demo）通道与生产（prod）通道在数据层清晰隔离
--   - 禁止历史 dev 字符串混入
--
-- 背景：
--   当前 pay_dcep_channel.env_mode 是 varchar(16) DEFAULT 'SANDBOX'，无 CHECK 约束。
--   历史数据库中存在 8 行 env_mode='dev' 的脏数据（UT5-CH-* 测试残留）。
--
-- 执行策略（两步走，遵循 P0-T12 实施规则：禁止破坏历史数据、禁止 DELETE）：
--   Step 1: UPDATE 把所有 env_mode 不在 ('MOCK','SANDBOX','PROD') 集合的行修复为 'MOCK'
--           （dev 模式通道本质是 demo 通道，语义上等价于 MOCK；这样不删除任何行）
--   Step 2: ALTER TABLE 加 CHECK 约束
--
-- 回滚策略：
--   ALTER TABLE pay_dcep_channel DROP CONSTRAINT ck_pay_dcep_channel_env_mode;
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 1：清理历史脏数据（值修复，非删除）
-- ----------------------------------------------------------------------------
UPDATE pay_dcep_channel
SET env_mode = 'MOCK'
WHERE env_mode NOT IN ('MOCK', 'SANDBOX', 'PROD');

-- 记录受影响的行数（手动核查）：
-- SELECT channel_code, env_mode FROM pay_dcep_channel WHERE env_mode = 'MOCK' AND channel_code LIKE 'UT5-%';

-- ----------------------------------------------------------------------------
-- Step 2：加 CHECK 约束（MySQL 8.0.46 语法：不支持 DROP CONSTRAINT IF EXISTS；
--      若约束已存在，先用 DROP CHECK 移除；新约束不存在则跳过）
-- ----------------------------------------------------------------------------
ALTER TABLE pay_dcep_channel
DROP CHECK IF EXISTS ck_pay_dcep_channel_env_mode;  -- MySQL 8.0.29+ 支持 IF EXISTS

ALTER TABLE pay_dcep_channel
ADD CONSTRAINT ck_pay_dcep_channel_env_mode
CHECK (env_mode IN ('MOCK', 'SANDBOX', 'PROD'));

-- ----------------------------------------------------------------------------
-- 验证（手动执行）：
-- SHOW CREATE TABLE pay_dcep_channel\G
-- 应在 CONSTRAINT 部分看到 ck_pay_dcep_channel_env_mode
-- ----------------------------------------------------------------------------