-- ============================================================
-- I165 修复：分享上报幂等窗口配置（sys_config）
-- 对应 Issue：I165（POST /invite/{codeId}/report-share 缺租户校验 + 无幂等）
-- 配套：InviteService.acquireShareIdempotency 读取本配置作为 Redis 幂等键 TTL
--
-- 配置项：invite.share.idempotent.window
--   含义：同一 codeId + 同一 IP 的分享上报去重窗口（单位：秒）
--   默认：600 秒（代码内兜底默认值与本行一致）
--
-- 幂等行为（服务端）：
--   - 窗口内首次上报 → share_count + 1
--   - 窗口内重复上报 → 不计数，但返回成功（不暴露幂等命中与否）
--   - Redis 不可用 → fail-open（放行计数），仅告警
--
-- 幂等性：本脚本可重复执行（INSERT IGNORE + 唯一键 config_key）
-- 兼容性：sys_config 无 tenant_id 列 → 全局配置，对所有租户生效
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_config (config_key, config_value, config_group, description, sort_order)
VALUES ('invite.share.idempotent.window', '600', 'system',
        '邀请分享上报幂等窗口（秒）：同 codeId + 同 IP 在窗口内只计一次 share_count', 0);
