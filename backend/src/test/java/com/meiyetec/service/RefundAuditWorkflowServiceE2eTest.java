package com.meiyetec.service;

import com.meiyetec.entity.PosRefundAuditLog;
import com.meiyetec.enums.RefundStatus;
import com.meiyetec.mapper.PosRefundAuditLogMapper;
import com.meiyetec.mapper.PosRefundMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-14 退款审计 e2e 测试
 *
 * <p>使用真实数据库 (@SpringBootTest) 验证 RefundAuditWorkflowService 完整工作流。
 * 覆盖 ≥ 5 个案例：
 * <ol>
 *   <li>金额 100 元 → 门店终审（1 级链）</li>
 *   <li>金额 800 元 → 门店 → 总部终审（2 级链）</li>
 *   <li>金额 6000 元 → 门店 → 总部 → 财务终审（3 级链）</li>
 *   <li>驳回后重提：800 元被驳回 → 重新提交 → 门店终审</li>
 *   <li>SLA 超时：超 24h 待审项正确出现在超时列表</li>
 * </ol>
 *
 * <p>每个测试方法在独立事务中运行，结束后自动回滚，保证测试隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class RefundAuditWorkflowServiceE2eTest {

    private static final Long TENANT_ID = 1L;
    private static final Long STORE_ID = 1L;
    private static final Long OPERATOR_ID = 1L;
    private static final String OPERATOR_NAME = "e2e-tester";

    // ============================================================
    // Autowired
    // ============================================================

    @Autowired
    private RefundAuditWorkflowService workflowService;

    @Autowired
    private PosRefundMapper refundMapper;

    @Autowired
    private PosRefundAuditLogMapper auditLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ============================================================
    // Lifecycle
    // ============================================================

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TENANT_ID);
        ctx.setStoreId(STORE_ID);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ============================================================
    // Case 1: 金额 100 元 → 门店终审（1 级链）
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("金额 100 元：门店一次终审 → APPROVED，process() 可受理")
    void e2e_amount100_storeTerminal() {
        BigDecimal amount = new BigDecimal("100");

        // 1) 创建退款单（auditStatus = PENDING_STORE，status = AUDIT）
        Long refundId = createRefund(amount, "PENDING_STORE", "AUDIT");

        // 2) 门店审核（≤ 500 → 直接 APPROVED）
        var r1 = workflowService.auditByStore(refundId, 10L, "店长A", "STORE", null);
        assertTrue(r1.isSuccess(), "门店审核应成功");
        assertEquals("APPROVED", r1.getData().getAuditStatus(),
                "≤500 门店终审 → auditStatus = APPROVED");
        assertEquals(RefundStatus.APPROVED.name(), r1.getData().getStatus(),
                "overall status = APPROVED → process() 可受理");

        // 3) 断言审核流水写入（STORE_APPROVE 已写入）
        var logs = workflowService.timeline(refundId).getData();
        assertNotNull(logs);
        assertTrue(logs.size() >= 1, "至少 1 条流水：STORE_APPROVE（门店终审）");
        assertEquals("STORE_APPROVE", logs.get(logs.size() - 1).getAction());
    }

    // ============================================================
    // Case 2: 金额 800 元 → 门店 → 总部终审（2 级链）
    // ============================================================

    @Test
    @Order(2)
    @DisplayName("金额 800 元：门店通过 → PENDING_HQ → 总部终审 APPROVED（2 级链）")
    void e2e_amount800_twoLevel() {
        BigDecimal amount = new BigDecimal("800");
        // 800 > 500 → 需 2 级（store + HQ）
        // 800 ≤ 5000 → HQ 终审，不进财务

        Long refundId = createRefund(amount, "PENDING_STORE", "AUDIT");

        // 1) 门店审核 → PENDING_HQ
        var r1 = workflowService.auditByStore(refundId, 20L, "店长B", "STORE", null);
        assertEquals("PENDING_HQ", r1.getData().getAuditStatus(),
                "800 > 500，门店通过后进入 PENDING_HQ");
        assertEquals(RefundStatus.AUDIT.name(), r1.getData().getStatus(),
                "整体状态保持 AUDIT（未终审）");

        // 2) 总部审核 → APPROVED（800 ≤ 5000，HQ 终审）
        var r2 = workflowService.auditByHq(refundId, 30L, "总部B", "HQ", null);
        assertEquals("APPROVED", r2.getData().getAuditStatus(),
                "800 ≤ 5000，HQ 终审 → APPROVED（无需进财务）");
        assertEquals(RefundStatus.APPROVED.name(), r2.getData().getStatus());

        // 3) 断言流水：STORE_APPROVE + HQ_APPROVE
        var logs = workflowService.timeline(refundId).getData();
        assertNotNull(logs);
        assertEquals(2, logs.size(), "完整 2 级链应写入 2 条流水：STORE_APPROVE + HQ_APPROVE");
        assertEquals("STORE_APPROVE", logs.get(0).getAction());
        assertEquals("HQ_APPROVE",    logs.get(1).getAction());
    }

    // ============================================================
    // Case 3: 金额 6000 元 → 门店 → 总部 → 财务终审（3 级链）
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("金额 6000 元：门店→总部→财务，完整 3 级链 → APPROVED")
    void e2e_amount6000_threeLevel() {
        BigDecimal amount = new BigDecimal("6000");
        // 6000 > 5000 → 需 3 级（store + HQ + finance）

        Long refundId = createRefund(amount, "PENDING_STORE", "AUDIT");

        // 1) 门店审核 → PENDING_HQ
        var r1 = workflowService.auditByStore(refundId, 40L, "店长C", "STORE", null);
        assertEquals("PENDING_HQ", r1.getData().getAuditStatus());
        assertEquals(2, r1.getData().getCurrentAuditLevel());

        // 2) 总部审核 → PENDING_FINANCE（6000 > 5000，需进财务）
        var r2 = workflowService.auditByHq(refundId, 50L, "总部C", "HQ", null);
        assertEquals("PENDING_FINANCE", r2.getData().getAuditStatus(),
                "6000 > 5000，HQ 通过后进入 PENDING_FINANCE");
        assertEquals(3, r2.getData().getCurrentAuditLevel());

        // 3) 财务终审 → APPROVED
        var r3 = workflowService.auditByFinance(refundId, 60L, "财务C", "FINANCE", null);
        assertEquals("APPROVED", r3.getData().getAuditStatus(),
                "财务终审 → APPROVED");
        assertEquals(RefundStatus.APPROVED.name(), r3.getData().getStatus());

        // 4) 断言流水：STORE_APPROVE + HQ_APPROVE + FINANCE_APPROVE = 3
        var logs = workflowService.timeline(refundId).getData();
        assertNotNull(logs);
        assertEquals(3, logs.size(), "完整 3 级链应写入 3 条流水：STORE + HQ + FINANCE");
        assertEquals("STORE_APPROVE",    logs.get(0).getAction());
        assertEquals("HQ_APPROVE",       logs.get(1).getAction());
        assertEquals("FINANCE_APPROVE",  logs.get(2).getAction());

        // 5) 断言审核时间轴记录了每个角色的 actorId
        assertEquals(40L, logs.get(0).getActorId(), "STORE_APPROVE actorId = 店长C");
        assertEquals(50L, logs.get(1).getActorId(), "HQ_APPROVE actorId = 总部C");
        assertEquals(60L, logs.get(2).getActorId(), "FINANCE_APPROVE actorId = 财务C");
    }

    // ============================================================
    // Case 4: 驳回后重提（800 元被驳回 → resubmit → 门店终审）
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("800 元被驳回后重提：resubmit → 重新进入 PENDING_STORE → 门店终审 APPROVED")
    void e2e_rejectAndResubmit() {
        BigDecimal originalAmount = new BigDecimal("800");

        // 1) 创建退款单，进入 PENDING_STORE
        Long refundId = createRefund(originalAmount, "PENDING_STORE", "AUDIT");

        // 2) 门店审核通过 → PENDING_HQ
        workflowService.auditByStore(refundId, 70L, "店长D", "STORE", null);

        // 3) 总部驳回
        var rejectR = workflowService.reject(refundId, 80L, "总部D", "HQ", "材料不全");
        assertEquals("REJECTED", rejectR.getData().getAuditStatus());
        assertEquals("材料不全", rejectR.getData().getRejectReason());

        // 4) 重提（保持金额 800）
        var resubmitR = workflowService.resubmit(refundId, OPERATOR_ID, OPERATOR_NAME,
                new BigDecimal("800"), "补充材料后重新提交");
        assertEquals("PENDING_STORE", resubmitR.getData().getAuditStatus(),
                "重提后重新进入 PENDING_STORE");
        assertNull(resubmitR.getData().getRejectReason(), "驳回原因应清空");

        // 5) 门店终审 → APPROVED
        var finalR = workflowService.auditByStore(refundId, 70L, "店长D", "STORE", null);
        assertEquals("APPROVED", finalR.getData().getAuditStatus());
        assertEquals(RefundStatus.APPROVED.name(), finalR.getData().getStatus());

        // 6) 断言 RESUBMIT 流水写入
        var logs = workflowService.timeline(refundId).getData();
        assertNotNull(logs);
        var resubmitLog = logs.stream()
                .filter(l -> "RESUBMIT".equals(l.getAction()))
                .findFirst()
                .orElse(null);
        assertNotNull(resubmitLog, "应写入 RESUBMIT 流水");
        assertEquals("补充材料后重新提交", resubmitLog.getRemark());
    }

    // ============================================================
    // Case 5: SLA 超时（> 24h）
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("SLA 超时：apply_time 超过 24h 的 PENDING_STORE 项 slaExceeded=true")
    void e2e_slaTimeout() {
        BigDecimal amount = new BigDecimal("800");
        Long refundId = createRefund(amount, "PENDING_STORE", "AUDIT");

        // 手工把 apply_time 往前推 25 小时（模拟超时场景）
        LocalDateTime oldApplyTime = LocalDateTime.now().minusHours(25);
        jdbc.update("UPDATE pos_refund SET apply_time = ? WHERE id = ?", oldApplyTime, refundId);

        // 从 DB 重新查待审列表
        var pending = workflowService.pendingByPhase("PENDING_STORE", null).getData();
        assertNotNull(pending);

        var overdue = pending.stream()
                .filter(m -> Long.valueOf(refundId).equals(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("待审列表中未找到退款单 " + refundId));

        assertEquals(refundId, overdue.get("id"));
        assertTrue((Boolean) overdue.get("slaExceeded"),
                "apply_time 已超 25h，slaExceeded 应为 true");
        long hoursSinceApply = (Long) overdue.get("hoursSinceApply");
        assertTrue(hoursSinceApply >= 24, "hoursSinceApply 应 ≥ 24，实际=" + hoursSinceApply);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 在数据库中插入一条真实的退款单（用于 e2e 测试）。
     *
     * @param amount      退款金额
     * @param auditStatus 审核相位（初始值）
     * @param status      整体状态（初始值）
     * @return 退款单主键
     */
    private Long createRefund(BigDecimal amount, String auditStatus, String status) {
        String refundNo = "RF-E2E-" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        // 计算 needed auditLevel
        int neededLevel = amount.compareTo(new BigDecimal("500")) > 0
                ? (amount.compareTo(new BigDecimal("5000")) > 0 ? 3 : 2)
                : 1;

        jdbc.update("""
            INSERT INTO pos_refund
              (tenant_id, store_id, refund_no, refund_type, refund_amount,
               paid_amount, refunded_amount, remaining_amount,
               refund_strategy, status, audit_status, current_audit_level,
               audit_required, reason, operator_id, operator_name,
               apply_time, create_time, update_time, version, deleted)
            VALUES
              (?, ?, ?, 'FULL', ?,
               ?, 0, ?,
               'ORIGINAL', ?, ?, ?,
               1, 'e2e 测试退款', ?, ?,
               ?, ?, ?, 0, 0)
            """,
                TENANT_ID, STORE_ID, refundNo, amount,
                amount, amount,
                status, auditStatus, neededLevel,
                OPERATOR_ID, OPERATOR_NAME,
                now, now, now);

        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 注意：不写 SUBMIT 流水 —— 由 workflowService 的 resubmit / auditByStore 等方法写入，
        // 真实模拟业务入口（createRefund 只是测试辅助，不模拟完整创建流程）
        return id;
    }
}
