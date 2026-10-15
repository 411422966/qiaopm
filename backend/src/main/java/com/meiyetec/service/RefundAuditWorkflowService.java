package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.entity.PosRefund;
import com.meiyetec.entity.PosRefundAuditLog;
import com.meiyetec.entity.PosRefundAuditRule;
import com.meiyetec.enums.RefundStatus;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PosRefundAuditLogMapper;
import com.meiyetec.mapper.PosRefundAuditRuleMapper;
import com.meiyetec.mapper.PosRefundMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * POS-008 退款多级审核完整工作流（v2：显式三级审核链）
 *
 * <p>核心能力：
 * <ul>
 *   <li>显式三级审核链：{@code PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED}</li>
 *   <li>金额阈值分流：≤ 500 门店终审 / 500&lt;amount≤5000 总部必审 / &gt;5000 财务必审</li>
 *   <li>{@link com.meiyetec.service.PosRefundService#process(Long)} 仅在
 *       {@link PosRefund#getAuditStatus()} = APPROVED 时执行原路退款</li>
 *   <li>驳回后重新提交：{@code REJECTED → PENDING_STORE → ...}</li>
 *   <li>审核时间线：每次动作写入 {@code pos_refund_audit_log}</li>
 *   <li>待审核看板 / SLA 超时监控</li>
 *   <li>可配置规则：每个租户可配置每级的阈值/自动通过/必审</li>
 * </ul>
 *
 * <p>状态机（POS-008 v2）：
 * <pre>
 *   DRAFT → PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED → PROCESSING → SUCCESS
 *                                  ↓              ↓             ↓
 *                              REJECTED       REJECTED       REJECTED
 *   任意审核中状态 → CANCELLED
 * </pre>
 *
 * <p>路径前缀：/pos/refund
 *
 * @see com.meiyetec.enums.RefundStatus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundAuditWorkflowService {

    private final PosRefundMapper refundMapper;
    private final PosRefundAuditLogMapper auditLogMapper;
    private final PosRefundAuditRuleMapper auditRuleMapper;

    /** 默认审核规则阈值（POS-008 v2） */
    private static final BigDecimal THRESHOLD_STORE_MAX  = new BigDecimal("500");     // ≤ 500 门店终审
    private static final BigDecimal THRESHOLD_HQ_MIN     = new BigDecimal("500");     // > 500 总部必审
    private static final BigDecimal THRESHOLD_HQ_MAX     = new BigDecimal("5000");    // ≤ 5000 总部终审
    private static final BigDecimal THRESHOLD_FINANCE_MIN = new BigDecimal("5000");   // > 5000 财务必审

    /** 默认 SLA：审核超时时间（小时） */
    private static final int DEFAULT_AUDIT_SLA_HOURS = 24;

    /** 显式审核相位枚举（与 {@link RefundStatus} 中的 PENDING_STORE/PENDING_HQ/PENDING_FINANCE/APPROVED/REJECTED 保持一致） */
    public static final String AUDIT_STATUS_PENDING_STORE   = "PENDING_STORE";
    public static final String AUDIT_STATUS_PENDING_HQ      = "PENDING_HQ";
    public static final String AUDIT_STATUS_PENDING_FINANCE = "PENDING_FINANCE";
    public static final String AUDIT_STATUS_APPROVED        = "APPROVED";
    public static final String AUDIT_STATUS_REJECTED        = "REJECTED";

    // ============================================================
    // 1. 初始化审核相位（创建退款单时由 PosRefundService 调用）
    // ============================================================

    /**
     * 根据金额匹配起始审核相位
     * <ul>
     *   <li>≤ 500 元 → {@code PENDING_STORE}（门店终审）</li>
     *   <li>500 &lt; amount ≤ 5000 → {@code PENDING_STORE}（门店起步 → 总部）</li>
     *   <li>&gt; 5000 元 → {@code PENDING_STORE}（门店起步 → 总部 → 财务）</li>
     * </ul>
     * 返回 null 表示无需审核。
     */
    public String resolveInitialAuditStatus(BigDecimal amount) {
        if (amount == null) return null;
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid == null) tid = 1L;

        // 1) 查询租户规则
        List<PosRefundAuditRule> rules = auditRuleMapper.selectList(
                new LambdaQueryWrapper<PosRefundAuditRule>()
                .eq(PosRefundAuditRule::getTenantId, tid)
                .eq(PosRefundAuditRule::getEnabled, 1)
                .orderByAsc(PosRefundAuditRule::getAuditLevel));

        if (rules.isEmpty()) {
            // 2) 无租户规则 → 按默认阈值
            if (amount.compareTo(THRESHOLD_FINANCE_MIN) > 0) {
                return AUDIT_STATUS_PENDING_STORE;  // 需 3 级
            }
            if (amount.compareTo(THRESHOLD_HQ_MIN) > 0) {
                return AUDIT_STATUS_PENDING_STORE;  // 需 2 级
            }
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                return AUDIT_STATUS_PENDING_STORE;  // 需 1 级
            }
            return null;
        }

        // 3) 租户规则：取 max 命中级别
        int maxLevel = 0;
        for (PosRefundAuditRule r : rules) {
            BigDecimal min = r.getMinAmount() == null ? BigDecimal.ZERO : r.getMinAmount();
            BigDecimal max = r.getMaxAmount() == null ? new BigDecimal("999999") : r.getMaxAmount();
            int required = r.getRequired() == null ? 1 : r.getRequired();
            int auto = r.getAutoApprove() == null ? 0 : r.getAutoApprove();

            if (required == 0 && auto == 1) {
                // 自动通过 → 不计入级别
                continue;
            }
            if (amount.compareTo(min) > 0 && amount.compareTo(max) <= 0) {
                if (r.getAuditLevel() != null && r.getAuditLevel() > maxLevel) {
                    maxLevel = r.getAuditLevel();
                }
            }
        }
        return maxLevel > 0 ? AUDIT_STATUS_PENDING_STORE : null;
    }

    /**
     * 根据金额匹配需要的最高审核级别（兼容旧字段 currentAuditLevel）。
     * <p>1/2/3 = 门店/总部/财务；0 = 无需审核。
     */
    public int resolveAuditLevel(BigDecimal amount) {
        if (amount == null) return 0;
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid == null) tid = 1L;

        List<PosRefundAuditRule> rules = auditRuleMapper.selectList(
                new LambdaQueryWrapper<PosRefundAuditRule>()
                .eq(PosRefundAuditRule::getTenantId, tid)
                .eq(PosRefundAuditRule::getEnabled, 1)
                .orderByAsc(PosRefundAuditRule::getAuditLevel));

        int highestRequired = 0;
        for (PosRefundAuditRule r : rules) {
            BigDecimal min = r.getMinAmount() == null ? BigDecimal.ZERO : r.getMinAmount();
            BigDecimal max = r.getMaxAmount() == null ? new BigDecimal("999999") : r.getMaxAmount();
            int required = r.getRequired() == null ? 1 : r.getRequired();
            int auto = r.getAutoApprove() == null ? 0 : r.getAutoApprove();

            if (amount.compareTo(min) > 0 && amount.compareTo(max) <= 0) {
                if (required == 0 && auto == 1) {
                    continue;
                }
                if (r.getAuditLevel() != null && r.getAuditLevel() > highestRequired) {
                    highestRequired = r.getAuditLevel();
                }
            }
        }
        return highestRequired;
    }

    /**
     * 初始化退款单审核相位 + 写流水
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRefundCreated(PosRefund refund) {
        BigDecimal amount = refund.getRefundAmount();
        String initialAuditStatus = resolveInitialAuditStatus(amount);
        int auditLevel = resolveAuditLevel(amount);

        refund.setAuditStatus(initialAuditStatus);
        refund.setAuditRequired(initialAuditStatus == null ? 0 : 1);
        refund.setCurrentAuditLevel(auditLevel);

        String remark;
        if (initialAuditStatus == null) {
            remark = "提交退款申请，无需审核";
        } else {
            int needLevel = maxRequiredLevel(amount);
            remark = "提交退款申请，自动进入 " + needLevel + " 级审核（金额=" + amount + " 元）";
        }
        writeLog(refund, 0, "SUBMIT", refund.getOperatorId(), refund.getOperatorName(),
                null, null, refund.getStatus(), remark);
    }

    /**
     * 退款需要的最高审核级别
     */
    private int maxRequiredLevel(BigDecimal amount) {
        if (amount == null) return 0;
        if (amount.compareTo(THRESHOLD_FINANCE_MIN) > 0) return 3;
        if (amount.compareTo(THRESHOLD_HQ_MIN) > 0) return 2;
        if (amount.compareTo(BigDecimal.ZERO) > 0) return 1;
        return 0;
    }

    // ============================================================
    // 2. 显式三级审核（PENDING_STORE → PENDING_HQ → PENDING_FINANCE → APPROVED）
    // ============================================================

    /**
     * 门店审核：PENDING_STORE → PENDING_HQ（> 500）/ APPROVED（≤ 500）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> auditByStore(Long refundId, Long actorId, String actorName,
                                           String actorRole, String remark) {
        PosRefund refund = locked(refundId);
        requireCurrentPhase(refund, AUDIT_STATUS_PENDING_STORE, "门店审核");

        BigDecimal amount = refund.getRefundAmount();
        boolean canTerminal = amount != null && amount.compareTo(THRESHOLD_HQ_MIN) <= 0;

        // 写流水
        writeLog(refund, 1, "STORE_APPROVE", actorId, actorName, actorRole,
                AUDIT_STATUS_PENDING_STORE,
                canTerminal ? AUDIT_STATUS_APPROVED : AUDIT_STATUS_PENDING_HQ,
                canTerminal
                        ? "门店审核通过（≤ 500 门店终审），已 APPROVED"
                        : "门店审核通过（> 500 进入总部），PENDING_STORE → PENDING_HQ");

        // 更新状态
        if (canTerminal) {
            transit(refund, AUDIT_STATUS_APPROVED, RefundStatus.APPROVED);
        } else {
            refund.setAuditStatus(AUDIT_STATUS_PENDING_HQ);
            refund.setCurrentAuditLevel(2);
            refundMapper.updateById(refund);
        }
        refund.setAuditorId(actorId);
        refund.setAuditorName(actorName);
        refund.setAuditTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        return Result.success(canTerminal ? "门店终审通过，可执行退款" : "门店通过，已流转总部", refund);
    }

    /**
     * 总部审核：PENDING_HQ → PENDING_FINANCE（> 5000）/ APPROVED（500 < amount ≤ 5000）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> auditByHq(Long refundId, Long actorId, String actorName,
                                         String actorRole, String remark) {
        PosRefund refund = locked(refundId);
        requireCurrentPhase(refund, AUDIT_STATUS_PENDING_HQ, "总部审核");

        BigDecimal amount = refund.getRefundAmount();
        boolean needFinance = amount != null && amount.compareTo(THRESHOLD_FINANCE_MIN) > 0;

        writeLog(refund, 2, "HQ_APPROVE", actorId, actorName, actorRole,
                AUDIT_STATUS_PENDING_HQ,
                needFinance ? AUDIT_STATUS_PENDING_FINANCE : AUDIT_STATUS_APPROVED,
                needFinance
                        ? "总部审核通过（> 5000 进入财务），PENDING_HQ → PENDING_FINANCE"
                        : "总部审核通过（500 < amount ≤ 5000 总部终审），已 APPROVED");

        if (needFinance) {
            refund.setAuditStatus(AUDIT_STATUS_PENDING_FINANCE);
            refund.setCurrentAuditLevel(3);
            refundMapper.updateById(refund);
        } else {
            transit(refund, AUDIT_STATUS_APPROVED, RefundStatus.APPROVED);
        }
        refund.setAuditorId(actorId);
        refund.setAuditorName(actorName);
        refund.setAuditTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        return Result.success(needFinance ? "总部通过，已流转财务" : "总部终审通过，可执行退款", refund);
    }

    /**
     * 财务终审：PENDING_FINANCE → APPROVED
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> auditByFinance(Long refundId, Long actorId, String actorName,
                                             String actorRole, String remark) {
        PosRefund refund = locked(refundId);
        requireCurrentPhase(refund, AUDIT_STATUS_PENDING_FINANCE, "财务审核");

        writeLog(refund, 3, "FINANCE_APPROVE", actorId, actorName, actorRole,
                AUDIT_STATUS_PENDING_FINANCE, AUDIT_STATUS_APPROVED,
                "财务终审通过，PENDING_FINANCE → APPROVED");

        transit(refund, AUDIT_STATUS_APPROVED, RefundStatus.APPROVED);
        refund.setAuditorId(actorId);
        refund.setAuditorName(actorName);
        refund.setAuditTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        return Result.success("财务终审通过，可执行退款", refund);
    }

    /**
     * 任意级别驳回：当前相位 → REJECTED
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> reject(Long refundId, Long actorId, String actorName,
                                     String actorRole, String rejectReason) {
        PosRefund refund = locked(refundId);
        String cur = refund.getAuditStatus();
        if (cur == null
                || (!AUDIT_STATUS_PENDING_STORE.equals(cur)
                    && !AUDIT_STATUS_PENDING_HQ.equals(cur)
                    && !AUDIT_STATUS_PENDING_FINANCE.equals(cur))) {
            throw new BusinessException(BizCode.CONFLICT,
                    "当前相位不可驳回：" + cur + "（需为 PENDING_STORE/HQ/FINANCE）");
        }
        int level = currentLevelByPhase(cur);

        writeLog(refund, level, "REJECT", actorId, actorName, actorRole,
                cur, AUDIT_STATUS_REJECTED,
                levelName(level) + " 驳回：" + (rejectReason == null ? "审核未通过" : rejectReason));

        transit(refund, AUDIT_STATUS_REJECTED, RefundStatus.REJECTED);
        refund.setRejectReason(rejectReason == null ? "审核未通过" : rejectReason);
        refund.setAuditorId(actorId);
        refund.setAuditorName(actorName);
        refund.setAuditTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        return Result.success("已驳回", refund);
    }

    /**
     * 驳回后重新提交（修改金额/原因后再次进入审核流）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> resubmit(Long refundId, Long operatorId, String operatorName,
                                       BigDecimal newAmount, String newReason) {
        PosRefund refund = locked(refundId);
        if (!AUDIT_STATUS_REJECTED.equals(refund.getAuditStatus())
                && !RefundStatus.REJECTED.name().equals(refund.getStatus())) {
            throw new BusinessException(BizCode.CONFLICT,
                    "仅 REJECTED 状态可重新提交，当前 auditStatus="
                            + refund.getAuditStatus() + " status=" + refund.getStatus());
        }

        if (newAmount != null && newAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remain = refund.getRemainingAmount() == null ? BigDecimal.ZERO : refund.getRemainingAmount();
            if (newAmount.compareTo(remain) > 0) {
                throw new BusinessException(BizCode.REFUND_EXCEED,
                        "重新提交金额超过可退余额：" + remain);
            }
            refund.setRefundAmount(newAmount);
        }
        if (newReason != null && !newReason.isEmpty()) {
            refund.setReason(newReason);
        }

        // 重新计算审核相位
        String initialAuditStatus = resolveInitialAuditStatus(refund.getRefundAmount());
        int auditLevel = resolveAuditLevel(refund.getRefundAmount());
        refund.setAuditStatus(initialAuditStatus);
        refund.setAuditRequired(initialAuditStatus == null ? 0 : 1);
        refund.setCurrentAuditLevel(auditLevel);
        refund.setRejectReason(null);
        refund.setAuditTime(null);

        if (initialAuditStatus == null) {
            refund.setStatus(RefundStatus.APPROVED.name());
        } else {
            refund.setStatus(RefundStatus.AUDIT.name());
        }
        refundMapper.updateById(refund);

        writeLog(refund, 0, "RESUBMIT", operatorId, operatorName, null,
                AUDIT_STATUS_REJECTED, initialAuditStatus,
                initialAuditStatus == null ? "重新提交，无需审核，自动通过"
                        : "重新提交 → " + initialAuditStatus);
        return Result.success("已重新提交", refund);
    }

    // ============================================================
    // 3. 兼容旧链路（POS-008 v1 多级审核）
    // ============================================================

    /**
     * 旧链路：当前级别审核通过 → 进入下一级 / 终审通过
     * 保留用于 {@code PosRefundService.audit()}（向后兼容）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> approve(Long refundId, Long actorId, String actorName,
                                      String actorRole, String remark) {
        PosRefund refund = locked(refundId);
        if (!RefundStatus.AUDIT.name().equals(refund.getStatus())) {
            throw new BusinessException(BizCode.CONFLICT,
                    "仅 AUDIT 状态可审核，当前=" + refund.getStatus());
        }
        int currentLevel = refund.getCurrentAuditLevel() == null ? 0 : refund.getCurrentAuditLevel();
        if (currentLevel <= 0) {
            throw new BusinessException(BizCode.CONFLICT, "该退款单无需多级审核，请直接处理");
        }

        Long tid = TenantContextHolder.get().getTenantId();
        PosRefundAuditRule nextRule = auditRuleMapper.selectOne(
                new LambdaQueryWrapper<PosRefundAuditRule>()
                .eq(PosRefundAuditRule::getTenantId, tid)
                .eq(PosRefundAuditRule::getAuditLevel, currentLevel + 1)
                .eq(PosRefundAuditRule::getEnabled, 1));

        String actionName = "L" + currentLevel + "_APPROVE";
        String remarkStr = currentLevel + " 级审核通过（" + (actorName == null ? "system" : actorName) + "）";

        if (nextRule == null) {
            transit(refund, AUDIT_STATUS_APPROVED, RefundStatus.APPROVED);
            refund.setCurrentAuditLevel(0);
            refund.setAuditorId(actorId);
            refund.setAuditorName(actorName);
            refund.setAuditTime(LocalDateTime.now());
            refundMapper.updateById(refund);
            writeLog(refund, currentLevel, actionName, actorId, actorName, actorRole,
                    AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel), AUDIT_STATUS_APPROVED, remarkStr);
            writeLog(refund, 0, "AUTO_NEXT", null, "system", null,
                    AUDIT_STATUS_APPROVED, AUDIT_STATUS_APPROVED, "终审通过，无更高级别");
            return Result.success("终审通过", refund);
        }

        int nextRequired = nextRule.getRequired() == null ? 1 : nextRule.getRequired();
        int nextAuto = nextRule.getAutoApprove() == null ? 0 : nextRule.getAutoApprove();

        if (nextRequired == 0 && nextAuto == 1) {
            writeLog(refund, currentLevel, actionName, actorId, actorName, actorRole,
                    AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel),
                    AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel + 1),
                    remarkStr);
            refund.setCurrentAuditLevel(currentLevel + 1);
            refundMapper.updateById(refund);
            writeLog(refund, currentLevel + 1, "AUTO_APPROVE", null, "system", null,
                    AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel + 1),
                    AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel + 1),
                    "L" + (currentLevel + 1) + " 自动通过（" + nextRule.getAuditLevelName() + "）");
            return approve(refundId, null, "system", "system", null);
        }

        refund.setCurrentAuditLevel(currentLevel + 1);
        refundMapper.updateById(refund);
        writeLog(refund, currentLevel, actionName, actorId, actorName, actorRole,
                AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel),
                AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(currentLevel + 1),
                remarkStr + " → 进入 " + (currentLevel + 1) + " 级（" + nextRule.getAuditLevelName() + "）");
        return Result.success(currentLevel + " 级通过，已进入 " + (currentLevel + 1) + " 级（"
                + nextRule.getAuditLevelName() + "）", refund);
    }

    /**
     * 把旧链路的"级别"映射到新链路的"相位"，便于 audit_log 兼容。
     */
    private static String AUDIT_STATUS_PENDING_PHASE_FOR_LEVEL(int level) {
        return switch (level) {
            case 1 -> AUDIT_STATUS_PENDING_STORE;
            case 2 -> AUDIT_STATUS_PENDING_HQ;
            case 3 -> AUDIT_STATUS_PENDING_FINANCE;
            default -> "PENDING";
        };
    }

    // ============================================================
    // 4. 待审核列表 / 看板
    // ============================================================

    /**
     * 待我审核列表（按显式相位过滤）
     */
    public Result<List<Map<String, Object>>> pendingByPhase(String auditStatus, Long storeId) {
        Long tid = TenantContextHolder.get().getTenantId();
        QueryWrapper<PosRefund> q = new QueryWrapper<>();
        q.eq("tenant_id", tid);
        q.eq("audit_status", auditStatus);
        if (storeId != null) q.eq("store_id", storeId);
        q.last("ORDER BY CASE WHEN apply_time < DATE_SUB(NOW(), INTERVAL " + DEFAULT_AUDIT_SLA_HOURS
                + " HOUR) THEN 0 ELSE 1 END, apply_time ASC LIMIT 200");
        List<PosRefund> records = refundMapper.selectList(q);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PosRefund r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("refundNo", r.getRefundNo());
            m.put("orderNo", r.getOrderNo());
            m.put("memberName", r.getMemberName());
            m.put("refundAmount", r.getRefundAmount());
            m.put("refundType", r.getRefundType());
            m.put("reason", r.getReason());
            m.put("applyTime", r.getApplyTime());
            m.put("auditStatus", r.getAuditStatus());
            m.put("auditLevel", r.getCurrentAuditLevel());
            long hoursSinceApply = r.getApplyTime() == null ? 0
                    : java.time.Duration.between(r.getApplyTime(), LocalDateTime.now()).toHours();
            m.put("hoursSinceApply", hoursSinceApply);
            m.put("slaExceeded", hoursSinceApply > DEFAULT_AUDIT_SLA_HOURS);
            result.add(m);
        }
        return Result.success(result);
    }

    /** 向后兼容：按级别查待审 */
    public Result<List<Map<String, Object>>> pendingForLevel(Integer auditLevel, Long storeId) {
        String phase = switch (auditLevel == null ? 0 : auditLevel) {
            case 1 -> AUDIT_STATUS_PENDING_STORE;
            case 2 -> AUDIT_STATUS_PENDING_HQ;
            case 3 -> AUDIT_STATUS_PENDING_FINANCE;
            default -> "AUDIT";
        };
        return pendingByPhase(phase, storeId);
    }

    /**
     * 审核统计（看板）
     */
    public Result<Map<String, Object>> stats() {
        Long tid = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 三级待审数量（显式相位）
        for (String phase : new String[]{AUDIT_STATUS_PENDING_STORE, AUDIT_STATUS_PENDING_HQ, AUDIT_STATUS_PENDING_FINANCE}) {
            Long cnt = refundMapper.selectCount(new LambdaQueryWrapper<PosRefund>()
                    .eq(PosRefund::getTenantId, tid)
                    .eq(PosRefund::getAuditStatus, phase));
            String key = switch (phase) {
                case AUDIT_STATUS_PENDING_STORE -> "storePending";
                case AUDIT_STATUS_PENDING_HQ -> "hqPending";
                case AUDIT_STATUS_PENDING_FINANCE -> "financePending";
                default -> phase;
            };
            data.put(key, cnt == null ? 0 : cnt);
        }

        Map<String, Object> summary = refundMapper.selectMaps(new QueryWrapper<PosRefund>()
                .select("SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS successCount",
                        "SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END) AS rejectedCount",
                        "SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) AS cancelledCount",
                        "SUM(CASE WHEN status='PROCESSING' THEN 1 ELSE 0 END) AS processingCount",
                        "COUNT(*) AS totalCount")
                .eq("tenant_id", tid)).stream().findFirst().orElse(new HashMap<>());
        data.putAll(summary);

        Map<String, Object> today = jdbcCount(
                "SELECT " +
                "  SUM(CASE WHEN DATE(create_time)=CURDATE() THEN 1 ELSE 0 END) AS todayApply, " +
                "  SUM(CASE WHEN DATE(audit_time)=CURDATE() AND status='APPROVED' THEN 1 ELSE 0 END) AS todayApprove, " +
                "  SUM(CASE WHEN DATE(audit_time)=CURDATE() AND status='REJECTED' THEN 1 ELSE 0 END) AS todayReject " +
                "FROM pos_refund WHERE tenant_id=? AND deleted=0", tid);
        data.putAll(today);

        Map<String, Object> slaMap = jdbcCount(
                "SELECT COUNT(*) AS cnt FROM pos_refund " +
                "WHERE tenant_id=? AND deleted=0 AND audit_status IN ('PENDING_STORE','PENDING_HQ','PENDING_FINANCE') " +
                "AND apply_time < DATE_SUB(NOW(), INTERVAL ? HOUR)",
                tid, DEFAULT_AUDIT_SLA_HOURS);
        Object slaOverdue = slaMap == null ? null : slaMap.get("cnt");
        data.put("slaOverdue", slaOverdue == null ? 0 : ((Number) slaOverdue).longValue());

        return Result.success(data);
    }

    /**
     * 审核时间线
     */
    public Result<List<PosRefundAuditLog>> timeline(Long refundId) {
        Long tid = TenantContextHolder.get().getTenantId();
        PosRefund refund = refundMapper.selectById(refundId);
        if (refund == null || (tid != null && !tid.equals(refund.getTenantId()))) {
            throw new BusinessException(BizCode.NOT_FOUND, "退款单不存在");
        }
        List<PosRefundAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<PosRefundAuditLog>()
                .eq(PosRefundAuditLog::getRefundId, refundId)
                .orderByAsc(PosRefundAuditLog::getCreateTime));
        return Result.success(logs);
    }

    // ============================================================
    // 5. 审核规则 CRUD
    // ============================================================

    public Result<List<PosRefundAuditRule>> listRules() {
        Long tid = TenantContextHolder.get().getTenantId();
        List<PosRefundAuditRule> rules = auditRuleMapper.selectList(
                new LambdaQueryWrapper<PosRefundAuditRule>()
                .eq(PosRefundAuditRule::getTenantId, tid)
                .orderByAsc(PosRefundAuditRule::getAuditLevel));
        if (rules.isEmpty()) {
            rules = defaultRules();
        }
        return Result.success(rules);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefundAuditRule> upsertRule(PosRefundAuditRule rule) {
        Long tid = TenantContextHolder.get().getTenantId();
        rule.setTenantId(tid);
        if (rule.getEnabled() == null) rule.setEnabled(1);

        PosRefundAuditRule exist = auditRuleMapper.selectOne(
                new LambdaQueryWrapper<PosRefundAuditRule>()
                .eq(PosRefundAuditRule::getTenantId, tid)
                .eq(PosRefundAuditRule::getAuditLevel, rule.getAuditLevel()));
        if (exist != null) {
            exist.setMinAmount(rule.getMinAmount());
            exist.setMaxAmount(rule.getMaxAmount());
            exist.setRequired(rule.getRequired());
            exist.setAutoApprove(rule.getAutoApprove());
            exist.setAuditLevelName(rule.getAuditLevelName());
            exist.setEnabled(rule.getEnabled());
            exist.setOperatorId(rule.getOperatorId());
            exist.setOperatorName(rule.getOperatorName());
            auditRuleMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        auditRuleMapper.insert(rule);
        return Result.success("已创建", rule);
    }

    // ============================================================
    // 6. 工具
    // ============================================================

    /**
     * 默认审核规则（POS-008 v2 阈值）
     */
    private List<PosRefundAuditRule> defaultRules() {
        List<PosRefundAuditRule> defaults = new ArrayList<>();
        PosRefundAuditRule l1 = new PosRefundAuditRule();
        l1.setAuditLevel(1);
        l1.setAuditLevelName("门店");
        l1.setMinAmount(BigDecimal.ZERO);
        l1.setMaxAmount(THRESHOLD_STORE_MAX);
        l1.setRequired(0);
        l1.setAutoApprove(0);
        l1.setEnabled(1);
        defaults.add(l1);

        PosRefundAuditRule l2 = new PosRefundAuditRule();
        l2.setAuditLevel(2);
        l2.setAuditLevelName("总部");
        l2.setMinAmount(THRESHOLD_HQ_MIN);
        l2.setMaxAmount(THRESHOLD_HQ_MAX);
        l2.setRequired(1);
        l2.setAutoApprove(0);
        l2.setEnabled(1);
        defaults.add(l2);

        PosRefundAuditRule l3 = new PosRefundAuditRule();
        l3.setAuditLevel(3);
        l3.setAuditLevelName("财务");
        l3.setMinAmount(THRESHOLD_FINANCE_MIN);
        l3.setMaxAmount(new BigDecimal("999999"));
        l3.setRequired(1);
        l3.setAutoApprove(0);
        l3.setEnabled(1);
        defaults.add(l3);
        return defaults;
    }

    /**
     * 同时迁移 auditStatus 与 status（带 version 乐观锁）
     */
    private void transit(PosRefund refund, String newAuditStatus, RefundStatus newOverallStatus) {
        String oldAudit = refund.getAuditStatus();
        String oldStatus = refund.getStatus();

        // 1. 校验 status 状态机合法性（如果 oldStatus 非 null 且不为终态）
        if (oldStatus != null) {
            try {
                RefundStatus curStatus = RefundStatus.valueOf(oldStatus);
                if (!curStatus.isTerminal() && !curStatus.canTransitionTo(newOverallStatus)) {
                    throw new BusinessException(BizCode.CONFLICT,
                            "退款单 status 不可从 " + curStatus + " 迁移到 " + newOverallStatus);
                }
            } catch (IllegalArgumentException ignored) {
                // 旧 status 字段可能含历史脏值（如 AUDIT），不阻塞
            }
        }

        // 2. CAS 更新（version 乐观锁）
        int rows = refundMapper.transitStatus(refund.getId(), refund.getVersion(),
                oldStatus == null ? "DRAFT" : oldStatus, newOverallStatus.name());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "退款单状态机并发冲突");
        }

        refund.setAuditStatus(newAuditStatus);
        refund.setStatus(newOverallStatus.name());
        refund.setVersion(refund.getVersion() == null ? 1 : refund.getVersion() + 1);

        log.debug("[POS-008] transit refundId={} audit: {} → {}, status: {} → {}",
                refund.getId(), oldAudit, newAuditStatus, oldStatus, newOverallStatus);
    }

    /**
     * 校验当前相位是否为 expected
     */
    private void requireCurrentPhase(PosRefund refund, String expected, String action) {
        String cur = refund.getAuditStatus();
        if (!expected.equals(cur)) {
            throw new BusinessException(BizCode.CONFLICT,
                    action + " 仅在 " + expected + " 相位可执行，当前=" + cur);
        }
    }

    private int currentLevelByPhase(String phase) {
        return switch (phase) {
            case AUDIT_STATUS_PENDING_STORE -> 1;
            case AUDIT_STATUS_PENDING_HQ -> 2;
            case AUDIT_STATUS_PENDING_FINANCE -> 3;
            default -> 0;
        };
    }

    private String levelName(int level) {
        return switch (level) {
            case 1 -> "门店";
            case 2 -> "总部";
            case 3 -> "财务";
            default -> "—";
        };
    }

    private void writeLog(PosRefund refund, Integer level, String action,
                           Long actorId, String actorName, String actorRole,
                           String beforeStatus, String afterStatus, String remark) {
        try {
            PosRefundAuditLog log = new PosRefundAuditLog();
            log.setTenantId(refund.getTenantId());
            log.setRefundId(refund.getId());
            log.setRefundNo(refund.getRefundNo());
            log.setAuditLevel(level);
            log.setAuditLevelName(levelName(level));
            log.setAction(action);
            log.setActorId(actorId);
            log.setActorName(actorName);
            log.setActorRole(actorRole);
            log.setBeforeStatus(beforeStatus);
            log.setAfterStatus(afterStatus);
            log.setRemark(remark);
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 流水失败不阻塞主流程
            log.warn("[POS-008] 写审核流水失败 refundId={} action={}: {}", refund.getId(), action, e.getMessage());
        }
    }

    private PosRefund locked(Long refundId) {
        PosRefund r = refundMapper.selectById(refundId);
        if (r == null) throw new BusinessException(BizCode.NOT_FOUND, "退款单不存在");
        return r;
    }

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Object> jdbcCount(String sql, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (Exception e) {
            log.warn("[POS-008] jdbcCount 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
