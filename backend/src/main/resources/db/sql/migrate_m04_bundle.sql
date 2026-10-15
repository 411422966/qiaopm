-- M04 项目组合 — crm_package 表 bundle 结构支持

ALTER TABLE crm_package
  ADD COLUMN bundle_type INT DEFAULT 0 COMMENT '组合类型：0=单商品 1=组合（含子项目）';

ALTER TABLE crm_package
  ADD COLUMN bundle_config TEXT COMMENT '组合子项目 JSON：[{serviceId, serviceName, times, unitPrice}, ...]';

ALTER TABLE crm_package
  ADD COLUMN bundle_item_count INT DEFAULT 0 COMMENT '组合子项目总数（冗余）';

ALTER TABLE crm_package
  ADD INDEX idx_bundle_type (bundle_type);
