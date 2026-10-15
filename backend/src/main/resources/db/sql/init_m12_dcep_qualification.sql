-- ============================================================
-- M12-P3A 数字人民币商户资质材料
-- 关联: 与 dcep_config 1:1 (按 config_id)
-- 设计: 资质材料以"配置"为单位，一份配置可对应一组资质材料
--       图片以"材料类型+序号"区分，不存 base64，统一放本地 storage/dcep/qualification/
-- ============================================================

CREATE TABLE IF NOT EXISTS dcep_merchant_qualification (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  config_id       BIGINT       NULL COMMENT 'FK→dcep_config.id (NULL 表示尚未绑定)',
  legal_person    VARCHAR(64)  NULL COMMENT '法人姓名',
  legal_id_no     VARCHAR(32)  NULL COMMENT '法人身份证号 (存文本，加密存储由上层负责)',
  legal_id_front  VARCHAR(256) NULL COMMENT '法人身份证正面图路径',
  legal_id_back   VARCHAR(256) NULL COMMENT '法人身份证反面图路径',
  business_license_no   VARCHAR(64)  NULL COMMENT '营业执照注册号',
  business_license_img  VARCHAR(256) NULL COMMENT '营业执照图片路径',
  org_code             VARCHAR(64)  NULL COMMENT '组织机构代码证 (可选)',
  tax_no               VARCHAR(64)  NULL COMMENT '税务登记号',
  bank_account_name    VARCHAR(64)  NULL COMMENT '银行账户名',
  bank_account_no     VARCHAR(64)  NULL COMMENT '银行账号',
  bank_name            VARCHAR(64)  NULL COMMENT '开户行名称',
  bank_branch          VARCHAR(128) NULL COMMENT '开户支行',
  contact_person       VARCHAR(64)  NULL COMMENT '联系人',
  contact_phone        VARCHAR(32)  NULL COMMENT '联系电话',
  contact_email        VARCHAR(128) NULL COMMENT '联系邮箱',
  address              VARCHAR(256) NULL COMMENT '经营地址',
  remark               VARCHAR(512) NULL COMMENT '备注',
  audit_status         TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态 0=待提交 1=审核中 2=已通过 3=已驳回',
  audit_remark         VARCHAR(512) NULL COMMENT '审核意见',
  audit_at             DATETIME     NULL COMMENT '审核时间',
  created_at           DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config (config_id),
  KEY idx_audit (audit_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数字人民币商户资质材料';

-- 资质材料附件表（支持多张图：每种材料类型可上传多张，如多张身份证）
CREATE TABLE IF NOT EXISTS dcep_qualification_attachment (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  qualification_id BIGINT      NOT NULL COMMENT 'FK→dcep_merchant_qualification.id',
  material_type   VARCHAR(32)  NOT NULL COMMENT '材料类型 legal_id_front/legal_id_back/business_license/other',
  file_name       VARCHAR(256) NOT NULL COMMENT '原始文件名',
  file_path       VARCHAR(256) NOT NULL COMMENT '相对 storage/dcep/qualification/ 的路径',
  file_size       BIGINT       NOT NULL DEFAULT 0 COMMENT '字节',
  content_type    VARCHAR(64)  NULL COMMENT 'MIME',
  sort_no         INT          NOT NULL DEFAULT 0 COMMENT '同类型内的排序',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_qual (qualification_id, material_type, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='数字人民币资质材料附件';