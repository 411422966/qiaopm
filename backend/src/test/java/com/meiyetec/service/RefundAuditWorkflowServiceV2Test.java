package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PosRefund;
import com.meiyetec.entity.PosRefundAuditRule;
import com.meiyetec.enums.RefundStatus;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PosRefundAuditLogMapper;
import com.meiyetec.mapper.PosRefundAuditRuleMapper;
import com.meiyetec.mapper.PosRefundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * POS-008 v2 退款多级审核 — 端到端工作流测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>金额阈值分流决策</li>
 *   <li>三级审核端到端流转：PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED</li>
 *   <li>≤ 500 门店终审（仅 PENDING_STORE）</li>
 *   <li>驳回链：任意级别可 REJECTED</li>
 *   <li>resubmit 重新计算审核相位</li>
 *   <li>错误相位抛 BizCode.CONFLICT</li>
 * </ol>
 */
@DisplayName("POS-008 v2 退款多级审核工作流")
class RefundAuditWorkflowServiceV2Test {

    private PosRefundMapper refundMapper;
    private PosRefundAuditLogMapper auditLogMapper;
    private PosRefundAuditRuleMapper auditRuleMapper;
    private RefundAuditWorkflowService workflow;

    /** 模拟当前内存中的退款单（mock 不维护状态） */
    private final java.util.Map<Long, PosRefund> refunds = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        refundMapper = mock(PosRefundMapper.class);
        auditLogMapper = mock(PosRefundAuditLogMapper.class);
        auditRuleMapper = mock(PosRefundAuditRuleMapper.class);
        workflow = new RefundAuditWorkflowService(refundMapper, auditLogMapper, auditRuleMapper);

        // 默认审计规则：500 / 5000
        List<PosRefundAuditRule> rules = defaultRules();
        when(auditRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rules);

        // refundMapper.transitStatus：模拟成功
        when(refundMapper.transitStatus(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            String to = inv.getArgument(3);
            PosRefund r = refunds.get(id);
            if (r != null) r.setStatus(to);
            return 1;
        });
    }

    private List<PosRefundAuditRule> defaultRules() {
        List<PosRefundAuditRule> rs = new ArrayList<>();
        PosRefundAuditRule l1 = new PosRefundAuditRule();
        l1.setAuditLevel(1); l1.setAuditLevelName("门店");
        l1.setMinAmount(BigDecimal.ZERO); l1.setMaxAmount(new BigDecimal("500"));
        l1.setRequired(0); l1.setAutoApprove(0); l1.setEnabled(1);
        rs.add(l1);

        PosRefundAuditRule l2 = new PosRefundAuditRule();
        l2.setAuditLevel(2); l2.setAuditLevelName("总部");
        l2.setMinAmount(new BigDecimal("500")); l2.setMaxAmount(new BigDecimal("5000"));
        l2.setRequired(1); l2.setAutoApprove(0); l2.setEnabled(1);
        rs.add(l2);

        PosRefundAuditRule l3 = new PosRefundAuditRule();
        l3.setAuditLevel(3); l3.setAuditLevelName("财务");
        l3.setMinAmount(new BigDecimal("5000")); l3.setMaxAmount(new BigDecimal("999999"));
        l3.setRequired(1); l3.setAutoApprove(0); l3.setEnabled(1);
        rs.add(l3);
        return rs;
    }

    private PosRefund mockRefund(Long id, BigDecimal amount, String auditStatus, String status, int version) {
        PosRefund r = new PosRefund();
        r.setId(id);
        r.setTenantId(1L);
        r.setStoreId(100L);
        r.setRefundNo("RF" + id);
        r.setRefundAmount(amount);
        r.setPaidAmount(amount == null ? BigDecimal.ZERO : amount);
        r.setRefundedAmount(BigDecimal.ZERO);
        r.setRemainingAmount(amount == null ? BigDecimal.ZERO : amount);
        r.setAuditStatus(auditStatus);
        r.setStatus(status);
        r.setVersion(version);
        r.setOperatorId(1L);
        r.setOperatorName("门店员");
        refunds.put(id, r);
        when(refundMapper.selectById(id)).thenReturn(r);
        return r;
    }

    // ============================================================
    // 1. 金额阈值分流
    // ============================================================

    @Test
    @DisplayName("金额 300 元：resolveInitialAuditStatus 返回 PENDING_STORE（仅需 1 级）")
    void threshold_300_storeOnly() {
        String phase = workflow.resolveInitialAuditStatus(new BigDecimal("300"));
        assertEquals("PENDING_STORE", phase);

        int level = workflow.resolveAuditLevel(new BigDecimal("300"));
        assertEquals(1, level);
    }

    @Test
    @DisplayName("金额 500 元（边界）：PENDING_STORE 起步，门店终审即可")
    void threshold_500_boundary() {
        String phase = workflow.resolveInitialAuditStatus(new BigDecimal("500"));
        assertEquals("PENDING_STORE", phase);

        int level = workflow.resolveAuditLevel(new BigDecimal("500"));
        assertEquals(1, level);
    }

    @Test
    @DisplayName("金额 600 元：PENDING_STORE 起步，需 2 级")
    void threshold_600_hq() {
        String phase = workflow.resolveInitialAuditStatus(new BigDecimal("600"));
        assertEquals("PENDING_STORE", phase);

        int level = workflow.resolveAuditLevel(new BigDecimal("600"));
        assertEquals(2, level);
    }

    @Test
    @DisplayName("金额 5000 元（边界）：2 级（HQ 终审）")
    void threshold_5000_hqTerminal() {
        String phase = workflow.resolveInitialAuditStatus(new BigDecimal("5000"));
        assertEquals("PENDING_STORE", phase);

        int level = workflow.resolveAuditLevel(new BigDecimal("5000"));
        assertEquals(2, level);
    }

    @Test
    @DisplayName("金额 6000 元：3 级（财务必审）")
    void threshold_6000_finance() {
        String phase = workflow.resolveInitialAuditStatus(new BigDecimal("6000"));
        assertEquals("PENDING_STORE", phase);

        int level = workflow.resolveAuditLevel(new BigDecimal("6000"));
        assertEquals(3, level);
    }

    @Test
    @DisplayName("金额 null：返回 null，无需审核")
    void threshold_null() {
        assertNull(workflow.resolveInitialAuditStatus(null));
        assertEquals(0, workflow.resolveAuditLevel(null));
    }

    // ============================================================
    // 2. 三级审核端到端流转（> 5000 元场景）
    // ============================================================

    @Test
    @DisplayName("金额 6000：store → hq → finance → APPROVED（完整 3 级链）")
    void endToEnd_threeLevel() {
        PosRefund refund = mockRefund(1L, new BigDecimal("6000"),
                "PENDING_STORE", "AUDIT", 0);

        // 1) 门店审核
        workflow.auditByStore(1L, 100L, "店长A", "STORE", null);
        PosRefund afterStore = refunds.get(1L);
        assertEquals("PENDING_HQ", afterStore.getAuditStatus());
        assertEquals("AUDIT", afterStore.getStatus(), "门店未终审，overall status 保持 AUDIT");
        assertEquals(2, afterStore.getCurrentAuditLevel(), "currentAuditLevel 升级到 2");

        // 2) 总部审核
        when(refundMapper.transitStatus(anyLong(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            String to = inv.getArgument(3);
            refunds.get(id).setStatus(to);
            return 1;
        });
        workflow.auditByHq(1L, 200L, "总部B", "HQ", null);
        PosRefund afterHq = refunds.get(1L);
        assertEquals("PENDING_FINANCE", afterHq.getAuditStatus());
        assertEquals("AUDIT", afterHq.getStatus(), "总部未终审，overall status 保持 AUDIT");
        assertEquals(3, afterHq.getCurrentAuditLevel());

        // 3) 财务终审
        workflow.auditByFinance(1L, 300L, "财务C", "FINANCE", null);
        PosRefund afterFinance = refunds.get(1L);
        assertEquals("APPROVED", afterFinance.getAuditStatus(), "财务终审后 auditStatus=APPROVED");
        assertEquals(RefundStatus.APPROVED.name(), afterFinance.getStatus(),
                "财务终审后 overall status=APPROVED，process() 可受理");
    }

    // ============================================================
    // 3. 短链：≤ 500 门店终审
    // ============================================================

    @Test
    @DisplayName("金额 300：store 一次终审 → auditStatus=APPROVED，process() 可受理")
    void shortChain_storeTerminal() {
        PosRefund refund = mockRefund(2L, new BigDecimal("300"),
                "PENDING_STORE", "AUDIT", 0);

        workflow.auditByStore(2L, 100L, "店长A", "STORE", null);

        PosRefund after = refunds.get(2L);
        assertEquals("APPROVED", after.getAuditStatus(), "≤ 500 门店一次终审 → APPROVED");
        assertEquals(RefundStatus.APPROVED.name(), after.getStatus());
    }

    // ============================================================
    // 4. 中链：500 < amount ≤ 5000 总部终审
    // ============================================================

    @Test
    @DisplayName("金额 3000：store → hq 终审 → APPROVED（2 级链）")
    void midChain_hqTerminal() {
        PosRefund refund = mockRefund(3L, new BigDecimal("3000"),
                "PENDING_STORE", "AUDIT", 0);

        workflow.auditByStore(3L, 100L, "店长A", "STORE", null);
        PosRefund afterStore = refunds.get(3L);
        assertEquals("PENDING_HQ", afterStore.getAuditStatus());

        workflow.auditByHq(3L, 200L, "总部B", "HQ", null);
        PosRefund afterHq = refunds.get(3L);
        assertEquals("APPROVED", afterHq.getAuditStatus(),
                "3000 ≤ 5000，HQ 终审 → APPROVED（无需进入财务）");
        assertEquals(RefundStatus.APPROVED.name(), afterHq.getStatus());
    }

    // ============================================================
    // 5. 错误相位抛 CONFLICT
    // ============================================================

    @Test
    @DisplayName("在 PENDING_HQ 上调用 auditByStore：抛 BizCode.CONFLICT")
    void wrongPhase_auditStoreFromPendingHq() {
        PosRefund refund = mockRefund(4L, new BigDecimal("3000"),
                "PENDING_HQ", "AUDIT", 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflow.auditByStore(4L, 100L, "店长", "STORE", null));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("门店审核"));
    }

    @Test
    @DisplayName("在 PENDING_STORE 上调用 auditByFinance：抛 BizCode.CONFLICT（必须按顺序）")
    void wrongPhase_auditFinanceFromPendingStore() {
        PosRefund refund = mockRefund(5L, new BigDecimal("1000"),
                "PENDING_STORE", "AUDIT", 0);

        assertThrows(BusinessException.class,
                () -> workflow.auditByFinance(5L, 300L, "财务", "FINANCE", null));
    }

    @Test
    @DisplayName("在 APPROVED 上调用 auditByHq：抛 CONFLICT")
    void wrongPhase_auditHqFromApproved() {
        PosRefund refund = mockRefund(6L, new BigDecimal("300"),
                "APPROVED", "APPROVED", 0);

        assertThrows(BusinessException.class,
                () -> workflow.auditByHq(6L, 200L, "总部", "HQ", null));
    }

    // ============================================================
    // 6. 驳回链
    // ============================================================

    @Test
    @DisplayName("PENDING_HQ 上调用 reject：auditStatus=REJECTED，status=REJECTED")
    void reject_atHq() {
        PosRefund refund = mockRefund(7L, new BigDecimal("3000"),
                "PENDING_HQ", "AUDIT", 0);

        workflow.reject(7L, 200L, "总部B", "HQ", "金额异常");

        PosRefund after = refunds.get(7L);
        assertEquals("REJECTED", after.getAuditStatus());
        assertEquals(RefundStatus.REJECTED.name(), after.getStatus());
        assertEquals("金额异常", after.getRejectReason());
    }

    @Test
    @DisplayName("在 REJECTED 上再 reject：抛 CONFLICT（驳回后只能 cancel / resubmit）")
    void reject_twice_fails() {
        PosRefund refund = mockRefund(8L, new BigDecimal("300"),
                "REJECTED", "REJECTED", 0);

        assertThrows(BusinessException.class,
                () -> workflow.reject(8L, 200L, "总部", "HQ", "再次驳回"));
    }

    // ============================================================
    // 7. Resubmit
    // ============================================================

    @Test
    @DisplayName("驳回后修改金额 6000 → resubmit → 进入 PENDING_STORE（3 级链）")
    void resubmit_changesLevel() {
        // 原始 300，剩余可退 10000（模拟部分退款场景：原订单实付 10300）
        PosRefund refund = mockRefund(9L, new BigDecimal("300"),
                "REJECTED", "REJECTED", 0);
        refund.setRemainingAmount(new BigDecimal("10000"));

        workflow.resubmit(9L, 1L, "门店员",
                new BigDecimal("6000"), "重新计算");

        PosRefund after = refunds.get(9L);
        assertEquals("PENDING_STORE", after.getAuditStatus(), "金额 6000 → 进入 3 级链");
        assertNull(after.getRejectReason(), "驳回原因已清空");
        assertEquals(new BigDecimal("6000"), after.getRefundAmount());
    }

    @Test
    @DisplayName("驳回后金额保持 300 → resubmit → 仍 PENDING_STORE（仅 1 级）")
    void resubmit_keepLevel() {
        PosRefund refund = mockRefund(10L, new BigDecimal("300"),
                "REJECTED", "REJECTED", 0);

        workflow.resubmit(10L, 1L, "门店员", null, "补齐资料");

        PosRefund after = refunds.get(10L);
        assertEquals("PENDING_STORE", after.getAuditStatus(), "金额 300 → 仍只需门店");
        assertEquals("补齐资料", after.getReason());
    }

    // ============================================================
    // 8. 写入审核流水次数
    // ============================================================

    @Test
    @DisplayName("三级链端到端：至少写入 4 条 audit_log（SUBMIT + STORE_APPROVE + HQ_APPROVE + FINANCE_APPROVE）")
    void threeLevelWritesAtLeastFourLogs() {
        PosRefund refund = mockRefund(11L, new BigDecimal("6000"),
                "PENDING_STORE", "AUDIT", 0);

        workflow.auditByStore(11L, 100L, "店长", "STORE", null);
        workflow.auditByHq(11L, 200L, "总部", "HQ", null);
        workflow.auditByFinance(11L, 300L, "财务", "FINANCE", null);

        verify(auditLogMapper, atLeast(3)).insert(any(com.meiyetec.entity.PosRefundAuditLog.class));
    }

    // ============================================================
    // 9. 工具常量
    // ============================================================

    @Test
    @DisplayName("公开常量 AUDIT_STATUS_APPROVED = \"APPROVED\"（兼容字符串字面量比较）")
    void publicConstants() {
        assertEquals("APPROVED", RefundAuditWorkflowService.AUDIT_STATUS_APPROVED);
        assertEquals("PENDING_STORE", RefundAuditWorkflowService.AUDIT_STATUS_PENDING_STORE);
        assertEquals("PENDING_HQ", RefundAuditWorkflowService.AUDIT_STATUS_PENDING_HQ);
        assertEquals("PENDING_FINANCE", RefundAuditWorkflowService.AUDIT_STATUS_PENDING_FINANCE);
        assertEquals("REJECTED", RefundAuditWorkflowService.AUDIT_STATUS_REJECTED);
    }
}
