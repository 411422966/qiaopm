package com.meiyetec.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * R2.0-C / POS-008 退款单状态机：
 *
 * <pre>
 *   DRAFT → PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED → PROCESSING → SUCCESS
 *                                  ↓              ↓             ↓
 *                              REJECTED       REJECTED       REJECTED
 *   DRAFT/PENDING_STORE/PENDING_HQ/PENDING_FINANCE/APPROVED/FAILED → CANCELLED
 *                                                  ↑      ↓
 *                                                  └─ PROCESSING ─→ FAILED
 * </pre>
 *
 * <p>POS-008 多级审核链：金额分流
 * <ul>
 *   <li>≤ 500 元：PENDING_STORE → APPROVED（门店终审）</li>
 *   <li>500 &lt; amount ≤ 5000：PENDING_STORE → PENDING_HQ → APPROVED（总部终审）</li>
 *   <li>&gt; 5000 元：PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED（财务终审）</li>
 * </ul>
 *
 * <p>{@code process()} 仅在 {@link #APPROVED} 状态可受理。
 */
public enum RefundStatus {
    DRAFT,
    PENDING,
    PENDING_STORE,
    PENDING_HQ,
    PENDING_FINANCE,
    AUDIT,
    APPROVED,
    REJECTED,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED;

    /** 可被用户主动取消的来源状态 */
    private static final Set<RefundStatus> CANCEL_FROM = EnumSet.of(
            DRAFT, PENDING, PENDING_STORE, PENDING_HQ, PENDING_FINANCE,
            AUDIT, APPROVED, FAILED);

    public boolean canTransitionTo(RefundStatus next) {
        if (this == next) return true;
        switch (this) {
            case DRAFT:
                return next == PENDING || next == PENDING_STORE
                        || next == AUDIT || next == APPROVED
                        || next == CANCELLED;
            case PENDING:
                return next == AUDIT || next == APPROVED || next == CANCELLED;
            case PENDING_STORE:
                // 门店审核 → 下一级 / 终审通过 / 驳回 / 取消
                return next == PENDING_HQ || next == APPROVED
                        || next == REJECTED || next == CANCELLED;
            case PENDING_HQ:
                // 总部审核 → 财务终审 / 直接终审通过 / 驳回 / 取消
                return next == PENDING_FINANCE || next == APPROVED
                        || next == REJECTED || next == CANCELLED;
            case PENDING_FINANCE:
                // 财务终审 → 通过 / 驳回 / 取消
                return next == APPROVED || next == REJECTED || next == CANCELLED;
            case AUDIT:
                // 旧链路保留（POS-008 改造前产生的单）
                return next == APPROVED || next == REJECTED || next == CANCELLED;
            case APPROVED:
                return next == PROCESSING || next == CANCELLED;
            case REJECTED:
                return next == CANCELLED;
            case PROCESSING:
                return next == SUCCESS || next == FAILED;
            case FAILED:
                return next == PROCESSING || next == CANCELLED;
            case SUCCESS:
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }

    public boolean isCancellable() {
        return CANCEL_FROM.contains(this);
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == CANCELLED || this == REJECTED;
    }

    /**
     * 是否处于"等待审核"状态（任何一级）。
     */
    public boolean isAwaitingAudit() {
        return this == AUDIT || this == PENDING_STORE
                || this == PENDING_HQ || this == PENDING_FINANCE;
    }
}
