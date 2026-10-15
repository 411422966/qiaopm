-- ============================================================
-- P2-16 BNPL 先买后付订单表 (P2 验证补漏 / 2026-08-27)
-- 说明:
--   - 与实体 com.meiyetec.entity.BnplOrder (@TableName("biz_bnpl_order")) 对齐
--   - 这是 P2 验证发现的"链路断裂"项: 实体/Mapper/Controller(BnplController)
--     全在, 但 backend/ 下完全没有 CREATE TABLE biz_bnpl_order 的 DDL。
--   - 一条记录 = 一个会员在某 BNPL 平台 (atome/klarna/afterpay/affirm 等)
--     下的一次分期订单。
--   - 状态机 (status):
--       1=待确认 2=已确认(待发货) 3=已发货 4=已收货
--       5=已完成 6=已取消 7=退款中 8=已退款
--   - repayment_schedule 用 JSON 存分期明细 (每期金额、到期日、状态等),
--     与实体 String 字段一致; 业务侧自行序列化/反序列化。
-- 关联表:
--   - biz_bnpl_order.member_id -> member.id
--   - biz_bnpl_order.store_id  -> store.id
--   - biz_bnpl_order.biz_order_no -> pos_order.order_no (跨模块, 弱关联)
-- ============================================================

CREATE TABLE IF NOT EXISTS biz_bnpl_order (
  id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id             BIGINT        NULL     COMMENT '所属租户ID',
  store_id              BIGINT        NULL     COMMENT '所属门店ID',
  biz_order_no          VARCHAR(64)   NULL     COMMENT '关联业务订单号 (e.g. pos_order.order_no)',
  member_id             BIGINT        NULL     COMMENT '会员ID -> member.id',
  member_name           VARCHAR(64)   NULL     COMMENT '会员姓名 (冗余)',
  member_email          VARCHAR(128)  NULL     COMMENT '会员邮箱 (BNPL 平台通知用)',
  member_phone          VARCHAR(32)   NULL     COMMENT '会员手机号 (BNPL 平台通知用)',
  provider              VARCHAR(32)   NULL     COMMENT 'BNPL 平台 atome/klarna/afterpay/affirm',
  provider_order_no     VARCHAR(128)  NULL     COMMENT 'BNPL 平台返回的订单号',
  amount                DECIMAL(18,2) NULL     COMMENT '订单总额',
  currency              VARCHAR(8)    NULL     DEFAULT 'CNY' COMMENT '币种',
  installments          INT           NULL     COMMENT '分期期数',
  installment_amount    DECIMAL(18,2) NULL     COMMENT '每期应还金额',
  down_payment          DECIMAL(18,2) NULL     COMMENT '首付金额',
  platform_fee          DECIMAL(18,2) NULL     COMMENT '平台手续费',
  status                INT           NULL     DEFAULT 1 COMMENT '1待确认 2已确认 3已发货 4已收货 5已完成 6已取消 7退款中 8已退款',
  repayment_schedule    TEXT          NULL     COMMENT '分期还款计划 (JSON)',
  first_repay_date      DATE          NULL     COMMENT '首次还款日',
  remark                VARCHAR(512)  NULL     COMMENT '备注',
  create_time           DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  update_time           DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted               INT           NULL     DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (id),
  KEY idx_bnpl_tenant (tenant_id),
  KEY idx_bnpl_store (store_id),
  KEY idx_bnpl_member (member_id),
  KEY idx_bnpl_biz_order (biz_order_no),
  KEY idx_bnpl_provider (provider),
  KEY idx_bnpl_status (status),
  KEY idx_bnpl_provider_order (provider_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='BNPL 先买后付订单 (BnplOrder)';
