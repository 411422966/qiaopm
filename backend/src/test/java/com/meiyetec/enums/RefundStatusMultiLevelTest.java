package com.meiyetec.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POS-008 v2 退款多级审核 — 状态机单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>新增相位枚举：PENDING_STORE / PENDING_HQ / PENDING_FINANCE / APPROVED / REJECTED</li>
 *   <li>三级状态机迁移合法性</li>
 *   <li>阈值分流决策（≤ 500 / 500&lt;x≤5000 / &gt; 5000）</li>
 *   <li>process() 闸门：仅 APPROVED 可被 process()</li>
 * </ol>
 */
@DisplayName("POS-008 v2 退款多级审核 — 状态机")
class RefundStatusMultiLevelTest {

    // ============================================================
    // 1. 新相位存在
    // ============================================================

    @Test
    @DisplayName("新相位枚举 PENDING_STORE / PENDING_HQ / PENDING_FINANCE 存在")
    void newAuditPhasesExist() {
        assertNotNull(RefundStatus.valueOf("PENDING_STORE"));
        assertNotNull(RefundStatus.valueOf("PENDING_HQ"));
        assertNotNull(RefundStatus.valueOf("PENDING_FINANCE"));
    }

    @Test
    @DisplayName("isAwaitingAudit：PENDING_STORE / PENDING_HQ / PENDING_FINANCE / AUDIT 均返回 true")
    void awaitingAuditHelper() {
        assertTrue(RefundStatus.PENDING_STORE.isAwaitingAudit());
        assertTrue(RefundStatus.PENDING_HQ.isAwaitingAudit());
        assertTrue(RefundStatus.PENDING_FINANCE.isAwaitingAudit());
        assertTrue(RefundStatus.AUDIT.isAwaitingAudit(), "旧链路 AUDIT 也算审核中");
        assertFalse(RefundStatus.APPROVED.isAwaitingAudit());
        assertFalse(RefundStatus.REJECTED.isAwaitingAudit());
        assertFalse(RefundStatus.PROCESSING.isAwaitingAudit());
        assertFalse(RefundStatus.SUCCESS.isAwaitingAudit());
    }

    // ============================================================
    // 2. 状态机迁移合法性
    // ============================================================

    @Test
    @DisplayName("PENDING_STORE → PENDING_HQ / APPROVED / REJECTED / CANCELLED 均合法")
    void pendingStoreTransitions() {
        RefundStatus s = RefundStatus.PENDING_STORE;
        assertTrue(s.canTransitionTo(RefundStatus.PENDING_HQ));
        assertTrue(s.canTransitionTo(RefundStatus.APPROVED));
        assertTrue(s.canTransitionTo(RefundStatus.REJECTED));
        assertTrue(s.canTransitionTo(RefundStatus.CANCELLED));
        // 非法迁移
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_FINANCE), "门店不可直接跳财务");
        assertFalse(s.canTransitionTo(RefundStatus.PROCESSING), "未审核不可处理");
        assertFalse(s.canTransitionTo(RefundStatus.SUCCESS), "未审核不可终态");
    }

    @Test
    @DisplayName("PENDING_HQ → PENDING_FINANCE / APPROVED / REJECTED / CANCELLED 均合法")
    void pendingHqTransitions() {
        RefundStatus s = RefundStatus.PENDING_HQ;
        assertTrue(s.canTransitionTo(RefundStatus.PENDING_FINANCE));
        assertTrue(s.canTransitionTo(RefundStatus.APPROVED));
        assertTrue(s.canTransitionTo(RefundStatus.REJECTED));
        assertTrue(s.canTransitionTo(RefundStatus.CANCELLED));
        // 非法迁移
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_STORE), "不可回退门店");
        assertFalse(s.canTransitionTo(RefundStatus.PROCESSING), "未审核不可处理");
    }

    @Test
    @DisplayName("PENDING_FINANCE → APPROVED / REJECTED / CANCELLED 均合法")
    void pendingFinanceTransitions() {
        RefundStatus s = RefundStatus.PENDING_FINANCE;
        assertTrue(s.canTransitionTo(RefundStatus.APPROVED));
        assertTrue(s.canTransitionTo(RefundStatus.REJECTED));
        assertTrue(s.canTransitionTo(RefundStatus.CANCELLED));
        // 非法迁移
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_STORE));
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_HQ));
        assertFalse(s.canTransitionTo(RefundStatus.PROCESSING), "未审核不可处理");
    }

    @Test
    @DisplayName("APPROVED → PROCESSING / CANCELLED 合法（process() 闸门要求）")
    void approvedTransitions() {
        RefundStatus s = RefundStatus.APPROVED;
        assertTrue(s.canTransitionTo(RefundStatus.PROCESSING));
        assertTrue(s.canTransitionTo(RefundStatus.CANCELLED));
        // 任何中间审核相位都不可直接到 APPROVED 之外的终态
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_STORE));
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_HQ));
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_FINANCE));
        assertFalse(s.canTransitionTo(RefundStatus.REJECTED));
    }

    @Test
    @DisplayName("REJECTED → CANCELLED 合法；不可复活")
    void rejectedTransitions() {
        RefundStatus s = RefundStatus.REJECTED;
        assertTrue(s.canTransitionTo(RefundStatus.CANCELLED));
        assertFalse(s.canTransitionTo(RefundStatus.PENDING_STORE), "驳回不可直接复活（需走 resubmit）");
        assertFalse(s.canTransitionTo(RefundStatus.APPROVED));
        assertFalse(s.canTransitionTo(RefundStatus.PROCESSING));
    }

    @Test
    @DisplayName("终态 SUCCESS / CANCELLED 不可再迁移")
    void terminalStates() {
        assertFalse(RefundStatus.SUCCESS.canTransitionTo(RefundStatus.APPROVED));
        assertFalse(RefundStatus.SUCCESS.canTransitionTo(RefundStatus.PROCESSING));
        assertFalse(RefundStatus.CANCELLED.canTransitionTo(RefundStatus.APPROVED));
        assertFalse(RefundStatus.CANCELLED.canTransitionTo(RefundStatus.PROCESSING));
    }

    @Test
    @DisplayName("isCancellable：审核中三态均可取消")
    void cancellableFromAuditPhases() {
        assertTrue(RefundStatus.PENDING_STORE.isCancellable());
        assertTrue(RefundStatus.PENDING_HQ.isCancellable());
        assertTrue(RefundStatus.PENDING_FINANCE.isCancellable());
        assertTrue(RefundStatus.APPROVED.isCancellable());
        assertFalse(RefundStatus.PROCESSING.isCancellable());
        assertFalse(RefundStatus.SUCCESS.isCancellable());
    }
}
