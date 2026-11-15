-- =============================================================================
-- I178 fixture -- channel quota counted per brand (tenant + brand + channel)
-- NON-FORMAL TOOL: audit evidence collection only. NOT in CI / PR mandatory list.
-- Purpose  : create 4 isolated brand-level channel configs for IN_APP so that
--            per-brand quota behaviour can be observed at runtime.
-- Safety   : brand ids 990301-990304 are synthetic; the verify script performs a
--            fail-fast existence check BEFORE running this file.
-- Cleanup  : the verify script deletes these rows (and the tasks/logs it created)
--            at the end. See raw/verify-i178.ps1.txt.
-- =============================================================================

INSERT INTO mkt_channel_config
    (tenant_id, brand_id, channel_type, channel_name, provider,
     rate_limit_per_min, rate_limit_per_day, status, operator_name, deleted)
VALUES
    -- brand A: daily gate only (per_min large) -> quota consumed by 2 sends/day
    (1, 990301, 'IN_APP', 'I178 TEST BRAND A', 'INTERNAL', 1000, 2, 1, 'audit-i178', 0),
    -- brand B: same shape as brand A -> proves brands do not share the pool
    (1, 990302, 'IN_APP', 'I178 TEST BRAND B', 'INTERNAL', 1000, 2, 1, 'audit-i178', 0),
    -- brand C: minute gate only (per_day large) -> quota consumed by 2 sends/min
    (1, 990303, 'IN_APP', 'I178 TEST BRAND C', 'INTERNAL', 2, 1000, 1, 'audit-i178', 0),
    -- brand D: strict daily gate -> quota consumed by 1 send/day
    (1, 990304, 'IN_APP', 'I178 TEST BRAND D', 'INTERNAL', 1000, 1, 1, 'audit-i178', 0);
