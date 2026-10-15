-- ============================================================
-- M12-P3A 数字人民币（DCEP）配置表
-- 说明:
--   - 与实体 com.meiyetec.entity.DcepConfig (@TableName("dcep_config")) 对齐
--   - 一条记录 = 一组 DCEP 商户凭据
--   - 当前系统按"全局单例"使用（见 DcepConfigController.list().findFirst()），
--     如未来需要多租户隔离，可加 tenant_id 列并在 Controller/Service 加过滤。
--   - 凭据列(apiKey/privateKey/publicKey)存放 RSA / API 凭据，
--     生产环境请在上层做加密存储（KMS / 应用层加解密），本 DDL 不引入列级加密。
-- 关联表:
--   - dcep_merchant_qualification.config_id → dcep_config.id
--     (见 init_m12_dcep_qualification.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS dcep_config (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  merchant_id     VARCHAR(64)  NULL     COMMENT '商户号',
  merchant_name   VARCHAR(128) NULL     COMMENT '商户名称',
  app_id          VARCHAR(128) NULL     COMMENT '应用 ID',
  api_key         TEXT         NULL     COMMENT 'API Key (建议上层加密)',
  private_key     MEDIUMTEXT   NULL     COMMENT '商户 RSA 私钥 PEM (PKCS#8, 建议加密)',
  public_key      MEDIUMTEXT   NULL     COMMENT '商户 RSA 公钥 PEM (X.509, 建议加密)',
  notify_url      VARCHAR(256) NULL     COMMENT '异步通知地址',
  environment     VARCHAR(32)  NULL     DEFAULT 'sandbox' COMMENT 'sandbox / production',
  enabled         TINYINT      NULL     DEFAULT 0 COMMENT '0=禁用 1=启用',
  description     VARCHAR(512) NULL     COMMENT '备注',
  created_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_env_enabled (environment, enabled),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数字人民币（DCEP）商户配置';
