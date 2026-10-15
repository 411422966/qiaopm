-- ============================================================
-- EXT-23：plugin_runtime_state 表（插件运行时状态持久化）
-- 对应功能：跨重启可观测的插件状态记录
-- 注意：本表只做"最近一次已知状态"快照，实际 pf4j 状态机由
--      PluginRuntimeService 启动期 loadAllFromDisk() 重建。
-- ============================================================

CREATE TABLE IF NOT EXISTS plugin_runtime_state (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  plugin_code VARCHAR(64) NOT NULL,
  version VARCHAR(32) DEFAULT NULL,
  plugin_id VARCHAR(96) DEFAULT NULL,
  state VARCHAR(16) DEFAULT NULL COMMENT 'CREATED/STARTED/STOPPED/FAILED/DISABLED/UNLOADED',
  last_error VARCHAR(500) DEFAULT NULL,
  loaded_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_plugin (tenant_id, plugin_code),
  INDEX idx_state (state),
  INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='插件运行时状态持久化（EXT-23）';
