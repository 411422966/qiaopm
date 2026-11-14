-- I179 verification fixtures (local dev DB `meiyetec` ONLY)
-- NON-FORMAL TOOL: audit evidence collection only. Not in CI / PR mandatory list.
-- Purpose: minimal reproducible dataset for
--          I179 (InviteService.trackClick -- scan_count must NOT be incremented
--                when the request carries no phone number)
-- Exit code convention: 0 = fixtures ready ; 1 = existence check failed (aborted, nothing written)
-- NOTE: ASCII-only fixture text on purpose -- piping UTF-8 through Windows
--       PowerShell -> docker exec mangles non-ASCII into '?'.
--
-- Existence check runs FIRST (fail-fast): if any target id already exists, abort.
-- Target ids: m09_invite_campaign 990201 / 990203, m09_invite_code 990202 / 990203.

-- ============ existence check (fail-fast) ============
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_campaign_id
  FROM m09_invite_campaign WHERE id IN (990201, 990203);
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_code_id
  FROM m09_invite_code WHERE id IN (990202, 990203);

-- ============ campaigns ============
-- 990201: invite_need_register = 1 (regular campaign -- scan_count counts only
--          visits that carry a phone number)
-- 990203: invite_need_register = 0 (scan-only campaign -- scan_count still counts,
--          preserved behaviour of the original code)
INSERT INTO m09_invite_campaign
  (id, tenant_id, campaign_code, campaign_name, campaign_type,
   inviter_reward_type, inviter_reward_value, invitee_reward_type, invitee_reward_value,
   invitee_min_consume, invite_need_register, inviter_quota_per_day, inviter_quota_total,
   campaign_quota_total, share_title, share_desc, start_time, end_time, status,
   operator_id, operator_name, remark)
VALUES
  (990201, 1, 'TESTAUDIT_I179_C', 'AuditFixtureCampaignI179C', 'INVITE_REWARD',
   1, 10.00, 1, 5.00, 0.00, 1, 5, 50, 0,
   'AuditShareTitleI179C', 'AuditShareDescI179C', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1,
   900006, 'operator01', 'audit fixture tenant1 I179 regular'),
  (990203, 1, 'TESTAUDIT_I179_D', 'AuditFixtureCampaignI179D', 'INVITE_REWARD',
   1, 10.00, 1, 5.00, 0.00, 0, 5, 50, 0,
   'AuditShareTitleI179D', 'AuditShareDescI179D', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1,
   900006, 'operator01', 'audit fixture tenant1 I179 scan-only');

-- ============ invite codes ============
INSERT INTO m09_invite_code
  (id, tenant_id, campaign_id, member_id, member_name, member_phone, invite_code,
   share_count, scan_count, register_count, success_count)
VALUES
  (990202, 1, 990201, 1, 'AuditMemberI179C', '13800000993', 'TESTI179C', 0, 0, 0, 0),
  (990203, 1, 990203, 1, 'AuditMemberI179D', '13800000994', 'TESTI179D', 0, 0, 0, 0);

-- ============ verification ============
SELECT id, tenant_id, campaign_id, invite_code, scan_count, share_count
  FROM m09_invite_code WHERE id IN (990202, 990203);
SELECT id, tenant_id, campaign_code, invite_need_register, status
  FROM m09_invite_campaign WHERE id IN (990201, 990203);
