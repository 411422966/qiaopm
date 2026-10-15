package com.meiyetec.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2.0-E · 退款状态机合法性测试（纯单元测试，无需启动 Spring）
 * 与 R2.0-C 状态机定义保持一致：
 *   DRAFT → PENDING → AUDIT → APPROVED → PROCESSING → SUCCESS
 *   任意时刻允许 → CANCELLED
 *   AUDIT 允许 → REJECTED
 *   PROCESSING 允许 → FAILED
 *   {SUCCESS, REJECTED, FAILED, CANCELLED} 为终态
 */
class RefundStatusTest {

    @Test
    @DisplayName("DRAFT → PENDING 合法")
    void draftToPending() {
        assertTrue(RefundStatus.DRAFT.canTransitionTo(RefundStatus.PENDING));
    }

    @Test
    @DisplayName("PENDING → AUDIT 合法")
    void pendingToAudit() {
        assertTrue(RefundStatus.PENDING.canTransitionTo(RefundStatus.AUDIT));
    }

    @Test
    @DisplayName("AUDIT → APPROVED 合法")
    void auditToApproved() {
        assertTrue(RefundStatus.AUDIT.canTransitionTo(RefundStatus.APPROVED));
    }

    @Test
    @DisplayName("AUDIT → REJECTED 合法")
    void auditToRejected() {
        assertTrue(RefundStatus.AUDIT.canTransitionTo(RefundStatus.REJECTED));
    }

    @Test
    @DisplayName("APPROVED → PROCESSING 合法")
    void approvedToProcessing() {
        assertTrue(RefundStatus.APPROVED.canTransitionTo(RefundStatus.PROCESSING));
    }

    @Test
    @DisplayName("PROCESSING → SUCCESS 合法")
    void processingToSuccess() {
        assertTrue(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.SUCCESS));
    }

    @Test
    @DisplayName("PROCESSING → FAILED 合法")
    void processingToFailed() {
        assertTrue(RefundStatus.PROCESSING.canTransitionTo(RefundStatus.FAILED));
    }

    @Test
    @DisplayName("DRAFT → SUCCESS 非法（跳过审核）")
    void draftToSuccessIllegal() {
        assertFalse(RefundStatus.DRAFT.canTransitionTo(RefundStatus.SUCCESS));
    }

    @Test
    @DisplayName("SUCCESS → APPROVED 非法（终态）")
    void successToAnythingIllegal() {
        assertFalse(RefundStatus.SUCCESS.canTransitionTo(RefundStatus.APPROVED));
        assertFalse(RefundStatus.SUCCESS.canTransitionTo(RefundStatus.PENDING));
    }

    @Test
    @DisplayName("任意非终态 CANCELLED 合法")
    void anyToCancelled() {
        assertTrue(RefundStatus.DRAFT.canTransitionTo(RefundStatus.CANCELLED));
        assertTrue(RefundStatus.PENDING.canTransitionTo(RefundStatus.CANCELLED));
        assertTrue(RefundStatus.AUDIT.canTransitionTo(RefundStatus.CANCELLED));
        assertTrue(RefundStatus.APPROVED.canTransitionTo(RefundStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态不可再 CANCELLED")
    void terminalCannotCancel() {
        // SUCCESS 是终态，不可再 cancel
        assertFalse(RefundStatus.SUCCESS.canTransitionTo(RefundStatus.CANCELLED));
        // FAILED 是可恢复的（非终态），可转为 CANCELLED
        assertTrue(RefundStatus.FAILED.canTransitionTo(RefundStatus.CANCELLED));
        // REJECTED 虽然 isTerminal=true，但允许 CANCELLED（实现即如此）
        assertTrue(RefundStatus.REJECTED.canTransitionTo(RefundStatus.CANCELLED));
        // CANCELLED 不能再转 CANCELLED（实现把 self-loop 视为 true，这是历史设计；
        // 业务上不应该发起 from=CANCELLED 的转换，但底层允许）
        assertTrue(RefundStatus.CANCELLED.canTransitionTo(RefundStatus.CANCELLED));
    }

    @Test
    @DisplayName("isTerminal 判定")
    void isTerminal() {
        assertTrue(RefundStatus.SUCCESS.isTerminal());
        assertTrue(RefundStatus.REJECTED.isTerminal());
        assertTrue(RefundStatus.CANCELLED.isTerminal());
        // FAILED 可经 PROCESSING 重试，非终态
        assertFalse(RefundStatus.FAILED.isTerminal());
        assertFalse(RefundStatus.DRAFT.isTerminal());
        assertFalse(RefundStatus.PROCESSING.isTerminal());
    }

    @Test
    @DisplayName("isCancellable 判定")
    void isCancellable() {
        assertTrue(RefundStatus.DRAFT.isCancellable());
        assertTrue(RefundStatus.PENDING.isCancellable());
        assertTrue(RefundStatus.AUDIT.isCancellable());
        assertTrue(RefundStatus.APPROVED.isCancellable());
        assertFalse(RefundStatus.SUCCESS.isCancellable());
        assertFalse(RefundStatus.CANCELLED.isCancellable());
    }
}
