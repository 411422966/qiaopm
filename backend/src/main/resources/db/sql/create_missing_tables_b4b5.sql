-- ============================================================
-- Batch 4 + Batch 5 缺失表补齐（meiyetec 库）
-- ============================================================

CREATE TABLE IF NOT EXISTS kiosk_device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  store_id BIGINT,
  device_code VARCHAR(64) NOT NULL,
  device_name VARCHAR(128),
  location VARCHAR(255),
  ip_address VARCHAR(64),
  last_heartbeat DATETIME,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS kiosk_checkin (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  device_id BIGINT,
  store_id BIGINT,
  booking_id BIGINT,
  member_id BIGINT,
  checkin_method VARCHAR(32),
  checkin_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  arrived_at DATETIME,
  service_started_at DATETIME,
  service_finished_at DATETIME,
  status VARCHAR(32) DEFAULT 'CHECKED_IN',
  staff_id BIGINT,
  notes VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_device (device_id),
  INDEX idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS complication_sop (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  sop_code VARCHAR(64) NOT NULL,
  sop_name VARCHAR(255),
  category VARCHAR(64),
  severity VARCHAR(32),
  trigger_symptom VARCHAR(500),
  trigger_timing VARCHAR(64),
  steps_json TEXT,
  medications VARCHAR(500),
  equipment VARCHAR(255),
  escalation_rule VARCHAR(500),
  response_deadline_min INT,
  responsible_role VARCHAR(64),
  version VARCHAR(16),
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_code (tenant_id, sop_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS complication_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  event_no VARCHAR(64),
  sop_id BIGINT,
  sop_code VARCHAR(64),
  member_id BIGINT,
  order_id BIGINT,
  service_id BIGINT,
  severity VARCHAR(32),
  symptoms TEXT,
  trigger_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  response_time DATETIME,
  resolved_time DATETIME,
  handler_id BIGINT,
  handler_name VARCHAR(64),
  status VARCHAR(32) DEFAULT 'OPEN',
  execution_log TEXT,
  outcome VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_member (member_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS cohort_definition (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  cohort_code VARCHAR(64) NOT NULL,
  cohort_name VARCHAR(255),
  cohort_type VARCHAR(32),
  metric VARCHAR(64),
  target_dim VARCHAR(64),
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_code (tenant_id, cohort_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS cohort_result (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  cohort_id BIGINT NOT NULL,
  cohort_code VARCHAR(64),
  cohort_period VARCHAR(32),
  size INT,
  period_index INT,
  value DECIMAL(18,2),
  value_type VARCHAR(32),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_cohort (cohort_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS gdpr_consent (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  member_id BIGINT,
  consent_type VARCHAR(64),
  granted TINYINT DEFAULT 1,
  version VARCHAR(16),
  ip_address VARCHAR(64),
  user_agent VARCHAR(255),
  channel VARCHAR(32),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS gdpr_data_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  request_no VARCHAR(64) NOT NULL,
  member_id BIGINT,
  request_type VARCHAR(32),
  status VARCHAR(32) DEFAULT 'SUBMITTED',
  export_url VARCHAR(500),
  export_expire_at DATETIME,
  reason VARCHAR(500),
  processor_id BIGINT,
  processed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_no (request_no),
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS gdpr_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  actor_id BIGINT,
  actor_name VARCHAR(64),
  action VARCHAR(64),
  target_type VARCHAR(64),
  target_id BIGINT,
  pii_data TINYINT,
  details TEXT,
  ip_address VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS hipaa_encryption_key (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  key_alias VARCHAR(64) NOT NULL,
  key_version INT,
  algorithm VARCHAR(32),
  key_status VARCHAR(32) DEFAULT 'ACTIVE',
  public_key VARCHAR(500),
  kms_arn VARCHAR(255),
  rotated_at DATETIME,
  next_rotate_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS hipaa_phi_access (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  member_id BIGINT,
  field_name VARCHAR(64),
  access_type VARCHAR(32),
  actor_id BIGINT,
  actor_name VARCHAR(64),
  actor_role VARCHAR(64),
  purpose VARCHAR(255),
  ip_address VARCHAR(64),
  is_break_glass TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS pos_device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  store_id BIGINT,
  vendor VARCHAR(32),
  device_code VARCHAR(64) NOT NULL,
  device_name VARCHAR(128),
  serial_number VARCHAR(128),
  api_key VARCHAR(255),
  location_id VARCHAR(64),
  webhook_url VARCHAR(500),
  last_heartbeat DATETIME,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_device_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS pos_transaction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  pos_id BIGINT,
  store_id BIGINT,
  tx_no VARCHAR(64),
  vendor VARCHAR(32),
  external_tx_id VARCHAR(64),
  amount DECIMAL(18,2),
  tip DECIMAL(18,2),
  tax DECIMAL(18,2),
  currency VARCHAR(8) DEFAULT 'USD',
  card_brand VARCHAR(32),
  card_last4 VARCHAR(8),
  status VARCHAR(32),
  order_id BIGINT,
  member_id BIGINT,
  raw_payload TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id),
  INDEX idx_pos (pos_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS plugin_market (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT DEFAULT 0,
  plugin_code VARCHAR(64) NOT NULL,
  plugin_name VARCHAR(255),
  category VARCHAR(64),
  version VARCHAR(32),
  vendor VARCHAR(128),
  short_desc VARCHAR(500),
  description TEXT,
  icon_url VARCHAR(500),
  tags VARCHAR(255),
  price DECIMAL(18,2),
  billing_type VARCHAR(32),
  install_count INT DEFAULT 0,
  rating DECIMAL(3,2) DEFAULT 0,
  review_count INT DEFAULT 0,
  is_official TINYINT DEFAULT 0,
  is_featured TINYINT DEFAULT 0,
  homepage VARCHAR(500),
  tarball_url VARCHAR(500),
  config_json TEXT,
  permissions VARCHAR(500),
  events VARCHAR(500),
  status VARCHAR(16) DEFAULT 'PUBLISHED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plugin_code (plugin_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS plugin_install (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  plugin_id BIGINT,
  plugin_code VARCHAR(64),
  version VARCHAR(32),
  installed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  expire_at DATETIME,
  config_json TEXT,
  enabled TINYINT DEFAULT 1,
  install_status VARCHAR(32) DEFAULT 'ACTIVE',
  error_message VARCHAR(500),
  last_used_at DATETIME,
  use_count BIGINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS plugin_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT,
  plugin_id BIGINT NOT NULL,
  member_id BIGINT,
  rating INT,
  title VARCHAR(255),
  content TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS fiscal_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  invoice_no VARCHAR(64) NOT NULL,
  invoice_type VARCHAR(32),
  buyer_name VARCHAR(255),
  buyer_tax_no VARCHAR(64),
  buyer_address VARCHAR(500),
  buyer_phone VARCHAR(32),
  buyer_email VARCHAR(128),
  buyer_bank VARCHAR(128),
  buyer_account VARCHAR(64),
  amount DECIMAL(18,2),
  tax_amount DECIMAL(18,2),
  total_amount DECIMAL(18,2),
  tax_rate DECIMAL(5,4),
  invoice_status VARCHAR(32) DEFAULT 'ISSUED',
  invoice_url VARCHAR(500),
  pdf_url VARCHAR(500),
  fiscal_no VARCHAR(64),
  order_id BIGINT,
  channel VARCHAR(32),
  error_message VARCHAR(500),
  issued_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invoice_no (invoice_no),
  INDEX idx_tenant (tenant_id),
  INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS crm_integration (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  platform VARCHAR(64) NOT NULL,
  app_id VARCHAR(128),
  client_id VARCHAR(128),
  client_secret VARCHAR(255),
  access_token VARCHAR(500),
  refresh_token VARCHAR(500),
  token_expire_at DATETIME,
  api_base_url VARCHAR(255),
  portal_id VARCHAR(64),
  sync_direction VARCHAR(32),
  sync_members TINYINT DEFAULT 0,
  sync_orders TINYINT DEFAULT 0,
  sync_contacts TINYINT DEFAULT 0,
  sync_deals TINYINT DEFAULT 0,
  auto_sync TINYINT DEFAULT 0,
  last_sync_at DATETIME,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  error_message VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS crm_sync_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT,
  platform VARCHAR(64),
  sync_type VARCHAR(32),
  direction VARCHAR(16),
  success_count INT DEFAULT 0,
  fail_count INT DEFAULT 0,
  skipped_count INT DEFAULT 0,
  error_message VARCHAR(500),
  duration_ms INT,
  status VARCHAR(32),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ecommerce_platform (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT DEFAULT 0,
  platform VARCHAR(64),
  shop_name VARCHAR(255),
  shop_id VARCHAR(64),
  app_key VARCHAR(255),
  app_secret VARCHAR(255),
  access_token VARCHAR(500),
  token_expire_at DATETIME,
  webhook_url VARCHAR(500),
  webhook_secret VARCHAR(255),
  api_base_url VARCHAR(255),
  sync_products TINYINT DEFAULT 0,
  sync_orders TINYINT DEFAULT 0,
  sync_inventory TINYINT DEFAULT 0,
  last_sync_at DATETIME,
  status VARCHAR(16) DEFAULT 'ACTIVE',
  error_message VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ecommerce_product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT DEFAULT 0,
  platform VARCHAR(64),
  external_product_id VARCHAR(64),
  local_product_id BIGINT,
  product_name VARCHAR(255),
  sku VARCHAR(64),
  price DECIMAL(18,2),
  stock INT,
  status VARCHAR(32),
  last_sync_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ecommerce_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT DEFAULT 0,
  platform VARCHAR(64),
  external_order_id VARCHAR(64),
  local_order_id BIGINT,
  order_no VARCHAR(64),
  buyer_name VARCHAR(128),
  buyer_phone VARCHAR(32),
  amount DECIMAL(18,2),
  shipping_fee DECIMAL(18,2),
  total_amount DECIMAL(18,2),
  status VARCHAR(32),
  address VARCHAR(500),
  tracking_no VARCHAR(64),
  shipping_status VARCHAR(32),
  items_json TEXT,
  raw_payload TEXT,
  order_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ecommerce_sync_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT,
  platform VARCHAR(64),
  sync_type VARCHAR(32),
  success_count INT DEFAULT 0,
  fail_count INT DEFAULT 0,
  duration_ms INT,
  status VARCHAR(32),
  error_message VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
