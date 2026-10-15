package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.PosRefund;
import com.meiyetec.entity.PosRefundAuditLog;
import com.meiyetec.entity.PosRefundAuditRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.RefundAuditWorkflowService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * POS-008 退款审核完整工作流 Controller
 *
 * <p>提供多级审核 + 驳回重提 + 时间线 + 看板 + 规则管理
 */
@Slf4j
@RestController
@RequestMapping("/pos/refund/audit")
@RequiredArgsConstructor
public class RefundAuditController {

    private final RefundAuditWorkflowService workflow;

    // ============================================================
    // 1. 多级审核
    // ============================================================

    /**
     * 当前级别审核通过（自动进入下一级或终审通过）
     */
    @PostMapping("/{id:\\d+}/approve")
    @RequirePermission("pos:refund:audit")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> approve(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String remark = body == null ? null : (String) body.get("remark");
        return workflow.approve(id, actorId, actorName, actorRole, remark);
    }

    /**
     * 审核驳回（任意级别可驳回）
     */
    @PostMapping("/{id:\\d+}/reject")
    @RequirePermission("pos:refund:audit")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> reject(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String rejectReason = body == null ? null : (String) body.get("rejectReason");
        return workflow.reject(id, actorId, actorName, actorRole, rejectReason);
    }

    /**
     * 驳回后重新提交（修改金额/原因后再次进入审核流）
     */
    @PostMapping("/{id:\\d+}/resubmit")
    @RequirePermission("pos:refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefund> resubmit(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Long operatorId = body == null || body.get("operatorId") == null ? null
                : ((Number) body.get("operatorId")).longValue();
        String operatorName = body == null ? null : (String) body.get("operatorName");
        BigDecimal newAmount = body == null || body.get("refundAmount") == null ? null
                : new BigDecimal(body.get("refundAmount").toString());
        String newReason = body == null ? null : (String) body.get("reason");
        return workflow.resubmit(id, operatorId, operatorName, newAmount, newReason);
    }

    // ============================================================
    // 2. 待审核 + 看板
    // ============================================================

    /**
     * 待我审核列表（按当前级别过滤）
     * <p>GET /pos/refund/audit/pending?auditLevel=1&storeId=xxx
     */
    @GetMapping("/pending")
    @RequirePermission("pos:refund:audit")
    public Result<List<Map<String, Object>>> pending(
            @RequestParam(required = false, defaultValue = "1") Integer auditLevel,
            @RequestParam(required = false) Long storeId) {
        return workflow.pendingForLevel(auditLevel, storeId);
    }

    /**
     * 审核统计（看板）
     */
    @GetMapping("/stats")
    @RequirePermission("pos:refund:audit")
    public Result<Map<String, Object>> stats() {
        return workflow.stats();
    }

    /**
     * 审核时间线
     */
    @GetMapping("/timeline/{id:\\d+}")
    @RequirePermission("pos:refund:audit")
    public Result<List<PosRefundAuditLog>> timeline(@PathVariable Long id) {
        return workflow.timeline(id);
    }

    // ============================================================
    // 3. 审核规则
    // ============================================================

    @GetMapping("/rules")
    @RequirePermission("pos:refund:audit")
    public Result<List<PosRefundAuditRule>> listRules() {
        return workflow.listRules();
    }

    @PostMapping("/rules")
    @RequirePermission("pos:refund:audit:rule")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosRefundAuditRule> upsertRule(@RequestBody PosRefundAuditRule rule) {
        return workflow.upsertRule(rule);
    }
}
