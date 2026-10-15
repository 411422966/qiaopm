-- ================================================================
-- M12 系统设置模块 - 4 张表
-- ================================================================

-- 1. 系统配置表（KV 形式，存店铺信息/营业参数/系统开关）
CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  config_key VARCHAR(64) NOT NULL COMMENT '配置键',
  config_value TEXT COMMENT '配置值',
  config_group VARCHAR(32) DEFAULT 'default' COMMENT '分组(shop/biz/payment/print)',
  description VARCHAR(128) COMMENT '说明',
  sort_order INT DEFAULT 0 COMMENT '排序',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 2. 租户表（多店铺支持）
CREATE TABLE IF NOT EXISTS sys_tenant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tenant_code VARCHAR(32) NOT NULL COMMENT '租户编码',
  tenant_name VARCHAR(64) NOT NULL COMMENT '租户名称',
  contact_name VARCHAR(32) COMMENT '联系人',
  contact_phone VARCHAR(20) COMMENT '联系电话',
  address VARCHAR(255) COMMENT '地址',
  logo_url VARCHAR(255) COMMENT 'Logo',
  status TINYINT DEFAULT 1 COMMENT '状态(1启用/0禁用)',
  expire_at DATETIME COMMENT '到期时间',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 3. 操作日志表
CREATE TABLE IF NOT EXISTS sys_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT COMMENT '用户ID',
  username VARCHAR(32) COMMENT '用户名',
  module VARCHAR(32) COMMENT '模块',
  action VARCHAR(32) COMMENT '操作(create/update/delete/login/...)',
  description VARCHAR(255) COMMENT '操作描述',
  request_url VARCHAR(255) COMMENT '请求URL',
  request_method VARCHAR(8) COMMENT 'HTTP方法',
  request_params TEXT COMMENT '请求参数',
  ip VARCHAR(45) COMMENT 'IP',
  user_agent VARCHAR(255) COMMENT 'UA',
  cost_ms INT COMMENT '耗时ms',
  status TINYINT DEFAULT 1 COMMENT '状态(1成功/0失败)',
  error_msg VARCHAR(500) COMMENT '错误信息',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_created_at (created_at),
  KEY idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

-- 4. 数字人民币配置表
CREATE TABLE IF NOT EXISTS dcep_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户号',
  merchant_name VARCHAR(128) COMMENT '商户名称',
  app_id VARCHAR(64) COMMENT 'AppID',
  api_key VARCHAR(255) COMMENT 'API密钥',
  private_key TEXT COMMENT '私钥',
  public_key TEXT COMMENT '公钥',
  notify_url VARCHAR(255) COMMENT '回调地址',
  environment VARCHAR(16) DEFAULT 'sandbox' COMMENT '环境(sandbox/production)',
  enabled TINYINT DEFAULT 0 COMMENT '是否启用',
  description VARCHAR(255) COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字人民币配置表';

-- 初始化租户数据
INSERT INTO sys_tenant (tenant_code, tenant_name, contact_name, contact_phone, status, expire_at)
VALUES ('MEIYETEC', '默认美业门店', '管理员', '13800138000', 1, '2030-12-31 23:59:59')
ON DUPLICATE KEY UPDATE tenant_name=VALUES(tenant_name);

-- 初始化系统配置
INSERT INTO sys_config (config_key, config_value, config_group, description) VALUES
('shop_name', '美业生活馆', 'shop', '店铺名称'),
('shop_logo', '', 'shop', '店铺Logo'),
('shop_address', '北京市朝阳区', 'shop', '店铺地址'),
('shop_phone', '400-888-8888', 'shop', '联系电话'),
('business_hours', '09:00-22:00', 'biz', '营业时间'),
('receipt_printer', 'XP-58', 'print', '小票打印机'),
('pms_module_enabled', '1', 'biz', 'PMS模块开关')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);