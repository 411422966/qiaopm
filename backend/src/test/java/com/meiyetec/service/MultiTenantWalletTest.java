package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-004 · 多租户钱包隔离测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>Tenant A 的钱包充值只能写到 Tenant A 的 crm_wallet_transaction.tenant_id=A</li>
 *   <li>Tenant B 的钱包充值只能写到 Tenant B 的 crm_wallet_transaction.tenant_id=B</li>
 *   <li>伪造 tenantId 不影响写库（永远取 ThreadLocal）</li>
 *   <li>不同租户的 member_id 不可跨租户互相看到流水</li>
 * </ul>
 *
 * <p>前置条件：sys_tenant 已存在 tenant_id=1 (A) 和 tenant_id=2 (B)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiTenantWalletTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    private static Long memberAId;
    private static Long memberBId;

    @Autowired private WalletService walletService;
    @Autowired private WalletTransactionService txnService;
    @Autowired private JdbcTemplate jdbc;

    private void createTestMember(Long tenantId, Long... holderOut) {
        String ts = String.valueOf(System.currentTimeMillis() + (long)(Math.random() * 10000));
        String phone = "T" + tenantId + ts.substring(ts.length() - 8);
        String memberNo = "TM" + tenantId + ts.substring(ts.length() - 8);
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, 1, ?, ?, ?, 0, 1, 0, ?, ?, CURDATE())",
            tenantId, memberNo, "多租户测试会员-" + tenantId, phone, BigDecimal.ZERO, BigDecimal.ZERO);
        Long id = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);
        if (holderOut.length > 0) holderOut[0] = id;
    }

    private void setCtx(Long tenantId, Long userId) {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(tenantId);
        ctx.setUserId(userId);
        ctx.setUsername("test-" + tenantId);
        ctx.setDataScope("SELF");
        TenantContextHolder.set(ctx);
    }

    private WalletOpDTO createRechargeDto(Long memberId, BigDecimal amount) {
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(memberId);
        dto.setAmount(amount);
        dto.setOperatorId(1L);
        dto.setPayMethod("现金");
        dto.setRemark("多租户测试");
        return dto;
    }

    // ================================================================
    // 1. Tenant A 充值 → tenant_id 必须是 A
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("P0-MONEY-004-1: Tenant A 钱包充值 → crm_wallet_transaction.tenant_id = A")
    void tenantARechargeWritesTenantA() {
        Long[] holder = new Long[1];
        createTestMember(TENANT_A, holder);
        Long mId = holder[0];

        try {
            setCtx(TENANT_A, 1L);
            walletService.doRecharge(createRechargeDto(mId, new BigDecimal("500.00")), TENANT_A);

            // 验证 crm_wallet_transaction.tenant_id = A
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT tenant_id FROM crm_wallet_transaction WHERE member_id = ? AND type = 1 ORDER BY id DESC LIMIT 1",
                mId);
            assertEquals(TENANT_A.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "Tenant A 充值 → tenant_id 必须 = A");

            // 验证 crm_wallet.balance = 500
            BigDecimal bal = jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, mId);
            assertEquals(0, new BigDecimal("500.00").compareTo(bal),
                    "Tenant A 钱包余额 = 500");
        } finally {
            cleanupMember(mId);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // 2. Tenant B 充值 → tenant_id 必须是 B
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("P0-MONEY-004-2: Tenant B 钱包充值 → crm_wallet_transaction.tenant_id = B (NOT A)")
    void tenantBRechargeWritesTenantB() {
        Long[] holder = new Long[1];
        createTestMember(TENANT_B, holder);
        Long mId = holder[0];

        try {
            setCtx(TENANT_B, 1L);
            walletService.doRecharge(createRechargeDto(mId, new BigDecimal("800.00")), TENANT_B);

            Map<String, Object> row = jdbc.queryForMap(
                "SELECT tenant_id FROM crm_wallet_transaction WHERE member_id = ? AND type = 1 ORDER BY id DESC LIMIT 1",
                mId);
            assertEquals(TENANT_B.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "Tenant B 充值 → tenant_id 必须 = B (不能错标为 A)");
            assertNotEquals(TENANT_A.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "Tenant B 充值 → 绝不能标 tenant_id = A");
        } finally {
            cleanupMember(mId);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // 3. 伪造 tenantId = 999，但 ctx = A → 写库 tenant_id 仍 = A
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("P0-MONEY-004-3: 伪造 tenantId=999 → 写库 tenant_id 仍 = ctx 中的 A")
    void spoofedTenantIdIgnored() {
        Long[] holder = new Long[1];
        createTestMember(TENANT_A, holder);
        Long mId = holder[0];

        try {
            setCtx(TENANT_A, 1L);
            // 即使调用方传 999L，最终写库必须是 ctx 中的 1L
            walletService.doRecharge(createRechargeDto(mId, new BigDecimal("100.00")), 999L);

            Map<String, Object> row = jdbc.queryForMap(
                "SELECT tenant_id FROM crm_wallet_transaction WHERE member_id = ? AND type = 1 ORDER BY id DESC LIMIT 1",
                mId);
            assertEquals(TENANT_A.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "伪造 tenantId=999 失败：ctx 中的 tenantId 永远胜出");
            assertNotEquals(999L, ((Number) row.get("tenant_id")).longValue(),
                    "伪造 tenantId=999 必须被忽略");
        } finally {
            cleanupMember(mId);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // 4. 不同租户的会员钱包完全独立
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("P0-MONEY-004-4: Tenant A 与 Tenant B 钱包完全隔离")
    void walletsAreIsolated() {
        Long[] hA = new Long[1];
        Long[] hB = new Long[1];
        createTestMember(TENANT_A, hA);
        createTestMember(TENANT_B, hB);
        Long mIdA = hA[0];
        Long mIdB = hB[0];

        try {
            setCtx(TENANT_A, 1L);
            walletService.doRecharge(createRechargeDto(mIdA, new BigDecimal("600.00")), TENANT_A);
            setCtx(TENANT_B, 1L);
            walletService.doRecharge(createRechargeDto(mIdB, new BigDecimal("900.00")), TENANT_B);

            BigDecimal balA = jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, mIdA);
            BigDecimal balB = jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, mIdB);

            assertEquals(0, new BigDecimal("600.00").compareTo(balA),
                    "Tenant A 余额应为 600");
            assertEquals(0, new BigDecimal("900.00").compareTo(balB),
                    "Tenant B 余额应为 900");

            // 验证 Tenant A 的流水 tenant_id 全部 = A
            List<WalletTransaction> txnListA = txnService.list(
                new LambdaQueryWrapper<WalletTransaction>()
                    .eq(WalletTransaction::getMemberId, mIdA)
                    .orderByDesc(WalletTransaction::getId));
            txnListA.forEach(t -> {
                if (t.getTenantId() != null) {
                    assertEquals(TENANT_A.longValue(), t.getTenantId().longValue(),
                            "Tenant A 会员的所有流水 tenant_id 应均为 A, 实际=" + t.getTenantId());
                }
            });
        } finally {
            cleanupMember(mIdA);
            cleanupMember(mIdB);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // 5. 跨租户消费：Tenant A 消费 → tenant_id 仍 = A
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("P0-MONEY-004-5: Tenant A 会员消费 → crm_wallet_transaction.tenant_id = A")
    void tenantAConsumeWritesTenantA() {
        Long[] holder = new Long[1];
        createTestMember(TENANT_A, holder);
        Long mId = holder[0];

        try {
            // 先充值
            setCtx(TENANT_A, 1L);
            walletService.doRecharge(createRechargeDto(mId, new BigDecimal("600.00")), TENANT_A);

            // 再消费
            WalletOpDTO dto = new WalletOpDTO();
            dto.setMemberId(mId);
            dto.setAmount(new BigDecimal("100.00"));
            dto.setOperatorId(1L);
            dto.setPayMethod("wallet");
            dto.setRemark("多租户测试消费");
            Result<Map<String, Object>> r = walletService.doConsume(dto, TENANT_A);
            assertEquals(200, r.getCode(), "消费应成功");

            Map<String, Object> row = jdbc.queryForMap(
                "SELECT tenant_id FROM crm_wallet_transaction WHERE member_id = ? AND type = 2 ORDER BY id DESC LIMIT 1",
                mId);
            assertEquals(TENANT_A.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "Tenant A 消费 → tenant_id 必须 = A");

            BigDecimal balA = jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, mId);
            assertEquals(0, new BigDecimal("500.00").compareTo(balA),
                    "Tenant A 余额应为 500 (600 - 100)");
        } finally {
            cleanupMember(mId);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // 6. Tenant B 退款 → tenant_id = B，且 crm_member.balance 同步
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("P0-MONEY-004-6: Tenant B 退款 → tenant_id = B，且 crm_member.balance 同步")
    void tenantBRefundWritesTenantB() {
        Long[] holder = new Long[1];
        createTestMember(TENANT_B, holder);
        Long mId = holder[0];

        try {
            setCtx(TENANT_B, 1L);
            walletService.doRecharge(createRechargeDto(mId, new BigDecimal("800.00")), TENANT_B);

            WalletOpDTO dto = new WalletOpDTO();
            dto.setMemberId(mId);
            dto.setAmount(new BigDecimal("200.00"));
            dto.setOperatorId(1L);
            dto.setPayMethod("原路返回");
            dto.setRemark("多租户测试退款");

            // refund 需要 orderId，先创建一个测试 pos_order
            String orderNo = "TESTMT" + System.currentTimeMillis();
            jdbc.update(
                "INSERT INTO pos_order(tenant_id, store_id, order_no, member_id, member_name, type, total_amount, final_amount, cashier_id, status) " +
                "VALUES(?, 1, ?, ?, '多租户测试会员-B', 1, 100, 100, 1, 1)",
                TENANT_B, orderNo, mId);
            Long orderId = jdbc.queryForObject(
                "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);
            dto.setOrderId(orderId);

            Result<Map<String, Object>> r = walletService.doRefund(dto, TENANT_B);
            assertEquals(200, r.getCode(), "退款应成功");

            Map<String, Object> row = jdbc.queryForMap(
                "SELECT tenant_id FROM crm_wallet_transaction WHERE member_id = ? AND type = 3 ORDER BY id DESC LIMIT 1",
                mId);
            assertEquals(TENANT_B.longValue(), ((Number) row.get("tenant_id")).longValue(),
                    "Tenant B 退款 → tenant_id 必须 = B");

            BigDecimal balB = jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, mId);
            assertEquals(0, new BigDecimal("1000.00").compareTo(balB),
                    "Tenant B 余额应为 1000 (800 + 200)");

            // SSOT 同步
            BigDecimal memberBalB = jdbc.queryForObject(
                "SELECT balance FROM crm_member WHERE id = ?",
                BigDecimal.class, mId);
            assertEquals(0, balB.compareTo(memberBalB),
                    "SSOT 同步：crm_member.balance(" + memberBalB + ") == crm_wallet.balance(" + balB + ")");
        } finally {
            cleanupMember(mId);
            TenantContextHolder.clear();
        }
    }

    // ================================================================
    // Cleanup helper
    // ================================================================
    private void cleanupMember(Long mId) {
        if (mId == null) return;
        try {
            jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", mId);
            jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", mId);
            jdbc.update("DELETE FROM pos_order WHERE member_id = ? AND order_no LIKE 'TESTMT%'", mId);
            jdbc.update("DELETE FROM crm_member WHERE id = ?", mId);
        } catch (Exception ignore) {}
    }
}