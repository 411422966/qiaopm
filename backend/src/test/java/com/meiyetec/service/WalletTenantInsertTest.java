package com.meiyetec.service;

import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Wallet;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.WalletMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-T03 · WalletMapper.insertIfAbsent 写入 tenant_id 验证
 *
 * <p>覆盖：
 * <ul>
 *   <li>WalletMapper.insertIfAbsent(memberId, tenantId) 直接调用 → DB 写入 tenant_id</li>
 *   <li>走 WalletService.doRecharge 触发 ensureWallet → 新钱包 tenant_id = ctx.tenantId</li>
 *   <li>ctx=null 时 ensureWallet 抛 BusinessException（禁止 fallback）</li>
 *   <li>不同 ctx.tenantId（tenant=2）能正确写入新钱包</li>
 *   <li>重复插入同一 memberId 是幂等的（第二次 NOOP）</li>
 * </ul>
 */
@SpringBootTest
class WalletTenantInsertTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletMapper walletMapper;
    @Autowired private JdbcTemplate jdbc;

    private Long testMemberId;

    @AfterEach
    void tearDown() {
        if (testMemberId != null) {
            jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM pos_order WHERE order_no LIKE 'T03-%' AND member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_member WHERE id = ?", testMemberId);
            testMemberId = null;
        }
        TenantContextHolder.clear();
    }

    private Long createTestMember(Long tenantId) {
        String ts = String.valueOf(System.currentTimeMillis()) + UUID.randomUUID().toString().substring(0, 4);
        String memberNo = "T03-" + ts;
        String phone = "T03" + ts.substring(ts.length() - 9);
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURDATE())",
            tenantId, 1L, memberNo, "P0-T03 测试会员", phone, 0, 1, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        return jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);
    }

    @Test
    @DisplayName("T03-1: WalletMapper.insertIfAbsent(memberId, tenantId) 直接写入 tenant_id")
    void mapper_insert_writes_tenant_id() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(1L);

        int rows = walletMapper.insertIfAbsent(testMemberId, 1L);
        // INSERT IF NOT EXISTS (MySQL 写法) 在已存在时返回 0；新行返回 1
        assertTrue(rows >= 0, "insertIfAbsent 应返回非负数，实际=" + rows);

        Long tenantInDb = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertNotNull(tenantInDb, "新钱包 tenant_id 不应为 null");
        assertEquals(1L, tenantInDb.longValue(), "新钱包 tenant_id 应等于传入值 1");
    }

    @Test
    @DisplayName("T03-2: 不同 ctx.tenantId → 新钱包 tenant_id = ctx 值")
    void different_tenant_writes_correctly() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(2L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(2L);

        walletMapper.insertIfAbsent(testMemberId, 2L);

        Long tenantInDb = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(2L, tenantInDb.longValue(),
                "新钱包 tenant_id 应等于 ctx.tenantId=2，实际=" + tenantInDb);
    }

    @Test
    @DisplayName("T03-3: 走 WalletService.doRecharge 触发 ensureWallet → 自动带 tenant_id")
    void service_path_writes_tenant_id() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(1L);

        // 充值会走 ensureWallet → insertIfAbsent(memberId, ctx.tenantId=1)
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setGiftAmount(BigDecimal.ZERO);
        dto.setPayMethod("现金");
        dto.setOperatorId(1L);
        Result<Map<String, Object>> r = walletService.doRecharge(dto, 1L);
        assertEquals(200, r.getCode());

        Long tenantInDb = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(1L, tenantInDb.longValue(),
                "service 路径下新钱包 tenant_id 应来自 ctx=1");
    }

    @Test
    @DisplayName("T03-4: ctx 为 null 时 ensureWallet 抛 BusinessException（禁止 fallback）")
    void no_ctx_throws_exception() {
        // 不设 ctx → TenantContextHolder.getTenantIdOrNull() 返回 null
        TenantContextHolder.clear();

        testMemberId = createTestMember(1L);

        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setGiftAmount(BigDecimal.ZERO);
        dto.setPayMethod("现金");
        dto.setOperatorId(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.doRecharge(dto, 1L),
                "ctx=null 时应抛 BusinessException（不静默 fallback）");
        assertEquals(BizCode.BAD_REQUEST.code, ex.getCode(),
                "应抛 BAD_REQUEST（400）而非 SYSTEM_ERROR 或 CONFLICT");
        assertTrue(ex.getMessage().contains("tenantId"),
                "异常消息应说明缺失 tenantId，实际=" + ex.getMessage());

        // 关键：DB 中不应有这条钱包（ensureWallet 在 INSERT 前已抛错）
        Integer walletCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_wallet WHERE member_id = ?", Integer.class, testMemberId);
        assertEquals(0, walletCount.intValue(),
                "ctx=null 时不应创建钱包（ensureWallet 应先抛错）");
    }

    @Test
    @DisplayName("T03-5: 重复调用 insertIfAbsent 同 memberId 是幂等的（不会覆盖 tenant_id）")
    void insert_if_absent_is_idempotent() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        testMemberId = createTestMember(1L);

        walletMapper.insertIfAbsent(testMemberId, 1L);
        Long firstTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(1L, firstTenant.longValue());

        // 第二次：理论上不应再插（NOT EXISTS 子句）
        walletMapper.insertIfAbsent(testMemberId, 1L);
        Long secondTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM crm_wallet WHERE member_id = ?", Long.class, testMemberId);
        assertEquals(1L, secondTenant.longValue(), "幂等：tenant_id 应保持 1");

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_wallet WHERE member_id = ?", Integer.class, testMemberId);
        assertEquals(1, rowCount.intValue(), "同一 memberId 只应有 1 行钱包");
    }
}
