-- ============================================================
-- 营销活动（新字段展开式） mkt_promotion 表 (P2 验证补漏 / 2026-08-27)
-- 说明:
--   - 与实体 com.meiyetec.entity.MktPromotion (@TableName("mkt_promotion")) 对齐
--   - 这是 P2 验证发现的"链路断裂"项: 实体/Mapper/Controller(MktPromotionController)
--     全在, 但 backend/ 下完全没有 CREATE TABLE mkt_promotion 的 DDL。
--   - 与已有但字段完全不同的老表 market_activity (在 backend/sql/m10_market.sql)
--     是两套数据模型 (MktPromotionController 源码 comment 已说明):
--       * market_activity (老): 9 列 + rule_config JSON 配置式, 用于早期简易活动
--       * mkt_promotion  (新): 30+ 列展开式, 支持满减/折扣/赠品/代金券/积分兑换等
--         复杂规则, 是 MktPromotionController 的实际落点。
--   - 当前 SQL 只补 mkt_promotion, market_activity 保留不冲突 (表名不同)。
-- 关联表:
--   - mkt_promotion.store_id      -> store.id
--   - mkt_promotion.member_level_ids -> 逗号分隔的 member_level.id 列表 (弱关联)
--   - mkt_promotion.exclusive_codes  -> 互斥的 promotion_code 列表 (自引用字符串)
--   - mkt_promotion.scope_ids    -> 根据 scopeType 含义不同 (服务/商品/门店)
-- ============================================================

CREATE TABLE IF NOT EXISTS mkt_promotion (
  id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id           BIGINT        NULL     COMMENT '所属租户ID',
  store_id            BIGINT        NULL     COMMENT '所属门店ID (NULL 表示全门店)',
  promotion_code      VARCHAR(64)   NULL     COMMENT '活动编码 (业务唯一)',
  promotion_name      VARCHAR(128)  NULL     COMMENT '活动名称',
  promotion_type      VARCHAR(32)   NULL     COMMENT 'FULL_REDUCE/DISCOUNT/GIFT/CASH_COUPON/POINTS_EXCHANGE',
  description         VARCHAR(512)  NULL     COMMENT '活动说明',
  start_time          DATETIME      NULL     COMMENT '开始时间',
  end_time            DATETIME      NULL     COMMENT '结束时间',
  threshold_amount    DECIMAL(18,2) NULL     COMMENT '满减门槛金额 (FULL_REDUCE 适用)',
  reduce_amount       DECIMAL(18,2) NULL     COMMENT '减免金额 (FULL_REDUCE/CASH_COUPON 适用)',
  discount_rate       DECIMAL(5,2)  NULL     COMMENT '折扣率 0.00-1.00 (DISCOUNT 适用)',
  scope_type          VARCHAR(32)   NULL     COMMENT '适用范围类型 ALL/SERVICE/PRODUCT/CATEGORY',
  scope_ids           VARCHAR(1024) NULL     COMMENT '适用范围 ID 列表 (逗号分隔)',
  member_level_ids    VARCHAR(255)  NULL     COMMENT '适用会员等级 ID 列表 (逗号分隔)',
  stackable           INT           NULL     DEFAULT 0 COMMENT '是否可叠加 0否 1是',
  exclusive_codes     VARCHAR(512)  NULL     COMMENT '互斥活动编码 (逗号分隔)',
  quota_total         INT           NULL     DEFAULT 0 COMMENT '总名额 (0=不限)',
  quota_used          INT           NULL     DEFAULT 0 COMMENT '已使用名额',
  per_member_limit    INT           NULL     DEFAULT 0 COMMENT '每人限领 (0=不限)',
  status              INT           NULL     DEFAULT 1 COMMENT '0=草稿 1=启用 2=停用 3=已结束',
  priority            INT           NULL     DEFAULT 0 COMMENT '优先级 (高者优先)',
  operator_id         BIGINT        NULL     COMMENT '创建人ID',
  operator_name       VARCHAR(64)   NULL     COMMENT '创建人姓名',
  create_time         DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             INT           NULL     DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_mktp_tenant (tenant_id),
  KEY idx_mktp_store (store_id),
  KEY idx_mktp_code (promotion_code),
  KEY idx_mktp_type (promotion_type),
  KEY idx_mktp_status (status),
  KEY idx_mktp_time (start_time, end_time),
  KEY idx_mktp_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='营销活动（新字段展开式，与 MarketActivity 老表并存）';
