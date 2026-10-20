package com.meiyetec.controller;

import com.meiyetec.entity.TreatmentReport;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.TreatmentReportService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M06 — 疗程效果报告 REST 接口。
 *
 * <p>路径前缀：{@code /biz/treatment/report}（与 {@code BizTreatmentService} 同 namespace）。
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /biz/treatment/report/generate} — 触发报告生成，返回 reportId</li>
 *   <li>{@code GET  /biz/treatment/report/{id}}     — 拉取单条报告</li>
 *   <li>{@code GET  /biz/treatment/report/list?memberId=} — 会员历史报告</li>
 *   <li>{@code GET  /biz/treatment/report/recent}   — 租户近期报告（看板用）</li>
 *   <li>{@code GET  /biz/treatment/report/stats}    — 简单聚合（total / completed / abandoned / compliance 平均）</li>
 * </ul>
 *
 * <p>租户隔离：从 {@link TenantContextHolder} 取 tenantId，未登录兜底 0L — 与 {@code BizTreatmentService.currentTenantId()} 行为一致。
 */
@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/biz/treatment/report")
@RequiredArgsConstructor
public class BizTreatmentReportController {

    private final TreatmentReportService reportService;

    @PostMapping("/generate")
    public Result<Long> generate(@RequestParam Long memberId,
                                 @RequestParam Long planId,
                                 @RequestParam(required = false) String generatedBy) {
        if (memberId == null || planId == null) {
            return Result.error(BizCode.BAD_REQUEST, "memberId / planId 必填");
        }
        Long tenantId = currentTenantId();
        try {
            Long id = reportService.generateReport(memberId, planId, generatedBy, tenantId);
            return Result.success("已生成报告", id);
        } catch (com.meiyetec.exception.BusinessException be) {
            return Result.error(be.getCode(), be.getMessage());
        } catch (Exception e) {
            return Result.error(BizCode.SYSTEM_ERROR, "生成失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<TreatmentReport> getById(@PathVariable Long id) {
        TreatmentReport r;
        try {
            r = reportService.getById(id, currentTenantId());
        } catch (com.meiyetec.exception.BusinessException be) {
            return Result.error(be.getCode(), be.getMessage());
        }
        if (r == null) return Result.error(BizCode.NOT_FOUND, "报告不存在");
        return Result.success(r);
    }

    @GetMapping("/list")
    public Result<List<TreatmentReport>> listByMember(@RequestParam Long memberId) {
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        Long tenantId = currentTenantId();
        return Result.success(reportService.listByMember(memberId, tenantId));
    }

    @GetMapping("/recent")
    public Result<List<TreatmentReport>> recent(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(reportService.listRecent(currentTenantId(), limit));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long tenantId = currentTenantId();
        List<TreatmentReport> all = reportService.listRecent(tenantId, 200);
        long total = all.size();
        long completed = all.stream().filter(r -> r.getCompletedSessions() != null
                && r.getTotalSessions() != null
                && r.getCompletedSessions() >= r.getTotalSessions()).count();
        long abandoned = all.stream().filter(r -> r.getAbandonedSessions() != null
                && r.getAbandonedSessions() > 0).count();
        double avgCompliance = all.stream()
                .filter(r -> r.getCareComplianceRate() != null)
                .mapToDouble(r -> r.getCareComplianceRate().doubleValue())
                .average().orElse(0.0);

        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("totalReports", total);
        out.put("completedCount", completed);
        out.put("abandonedCount", abandoned);
        out.put("avgComplianceRate", avgCompliance);
        return Result.success(out);
    }

    private Long currentTenantId() {
        Long tid = TenantContextHolder.get().getTenantId();
        return tid == null ? 0L : tid;
    }
}
