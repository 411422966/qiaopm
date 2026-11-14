-- =============================================================================
-- I156 / I157 隔离测试夹具（共享）
-- 用途：
--   I157（年化分母下限）：造「短 tenure 新客」与「长 tenure 老客」两类样本
--   I156（computeChurn 互斥）：新客/老客参与 churn 计算，验证两写入者不再双写
-- 隔离性：
--   - 使用绝对不存在的兜底 ID 990001 / 990002（脚本运行前做存在性检查，命中即中止）
--   - tenant_id = 1（与业务数据同租户，端点按 JWT 租户隔离）
--   - 所有新增行以 test-I157- 前缀标识，验证后整体删除
-- 注意：仅 INSERT 会员与订单，不写 crm_ltv_snapshot / crm_churn_prediction
--       （这两张表的测试行由端点产生，按 snapshot_date = CURDATE() 清理）
-- =============================================================================

-- ---- fail-fast：目标 ID / 单号若已存在则中止，绝不覆盖业务数据 ----
-- SELECT COUNT(*) FROM crm_member WHERE id IN (990001, 990002);          -- 期望 0
-- SELECT COUNT(*) FROM pos_order WHERE order_no LIKE 'test-I157-%';     -- 期望 0

-- ---- 样本 A：短 tenure 新客（1 单，7 天前）----
-- 预期：修复前 frequency = 1 * 365 / 7 = 52.14；修复后 = 1 * 365 / 30 = 12.17
INSERT INTO crm_member (id, member_no, name, phone, tenant_id, brand_id, store_id,
                        join_date, status, deleted, create_time, update_time)
VALUES (990001, 'test-I157-NEW', 'test-I157-新客', '19900009901', 1, 1, 1,
        CURDATE() - INTERVAL 7 DAY, 1, 0, NOW(), NOW());

-- ---- 样本 B：长 tenure 老客（5 单，200 天前起）----
-- 预期：修复前后 frequency 均为 5 * 365 / 200 = 9.13（分母 > 30，数值不变）
INSERT INTO crm_member (id, member_no, name, phone, tenant_id, brand_id, store_id,
                        join_date, status, deleted, create_time, update_time)
VALUES (990002, 'test-I157-LONG', 'test-I157-老客', '19900009902', 1, 1, 1,
        CURDATE() - INTERVAL 200 DAY, 1, 0, NOW(), NOW());

-- ---- 样本 A 的唯一一单（7 天前）----
INSERT INTO pos_order (id, tenant_id, store_id, order_no, member_id, member_name,
                       type, total_amount, discount_amount, final_amount, paid_amount,
                       cashier_id, cashier_name, status, pay_time, deleted, create_time)
VALUES (990001, 1, 1, 'test-I157-NEW-001', 990001, 'test-I157-新客',
        1, 500.00, 0.00, 500.00, 500.00, 1, 'audit-fixture', 2,
        CURDATE() - INTERVAL 7 DAY, 0, NOW());

-- ---- 样本 B 的 5 单（200 天前 ~ 180 天前）----
INSERT INTO pos_order (id, tenant_id, store_id, order_no, member_id, member_name,
                       type, total_amount, discount_amount, final_amount, paid_amount,
                       cashier_id, cashier_name, status, pay_time, deleted, create_time)
VALUES
 (990002, 1, 1, 'test-I157-LONG-001', 990002, 'test-I157-老客', 1, 100.00, 0.00, 100.00, 100.00, 1, 'audit-fixture', 2, CURDATE() - INTERVAL 200 DAY, 0, NOW()),
 (990003, 1, 1, 'test-I157-LONG-002', 990002, 'test-I157-老客', 1, 100.00, 0.00, 100.00, 100.00, 1, 'audit-fixture', 2, CURDATE() - INTERVAL 195 DAY, 0, NOW()),
 (990004, 1, 1, 'test-I157-LONG-003', 990002, 'test-I157-老客', 1, 100.00, 0.00, 100.00, 100.00, 1, 'audit-fixture', 2, CURDATE() - INTERVAL 190 DAY, 0, NOW()),
 (990005, 1, 1, 'test-I157-LONG-004', 990002, 'test-I157-老客', 1, 100.00, 0.00, 100.00, 100.00, 1, 'audit-fixture', 2, CURDATE() - INTERVAL 185 DAY, 0, NOW()),
 (990006, 1, 1, 'test-I157-LONG-005', 990002, 'test-I157-老客', 1, 100.00, 0.00, 100.00, 100.00, 1, 'audit-fixture', 2, CURDATE() - INTERVAL 180 DAY, 0, NOW());
