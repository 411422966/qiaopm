-- I177 verification fixtures (local dev DB `meiyetec` ONLY)
-- NON-FORMAL TOOL: audit evidence collection only. Not in CI / PR mandatory list.
-- Purpose: minimal reproducible dataset for
--          I177 (POST /intl-booking/{id}/send-confirmation idempotency gate
--                POST /intl-booking/{id}/status CONFIRMED auto-trigger dedup)
-- Exit code convention: 0 = fixtures ready ; 1 = existence check failed (aborted, nothing written)
-- NOTE: ASCII-only fixture text on purpose -- piping UTF-8 through Windows
--       PowerShell -> docker exec mangles non-ASCII into '?'.
--
-- Existence check runs FIRST (fail-fast): if any target id already exists, abort.
-- Target ids: overseas_booking 990101 / 990102 -- dedicated audit ids,
-- never real business primary keys.

-- ============ existence check (fail-fast) ============
SELECT IF(COUNT(*)=0, 'OK', 'ABORT') AS check_booking_id
  FROM overseas_booking WHERE id IN (990101, 990102);

-- ============ bookings ============
-- 990101: manual-trigger subject (POST /intl-booking/990101/send-confirmation)
-- 990102: auto-trigger subject  (POST /intl-booking/990102/status -> CONFIRMED)
-- customer_phone / customer_email are set so that the SMS + EMAIL channels are
-- routed; both channels resolve to DEMO mode in this local environment
-- (mkt_channel_config id=13 has config_json='{}' -> SmsSender demo fallback;
--  every EMAIL config row is deleted=1 -> EmailSender demo fallback).
INSERT INTO overseas_booking
  (id, tenant_id, booking_no, source, external_id, customer_name, customer_phone,
   customer_email, customer_locale, service_name, booking_date, booking_time, status, notes)
VALUES
  (990101, 1, 'TESTI177-A', 'AUDIT', 'EXT-I177-A', 'AuditCustomerA', '13800000991',
   'audit-i177-a@example.invalid', 'en', 'AuditFacial', '2026-12-01', '10:00', 'NEW', 'audit fixture I177 manual'),
  (990102, 1, 'TESTI177-B', 'AUDIT', 'EXT-I177-B', 'AuditCustomerB', '13800000992',
   'audit-i177-b@example.invalid', 'en', 'AuditFacial', '2026-12-01', '11:00', 'NEW', 'audit fixture I177 auto');

-- ============ idempotency window config ============
INSERT INTO sys_config (config_key, config_value, config_group, description, sort_order)
VALUES ('intl.booking.send.idempotent.window', '300', 'system',
        'booking confirmation send idempotent window (seconds)', 0)
ON DUPLICATE KEY UPDATE config_value = '300';

-- ============ verification ============
SELECT id, tenant_id, booking_no, customer_phone, customer_email, status
  FROM overseas_booking WHERE id IN (990101, 990102);
SELECT config_key, config_value FROM sys_config
 WHERE config_key = 'intl.booking.send.idempotent.window';
