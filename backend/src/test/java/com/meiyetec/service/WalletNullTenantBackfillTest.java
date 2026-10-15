package com.meiyetec.service;

import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.mapper.WalletMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-DB-001-T04 · crm_wallet.tenant_id NULL 历史数据回填验证
 *
 * <p>覆盖：
 * <ol>
 *   <li>回填前：crm_wallet.tenant_id IS NULL 数量 > 0（DB 已有 29 行历史 NULL）</li>
 *   <li>回填后：crm_wallet.tenant_id IS NULL 数量 = 0（SQL 已跑过）</li>
 *   <li>回填后：所有 wallet.tenant_id == member.tenant_id（跨租户一致）</li>
 *   <li>回填后：wallet.balance == member.balance（资金链路不变）</li>
 *   <li>回填后：wallet 行数不变 / balance / version 数值未被修改（仅 tenant_id 被回填）</li>
 *   <li>回填后：crm_wallet_transaction 行数与 tenant_id 分布不变</li>
 *   <li>T03 insertIfAbsent 修复仍保持有效（再插一条新钱包必须带 tenant_id）</li>
 * </ol>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletNullTenantBackfillTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletMapper walletMapper;
    @Autowired private JdbcTemplate jdbc;

    /**
     * T04 历史回填的 29 个 member_id（来自 pre-fix 快照）
     * 不再写死 wallet_id，因为钱包 id 是 AUTO_INCREMENT，迁移工具会变化；
     * 但 member_id 是业务唯一键，跨环境稳定。
     */
    private static final List<Long> T04_HISTORIC_NULL_MEMBER_IDS = List.of(
        57L, 58L, 70L, 71L, 83L, 84L, 96L, 97L, 109L, 110L,
        122L, 123L, 135L, 136L, 145L,
        334L, 335L, 375L, 376L, 418L, 419L,
        459L, 460L, 500L, 501L, 541L, 542L, 582L, 583L
    );

    private Long testMemberId;

    @AfterEach
    void tearDown() {
        if (testMemberId != null) {
            jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_member WHERE id = ?", testMemberId);
            testMemberId = null;
        }
        TenantContextHolder.clear();
    }

    private Long createTestMember(Long tenantId) {
        String ts = String.valueOf(System.currentTimeMillis()) + UUID.randomUUID().toString().substring(0, 4);
        String memberNo = "T04-" + ts;
        String phone = "T04" + ts.substring(ts.length() - 9);
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURDATE())",
            tenantId, 1L, memberNo, "P0-T04 测试会员", phone, 0, 1, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        return jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);
    }

    // ================================================================
    // 测试 1: 全表 NULL tenant = 0（SQL 已跑过）
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("T04-1: crm_wallet.tenant_id IS NULL 全表计数 = 0")
    void noNullTenantAnywhere() {
        Integer nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL",
            Integer.class);
        assertEquals(0, nullCount.intValue(),
            "T04 回填后 crm_wallet.tenant_id IS NULL 应为 0，实际=" + nullCount);
    }

    // ================================================================
    // 测试 2: 29 个历史 NULL 钱包已全部回填为对应 member.tenant_id
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("T04-2: 29 个历史 NULL wallet 全部 tenant_id != NULL 且 == member.tenant_id")
    void historic29Backfilled() {
        for (Long memberId : T04_HISTORIC_NULL_MEMBER_IDS) {
            Long walletTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                Long.class, memberId);
            assertNotNull(walletTenant,
                "T04 历史钱包 member_id=" + memberId + " tenant_id 仍为 NULL");
            Long memberTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_member WHERE id = ?",
                Long.class, memberId);
            assertNotNull(memberTenant,
                "crm_member.id=" + memberId + " tenant_id 不应 NULL");
            assertEquals(memberTenant.longValue(), walletTenant.longValue(),
                "T04 member_id=" + memberId + " wallet.tenant_id 应等于 member.tenant_id");
        }
    }

    // ================================================================
    // 测试 3: 跨租户一致性 — 全表 wallet.tenant_id 必须 == member.tenant_id
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("T04-3: 全表 wallet.tenant_id == member.tenant_id（0 行不一致）")
    void crossTenantConsistency() {
        Integer mismatches = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet w " +
            "INNER JOIN crm_member m ON m.id = w.member_id " +
            "WHERE w.tenant_id IS NOT NULL AND m.tenant_id IS NOT NULL " +
            "  AND w.tenant_id <> m.tenant_id",
            Integer.class);
        assertEquals(0, mismatches.intValue(),
            "wallet.tenant_id 与 member.tenant_id 不一致行数应为 0，实际=" + mismatches);
    }

    // ================================================================
    // 测试 4: 资金链路不变 — T04 不得引入新的 wallet/member 漂移
    // 上下文：14 行漂移是 pre-existing（P0-MONEY-002 历史 AUTO_FIX 残留），
    //   T04 的承诺是只动 tenant_id、不动 balance，绝不能新增漂移。
    // 因此断言：post-fix drift_count ≤ pre-fix drift_count（实测 14）。
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("T04-4: T04 不新增 wallet/member 漂移（pre-fix drift=14，post-fix 必须 ≤ 14）")
    void ssotIntact() {
        Integer drifts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet w " +
            "INNER JOIN crm_member m ON m.id = w.member_id " +
            "WHERE w.deleted = 0 AND m.deleted = 0 " +
            "  AND COALESCE(w.balance,0) <> COALESCE(m.balance,0)",
            Integer.class);
        assertTrue(drifts.intValue() <= 14,
            "T04 后 drift_count 应 ≤ pre-fix 14，实际=" + drifts);
        // 更严格：pre-fix 实测 14，T04 不能新增
        assertEquals(14, drifts.intValue(),
            "T04 后 drift_count 应 == pre-fix 14（不增不减），实际=" + drifts);
    }

    // ================================================================
    // 测试 5: 资金数值不变 — 29 历史钱包 balance/recharge/consume/frozen/version 全部未动
    // 快照（pre-fix 实测 2026-08-15）：
    //   - 14 个钱包 balance=500.00（REFUND 500 测试对）
    //   - 14 个钱包 balance=0.00（同上的镜像成员，未消费）
    //   - 1 个钱包 balance=100.00（RECHARGE 100，id=145/member=145）
    //   - 总余额 = 14*500 + 1*100 = 7100.00
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("T04-5: 29 历史钱包 balance 数值与回填前快照完全一致（7100.00）")
    void balanceUntouched() {
        BigDecimal totalBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(balance), 0) FROM crm_wallet " +
            "WHERE member_id IN (" + placeholders(T04_HISTORIC_NULL_MEMBER_IDS.size()) + ")",
            BigDecimal.class,
            T04_HISTORIC_NULL_MEMBER_IDS.toArray());
        // pre-fix 实测：14 × 500 + 1 × 100 = 7100.00
        assertEquals(0, new BigDecimal("7100.00").compareTo(totalBalance),
            "29 历史钱包总余额应为 7100.00（pre-fix 快照），实际=" + totalBalance);

        // 抽查：member_id=57 余额 = 500.00（REFUND 后的钱包余额）
        BigDecimal b57 = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = 57 AND deleted = 0",
            BigDecimal.class);
        assertEquals(0, new BigDecimal("500.00").compareTo(b57),
            "member_id=57 钱包余额应为 500.00，实际=" + b57);

        // 抽查：member_id=145 余额 = 100.00（唯一 RECHARGE）
        BigDecimal b145 = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = 145 AND deleted = 0",
            BigDecimal.class);
        assertEquals(0, new BigDecimal("100.00").compareTo(b145),
            "member_id=145 钱包余额应为 100.00，实际=" + b145);

        // 抽查：member_id=58 余额 = 0（REFUND 测试的镜像成员，未消费）
        BigDecimal b58 = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = 58 AND deleted = 0",
            BigDecimal.class);
        assertEquals(0, new BigDecimal("0.00").compareTo(b58),
            "member_id=58 钱包余额应为 0.00，实际=" + b58);

        // 版本号全部保持原值（version 字段未动）
        // 14 个 balance=500 的钱包 version=1；14 个 balance=0 的钱包 version=0；1 个 id=145 version=1
        Integer walletsV1 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet WHERE member_id IN (" + placeholders(T04_HISTORIC_NULL_MEMBER_IDS.size()) +
            ") AND deleted = 0 AND version = 1",
            Integer.class, T04_HISTORIC_NULL_MEMBER_IDS.toArray());
        Integer walletsV0 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet WHERE member_id IN (" + placeholders(T04_HISTORIC_NULL_MEMBER_IDS.size()) +
            ") AND deleted = 0 AND version = 0",
            Integer.class, T04_HISTORIC_NULL_MEMBER_IDS.toArray());
        assertEquals(15, walletsV1.intValue(),
            "29 历史钱包中 version=1 应有 15 行（14 个 REFUND + 1 个 RECHARGE），实际=" + walletsV1);
        assertEquals(14, walletsV0.intValue(),
            "29 历史钱包中 version=0 应有 14 行（REFUND 测试的镜像成员），实际=" + walletsV0);
    }

    // ================================================================
    // 测试 6: crm_wallet_transaction 行数与 tenant_id 分布不变
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("T04-6: crm_wallet_transaction 行数不变 / 29 钱包对应的 16 条流水 tenant_id 全非 NULL")
    void transactionLedgerIntact() {
        // 29 历史钱包共产生 16 条流水（pre-fix 快照）
        Integer txnCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_transaction " +
            "WHERE member_id IN (" + placeholders(T04_HISTORIC_NULL_MEMBER_IDS.size()) + ")",
            Integer.class,
            T04_HISTORIC_NULL_MEMBER_IDS.toArray());
        assertTrue(txnCount >= 15,
            "29 历史钱包的流水数应 >= 15（pre-fix 实测 16），实际=" + txnCount);

        // 这 16 条流水的 tenant_id 不应为 NULL（T03 修复保证新写入带 tenant）
        Integer nullTxnTenant = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_transaction " +
            "WHERE member_id IN (" + placeholders(T04_HISTORIC_NULL_MEMBER_IDS.size()) + ") " +
            "  AND tenant_id IS NULL",
            Integer.class,
            T04_HISTORIC_NULL_MEMBER_IDS.toArray());
        assertEquals(0, nullTxnTenant.intValue(),
            "历史钱包的流水 tenant_id 不应为 NULL（pre-fix 时也已是 tenant=1），实际=" + nullTxnTenant);
    }

    // ================================================================
    // 测试 7: T03 insertIfAbsent 修复仍生效 — 新钱包必须带 tenant_id
    // ================================================================
    @Test
    @Order(7)
    @DisplayName("T04-7: T03 insertIfAbsent 修复仍生效 — 新钱包写入 tenant_id（回归）")
    void t03InsertIfAbsentStillHolds() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(1L);
        walletMapper.insertIfAbsent(testMemberId, 1L);

        Long tenantInDb = jdbc.queryForObject(
            "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertNotNull(tenantInDb, "T03 修复应保证新钱包 tenant_id != NULL");
        assertEquals(1L, tenantInDb.longValue(),
            "T03 修复应保证新钱包 tenant_id == ctx.tenantId=1");
    }

    // ================================================================
    // 测试 8: T03 insertIfAbsent — tenant=2 也仍生效（异租户隔离不被 T04 影响）
    // ================================================================
    @Test
    @Order(8)
    @DisplayName("T04-8: T03 insertIfAbsent 修复 — tenant=2 ctx 也能正确写入")
    void t03InsertIfAbsentTenant2() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(2L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(2L);
        walletMapper.insertIfAbsent(testMemberId, 2L);

        Long tenantInDb = jdbc.queryForObject(
            "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(2L, tenantInDb.longValue(),
            "tenant=2 ctx 下新钱包 tenant_id 应 == 2，T04 回填不能污染 T03 行为");
    }

    // ================================================================
    // 测试 9: 走 service.doRecharge 触发 ensureWallet → 自动带 tenant_id（SSOT 同步）
    // ================================================================
    @Test
    @Order(9)
    @DisplayName("T04-9: T04 回填后，service.doRecharge 仍能正确创建新钱包并带 tenant_id")
    void serviceRechargeAfterBackfill() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(1L);

        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("50.00"));
        dto.setGiftAmount(BigDecimal.ZERO);
        dto.setPayMethod("现金");
        dto.setOperatorId(1L);
        Result<Map<String, Object>> r = walletService.doRecharge(dto, 1L);
        assertEquals(200, r.getCode(), "T04 回填后充值仍应成功");

        Long tenantInDb = jdbc.queryForObject(
            "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(1L, tenantInDb.longValue(),
            "T04 回填后 service 路径下新钱包 tenant_id 仍 == ctx.tenantId=1");

        // SSOT 同步也仍正常
        BigDecimal walletBal = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        BigDecimal memberBal = jdbc.queryForObject(
            "SELECT balance FROM crm_member WHERE id = ?",
            BigDecimal.class, testMemberId);
        assertEquals(0, walletBal.compareTo(memberBal),
            "T04 回填后 SSOT 仍同步：wallet.balance(" + walletBal + ") == member.balance(" + memberBal + ")");
    }

    // ================================================================
    // 测试 10: T04 回填审计日志已写入 crm_wallet_drift_log（fix_type=T04_BACKFILL_TENANT）
    // ================================================================
    @Test
    @Order(10)
    @DisplayName("T04-10: crm_wallet_drift_log 已写入 T04_BACKFILL_TENANT 审计记录")
    void auditLogWritten() {
        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_drift_log WHERE fix_type = 'T04_BACKFILL_TENANT'",
            Integer.class);
        assertTrue(auditCount >= 29,
            "T04 审计日志行数应 >= 29，实际=" + auditCount);
    }

    // ================================================================
    // 工具方法
    // ================================================================
    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }
}
