package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.dto.PosRefundCreateDTO;
import com.meiyetec.entity.PosRefund;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IdempotencyService;
import com.meiyetec.service.PosRefundService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.meiyetec.util.TraceUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M07 退款单 Controller（R2.0-C 升级版）
 *
 * <p>旧接口 `POST /pos/orders/{id}/refund` 保留兼容，但内部走新状态机。</p>
 */
@Slf4j
@RestController
@RequestMapping("/pos/refund")
@RequiredArgsConstructor
public class PosRefundController {

    private final PosRefundService refundService;
    private final IdempotencyService idem;

    /**
     * S-5 · 财务接口 tenant 归属校验：根据 ctx + refundId 校验该退款单是否归属当前租户。
     */
    private PosRefund requireOwnRefund(Long refundId) {
        Long tid = TenantContextHolder.getTenantIdOrNull();
        PosRefund r = refundService.getById(refundId);
        if (r == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "退款单不存在");
        }
        if (tid != null && r.getTenantId() != null && !tid.equals(r.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权访问该退款单（跨租户）");
        }
        return r;
    }

    @PostMapping
    @RequirePermission("pos:refund")
    public Result<PosRefund> create(@Valid @RequestBody PosRefundCreateDTO dto,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String traceId = TraceUtil.newTrace();
        Long tenantId = TenantContextHolder.get().getTenantId();
        String bizKey = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        var r = idem.execute("pos.refund", bizKey, dto, () -> {
            PosRefund refund = refundService.createRefund(dto, bizKey, traceId);
            return Result.success("退款单已创建", refund);
        });
        return r.result;
    }

    @PostMapping("/cross")
    @RequirePermission("pos:refund")
    public Result<PosRefund> createCross(@Valid @RequestBody PosRefundCreateDTO dto,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String traceId = TraceUtil.newTrace();
        String bizKey = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        var r = idem.execute("pos.refund.cross", bizKey, dto, () -> {
            PosRefund refund = refundService.createCrossRefund(dto, bizKey, traceId);
            return Result.success("跨单退款已发起", refund);
        });
        return r.result;
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("pos:refund")
    public Result<PosRefund> detail(@PathVariable Long id) {
        return Result.success(requireOwnRefund(id));
    }

    @GetMapping("/page")
    @RequirePermission("pos:refund")
    public Result<PageResult<PosRefund>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        // S-5: PosRefundService.page 已按 ctx tenantId 过滤
        Page<PosRefund> page = refundService.page(pageNum, pageSize, status, storeId, dateFrom, dateTo);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/{id:\\d+}/audit")
    @RequirePermission("pos:refund:audit")
    public Result<PosRefund> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireOwnRefund(id); // S-5: 校验归属
        Boolean approved = (Boolean) body.getOrDefault("approved", false);
        String rejectReason = (String) body.get("rejectReason");
        Long auditorId = body.get("auditorId") != null ? ((Number) body.get("auditorId")).longValue() : null;
        String auditorName = (String) body.get("auditorName");
        return Result.success(refundService.audit(id, approved, rejectReason, auditorId, auditorName));
    }

    // ============================================================
    // POS-008 v2：显式三级审核链（金额分流）
    //   ≤ 500：audit/store 终审 → APPROVED
    //   500<amount≤5000：audit/store → audit/hq 终审 → APPROVED
    //   > 5000：audit/store → audit/hq → audit/finance 终审 → APPROVED
    // ============================================================

    /**
     * POS-008 v2：门店审核（PENDING_STORE → PENDING_HQ 或 APPROVED）
     * <pre>POST /pos/refund/{id}/audit/store</pre>
     */
    @PostMapping("/{id:\\d+}/audit/store")
    @RequirePermission("pos:refund:audit:store")
    public Result<PosRefund> auditStore(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        requireOwnRefund(id);
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String remark = body == null ? null : (String) body.get("remark");
        return Result.success(refundService.auditByStore(id, actorId, actorName, actorRole, remark));
    }

    /**
     * POS-008 v2：总部审核（PENDING_HQ → PENDING_FINANCE 或 APPROVED）
     * <pre>POST /pos/refund/{id}/audit/hq</pre>
     */
    @PostMapping("/{id:\\d+}/audit/hq")
    @RequirePermission("pos:refund:audit:hq")
    public Result<PosRefund> auditHq(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        requireOwnRefund(id);
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String remark = body == null ? null : (String) body.get("remark");
        return Result.success(refundService.auditByHq(id, actorId, actorName, actorRole, remark));
    }

    /**
     * POS-008 v2：财务终审（PENDING_FINANCE → APPROVED）
     * <pre>POST /pos/refund/{id}/audit/finance</pre>
     */
    @PostMapping("/{id:\\d+}/audit/finance")
    @RequirePermission("pos:refund:audit:finance")
    public Result<PosRefund> auditFinance(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        requireOwnRefund(id);
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String remark = body == null ? null : (String) body.get("remark");
        return Result.success(refundService.auditByFinance(id, actorId, actorName, actorRole, remark));
    }

    /**
     * POS-008 v2：显式驳回（任意级别 PENDING_STORE/HQ/FINANCE → REJECTED）
     * <pre>POST /pos/refund/{id}/audit/reject</pre>
     */
    @PostMapping("/{id:\\d+}/audit/reject")
    @RequirePermission("pos:refund:audit")
    public Result<PosRefund> auditReject(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        requireOwnRefund(id);
        Long actorId = body == null || body.get("actorId") == null ? null
                : ((Number) body.get("actorId")).longValue();
        String actorName = body == null ? null : (String) body.get("actorName");
        String actorRole = body == null ? null : (String) body.get("actorRole");
        String rejectReason = body == null ? null : (String) body.get("rejectReason");
        return Result.success(refundService.auditReject(id, actorId, actorName, actorRole, rejectReason));
    }

    @PostMapping("/{id:\\d+}/process")
    @RequirePermission("pos:refund")
    public Result<PosRefund> process(@PathVariable Long id) {
        requireOwnRefund(id); // S-5: 校验归属
        return Result.success("已处理", refundService.process(id));
    }

    @PostMapping("/{id:\\d+}/cancel")
    @RequirePermission("pos:refund")
    public Result<PosRefund> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        requireOwnRefund(id); // S-5: 校验归属
        String reason = body == null ? null : (String) body.get("reason");
        return Result.success("已取消", refundService.cancel(id, reason));
    }
}
