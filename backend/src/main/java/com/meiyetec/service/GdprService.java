package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.GdprAuditLog;
import com.meiyetec.entity.GdprConsent;
import com.meiyetec.entity.GdprDataRequest;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.GdprAuditLogMapper;
import com.meiyetec.mapper.GdprConsentMapper;
import com.meiyetec.mapper.GdprDataRequestMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GDPR 合规（FEATURE_COMPARISON_MATRIX.md 268-278 P2-18）
 */
@Slf4j
@RestController
@RequestMapping("/gdpr")
@RequiredArgsConstructor
public class GdprService {

    private final GdprConsentMapper consentMapper;
    private final GdprDataRequestMapper requestMapper;
    private final GdprAuditLogMapper auditMapper;

    @PostMapping("/consent")
    public Result<GdprConsent> grantConsent(@RequestBody GdprConsent c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getMemberId() == null || c.getConsentType() == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId/consentType 必填");
        if (c.getGranted() == null) c.setGranted(1);
        c.setTenantId(tenantId);
        c.setId(null);
        if (c.getConsentType() == null) c.setConsentType("MARKETING");
        if (c.getChannel() == null) c.setChannel("WEB");
        consentMapper.insert(c);

        // 审计
        GdprAuditLog log = new GdprAuditLog();
        log.setTenantId(tenantId);
        log.setTargetId(c.getMemberId());
        log.setTargetType("MEMBER_CONSENT");
        log.setAction("GRANT_CONSENT");
        log.setDetails("consentType=" + c.getConsentType() + ", granted=" + c.getGranted());
        log.setActorName("system");
        auditMapper.insert(log);
        return Result.success("已记录", c);
    }

    @PostMapping("/consent/{id}/revoke")
    public Result<Void> revokeConsent(@PathVariable Long id) {
        GdprConsent c = consentMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "同意记录不存在");
        c.setGranted(0);
        consentMapper.updateById(c);

        GdprAuditLog log = new GdprAuditLog();
        log.setTenantId(c.getTenantId());
        log.setTargetId(c.getMemberId());
        log.setTargetType("MEMBER_CONSENT");
        log.setAction("REVOKE_CONSENT");
        log.setDetails("consentType=" + c.getConsentType());
        log.setActorName("system");
        auditMapper.insert(log);
        return Result.success("已撤销", null);
    }

    @GetMapping("/consent/list")
    public Result<List<GdprConsent>> listConsents(@RequestParam(required = false) Long memberId,
                                                  @RequestParam(required = false) String consentType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<GdprConsent> qw = new LambdaQueryWrapper<GdprConsent>()
                .eq(GdprConsent::getTenantId, tenantId)
                .orderByDesc(GdprConsent::getId);
        if (memberId != null) qw.eq(GdprConsent::getMemberId, memberId);
        if (consentType != null && !consentType.isEmpty()) qw.eq(GdprConsent::getConsentType, consentType);
        return Result.success(consentMapper.selectList(qw));
    }

    @PostMapping("/data-request")
    public Result<GdprDataRequest> createRequest(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String type = (String) body.get("requestType");
        if (memberId == null || type == null) return Result.error(BizCode.BAD_REQUEST, "memberId/requestType 必填");

        GdprDataRequest r = new GdprDataRequest();
        r.setTenantId(tenantId);
        r.setMemberId(memberId);
        r.setRequestType(type);
        r.setRequestNo("GDPR" + System.currentTimeMillis());
        r.setStatus("SUBMITTED");
        r.setReason((String) body.get("remark"));
        requestMapper.insert(r);
        return Result.success("已提交，30 天内响应", r);
    }

    @PostMapping("/data-request/{id}/process")
    public Result<Void> processRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        GdprDataRequest r = requestMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "请求不存在");
        r.setStatus((String) body.get("status"));
        if ("COMPLETED".equals(r.getStatus())) r.setProcessedAt(LocalDateTime.now());
        requestMapper.updateById(r);
        return Result.success("已更新", null);
    }

    @GetMapping("/data-request/list")
    public Result<List<GdprDataRequest>> listRequests(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<GdprDataRequest> qw = new LambdaQueryWrapper<GdprDataRequest>()
                .eq(GdprDataRequest::getTenantId, tenantId)
                .orderByDesc(GdprDataRequest::getId);
        if (status != null && !status.isEmpty()) qw.eq(GdprDataRequest::getStatus, status);
        return Result.success(requestMapper.selectList(qw));
    }

    @GetMapping("/audit-log/list")
    public Result<List<GdprAuditLog>> listAuditLogs(@RequestParam(required = false) Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<GdprAuditLog> qw = new LambdaQueryWrapper<GdprAuditLog>()
                .eq(GdprAuditLog::getTenantId, tenantId)
                .orderByDesc(GdprAuditLog::getId);
        if (memberId != null) qw.eq(GdprAuditLog::getTargetId, memberId);
        return Result.success(auditMapper.selectList(qw));
    }
}