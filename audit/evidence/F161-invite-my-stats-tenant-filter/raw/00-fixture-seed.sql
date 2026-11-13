-- I164/I165/I166 verification fixtures (local dev DB `meiyetec` ONLY)
-- NON-FORMAL TOOL: audit evidence collection only. Not in CI / PR mandatory list.
-- Purpose: minimal reproducible dataset for
--          I164 (my-stats cross-tenant IDOR)
--          I165 (report-share cross-tenant write + idempotency)
--          I166 (share-page implicit trackClick double counting)
-- Exit code convention: 0 = fixtures ready ; 1 = existence check failed (aborted, nothing written)
-- NOTE: ASCII-only fixture text on purpose -- piping UTF-8 through Windows
--       PowerShell -> docker exec mangles non-ASCII into '?'.
--
-- Existence check runs FIRST (fail-fast): if any target id already exists, abort.
-- Target ids: campaign 8/9, code 8/9, record 8/9/10, reward_log 8/9
-- All of them are dedicated audit ids, never real business primary keys.

-- ============ existence check (fail-fast) ============
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_campaign_id
  FROM m09_invite_campaign WHERE id IN (8,9);
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_code_id
  FROM m09_invite_code WHERE id IN (8,9);
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_record_id
  FROM m09_invite_record WHERE id IN (8,9,10);
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_reward_log_id
  FROM m09_invite_reward_log WHERE id IN (8,9);

-- ============ campaigns ============
INSERT INTO m09_invite_campaign
  (id, tenant_id, campaign_code, campaign_name, campaign_type,
   inviter_reward_type, inviter_reward_value, invitee_reward_type, invitee_reward_value,
   invitee_min_consume, invite_need_register, inviter_quota_per_day, inviter_quota_total,
   campaign_quota_total, share_title, share_desc, start_time, end_time, status,
   operator_id, operator_name, remark)
VALUES
  (8, 1, 'TESTAUDIT_C1', 'AuditFixtureCampaignC1', 'INVITE_REWARD',
   1, 10.00, 1, 5.00, 0.00, 1, 5, 50, 0,
   'AuditShareTitleC1', 'AuditShareDescC1', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1,
   900006, 'operator01', 'audit fixture tenant1'),
  (9, 2, 'TESTAUDIT_C2', 'AuditFixtureCampaignC2', 'INVITE_REWARD',
   1, 10.00, 1, 5.00, 0.00, 1, 5, 50, 0,
   'AuditShareTitleC2', 'AuditShareDescC2', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1,
   900006, 'operator01', 'audit fixture tenant2');

-- ============ invite codes ============
INSERT INTO m09_invite_code
  (id, tenant_id, campaign_id, member_id, member_name, member_phone, invite_code,
   share_count, scan_count, register_count, success_count)
VALUES
  (8, 1, 8, 1, 'AuditMemberA', '13800000001', 'TESTI164A', 0, 0, 0, 0),
  (9, 2, 9, 1, 'AuditMemberB', '13800000002', 'TESTI165B', 0, 0, 0, 0);

-- ============ invite records ============
-- memberId=1 deliberately exists in BOTH tenants: this is what exposes the
-- missing tenant_id filter (I164). Expected leak WITHOUT the fix:
--   successInvites=2 (t1 status=4 + t2 status=4), pendingRewards=1 (t2 status=3),
--   rewardReceived=2 (t1 log + t2 log).
INSERT INTO m09_invite_record
  (id, tenant_id, campaign_id, campaign_name, invite_code, inviter_member_id, inviter_name,
   invitee_phone, invitee_name, channel, status)
VALUES
  (8,  1, 8, 'AuditFixtureCampaignC1', 'TESTI164A', 1, 'AuditMemberA', '13900000001', 'InviteeA', 'LINK', 4),
  (9,  2, 9, 'AuditFixtureCampaignC2', 'TESTI165B', 1, 'AuditMemberB', '13900000002', 'InviteeB', 'LINK', 4),
  (10, 2, 9, 'AuditFixtureCampaignC2', 'TESTI165B', 1, 'AuditMemberB', '13900000003', 'InviteeC', 'LINK', 3);

-- ============ reward logs ============
INSERT INTO m09_invite_reward_log
  (id, tenant_id, record_id, campaign_id, campaign_name, reward_to, member_id, member_name,
   reward_type, reward_type_text, reward_value, status, is_auto)
VALUES
  (8, 1, 8, 8, 'AuditFixtureCampaignC1', 1, 1, 'AuditMemberA', 1, 'COUPON', 10.00, 1, 1),
  (9, 2, 9, 9, 'AuditFixtureCampaignC2', 1, 1, 'AuditMemberB', 1, 'COUPON', 10.00, 1, 1);

-- ============ idempotency window config ============
INSERT IGNORE INTO sys_config (config_key, config_value, config_group, description, sort_order)
VALUES ('invite.share.idempotent.window', '600', 'system',
        'invite share report idempotent window (seconds)', 0);

-- ============ verification ============
SELECT id, tenant_id, campaign_id, member_id, invite_code, share_count, scan_count
  FROM m09_invite_code WHERE deleted = 0 ORDER BY id;
SELECT id, tenant_id, campaign_id, inviter_member_id, status
  FROM m09_invite_record WHERE deleted = 0 ORDER BY id;
SELECT id, tenant_id, member_id, status
  FROM m09_invite_reward_log ORDER BY id;
