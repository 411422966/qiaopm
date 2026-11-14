-- ============================================================
-- I177：预约确认发送幂等窗口配置（sys_config）
-- 对应 Issue：I177（POST /intl-booking/{id}/send-confirmation 及
--              POST /intl-booking/{id}/status 的 CONFIRMED 自动触发
--              无防重复触发机制 → 阿里云短信 / SMTP 邮件 / 微信订阅消息重复计费）
-- 配套：BookingConfirmationIdempotency.acquire() 读取本配置作为 Redis 幂等键 TTL
--
-- 配置项：intl.booking.send.idempotent.window
--   含义：同一 tenantId + bookingId 的确认消息发送去重窗口（单位：秒）
--   默认：300 秒（代码内兜底默认值与本行一致）
--
-- 行为：
--   - 手动触发端点命中幂等 → 返回 code=400「确认消息已在发送中，请勿重复操作」
--   - 自动触发命中幂等   → 跳过发送，响应带 confirmationSkipped=true
--   - Redis 不可用       → fail-open（放行发送），仅告警
--
-- 幂等性：本脚本可重复执行（INSERT IGNORE + 唯一键 config_key）
-- 兼容性：sys_config 无 tenant_id 列 → 全局配置，对所有租户生效
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_config (config_key, config_value, config_group, description, sort_order)
VALUES ('intl.booking.send.idempotent.window', '300', 'system',
        '预约确认发送幂等窗口（秒）：同 tenantId + bookingId 在窗口内只发一次确认消息', 0);

-- 验证
SELECT config_key, config_value, config_group, description
  FROM sys_config
 WHERE config_key = 'intl.booking.send.idempotent.window';
