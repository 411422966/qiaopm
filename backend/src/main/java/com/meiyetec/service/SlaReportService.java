package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.SlaReport;
import com.meiyetec.entity.SlaTarget;
import com.meiyetec.entity.SlaUptimeRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.SlaReportMapper;
import com.meiyetec.mapper.SlaTargetMapper;
import com.meiyetec.mapper.SlaUptimeRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * R3.0-H P2.7：SLA 报告
 *
 * <p>提供：
 * <ul>
 *   <li>SLA 目标配置（可用性 % / P95/P99 / 错误率）</li>
 *   <li>每日可用性记录</li>
 *   <li>月度 SLA 报表生成</li>
 *   <li>SLA 达成判定</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/sla")
@RequiredArgsConstructor
public class SlaReportService {

    private final SlaTargetMapper targetMapper;
    private final SlaUptimeRecordMapper uptimeMapper;
    private final SlaReportMapper reportMapper;

    // ====================================================================
    // 1. SLA 目标
    // ====================================================================

    @PostMapping("/target")
    @Transactional(rollbackFor = Exception.class)
    public Result<SlaTarget> upsertTarget(@RequestBody SlaTarget t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        t.setTenantId(tenantId);
        if (t.getAvailabilityTarget() == null) t.setAvailabilityTarget(new BigDecimal("99.90"));
        if (t.getMonthlyDowntimeBudgetSec() == null) t.setMonthlyDowntimeBudgetSec(2592);
        if (t.getResponseP95Ms() == null) t.setResponseP95Ms(500);
        if (t.getResponseP99Ms() == null) t.setResponseP99Ms(1000);
        if (t.getErrorRateThreshold() == null) t.setErrorRateThreshold(new BigDecimal("0.0010"));
        if (t.getEnabled() == null) t.setEnabled(1);

        SlaTarget exist = targetMapper.selectOne(
                new LambdaQueryWrapper<SlaTarget>()
                        .eq(SlaTarget::getTenantId, tenantId)
                        .eq(SlaTarget::getServiceCode, t.getServiceCode()));
        if (exist != null) {
            exist.setServiceName(t.getServiceName());
            exist.setAvailabilityTarget(t.getAvailabilityTarget());
            exist.setMonthlyDowntimeBudgetSec(t.getMonthlyDowntimeBudgetSec());
            exist.setResponseP95Ms(t.getResponseP95Ms());
            exist.setResponseP99Ms(t.getResponseP99Ms());
            exist.setErrorRateThreshold(t.getErrorRateThreshold());
            exist.setEnabled(t.getEnabled());
            targetMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        targetMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/target/list")
    public Result<List<SlaTarget>> listTargets() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(targetMapper.selectList(
                new LambdaQueryWrapper<SlaTarget>()
                        .eq(SlaTarget::getTenantId, tenantId)
                        .eq(SlaTarget::getEnabled, 1)));
    }

    // ====================================================================
    // 2. 可用性记录
    // ====================================================================

    @PostMapping("/uptime/record")
    @Transactional(rollbackFor = Exception.class)
    public Result<SlaUptimeRecord> recordUptime(@RequestBody SlaUptimeRecord r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        r.setTenantId(tenantId);
        if (r.getTotalMinutes() == null) r.setTotalMinutes(1440);
        if (r.getDownMinutes() == null) r.setDownMinutes(0);
        r.setUptimeMinutes(r.getTotalMinutes() - r.getDownMinutes());
        // 计算可用性 %
        if (r.getTotalMinutes() > 0) {
            BigDecimal avail = BigDecimal.valueOf(r.getUptimeMinutes())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(r.getTotalMinutes()), 4, RoundingMode.HALF_UP);
            r.setAvailability(avail);
        }
        uptimeMapper.insert(r);
        return Result.success("已记录", r);
    }

    @GetMapping("/uptime/list")
    public Result<List<SlaUptimeRecord>> uptimeList(@RequestParam String serviceCode,
                                                       @RequestParam(required = false) String periodMonth) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<SlaUptimeRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(SlaUptimeRecord::getTenantId, tenantId)
                .eq(SlaUptimeRecord::getServiceCode, serviceCode);
        if (periodMonth != null) {
            YearMonth ym = YearMonth.parse(periodMonth);
            qw.between(SlaUptimeRecord::getRecordDate, ym.atDay(1), ym.atEndOfMonth());
        }
        qw.orderByAsc(SlaUptimeRecord::getRecordDate);
        return Result.success(uptimeMapper.selectList(qw));
    }

    // ====================================================================
    // 3. 月度 SLA 报告生成
    // ====================================================================

    @PostMapping("/report/generate")
    @Transactional(rollbackFor = Exception.class)
    public Result<SlaReport> generateReport(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String periodMonth = (String) body.getOrDefault("periodMonth",
                YearMonth.now().toString());
        String serviceCode = (String) body.get("serviceCode");
        if (serviceCode == null) return Result.error(BizCode.BAD_REQUEST, "serviceCode 必填");

        SlaTarget target = targetMapper.selectOne(
                new LambdaQueryWrapper<SlaTarget>()
                        .eq(SlaTarget::getTenantId, tenantId)
                        .eq(SlaTarget::getServiceCode, serviceCode));
        if (target == null) return Result.error(BizCode.NOT_FOUND, "SLA 目标未配置");

        YearMonth ym = YearMonth.parse(periodMonth);
        List<SlaUptimeRecord> records = uptimeMapper.selectList(
                new LambdaQueryWrapper<SlaUptimeRecord>()
                        .eq(SlaUptimeRecord::getTenantId, tenantId)
                        .eq(SlaUptimeRecord::getServiceCode, serviceCode)
                        .between(SlaUptimeRecord::getRecordDate, ym.atDay(1), ym.atEndOfMonth()));

        // 计算月可用性
        int totalMin = records.stream().mapToInt(r -> r.getTotalMinutes() == null ? 0 : r.getTotalMinutes()).sum();
        int downMin = records.stream().mapToInt(r -> r.getDownMinutes() == null ? 0 : r.getDownMinutes()).sum();
        int upMin = totalMin - downMin;
        BigDecimal availabilityActual = totalMin > 0
                ? BigDecimal.valueOf(upMin * 10000L / totalMin).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);

        int totalIncidents = records.stream().mapToInt(r -> r.getIncidentCount() == null ? 0 : r.getIncidentCount()).sum();
        long totalReqs = records.stream().mapToLong(r -> r.getTotalRequests() == null ? 0L : r.getTotalRequests()).sum();
        long totalErrs = records.stream().mapToLong(r -> r.getErrorCount() == null ? 0L : r.getErrorCount()).sum();
        BigDecimal errorRate = totalReqs > 0
                ? BigDecimal.valueOf(totalErrs).divide(BigDecimal.valueOf(totalReqs), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int p95Sum = records.stream().filter(r -> r.getP95ResponseMs() != null).mapToInt(SlaUptimeRecord::getP95ResponseMs).sum();
        int p99Sum = records.stream().filter(r -> r.getP99ResponseMs() != null).mapToInt(SlaUptimeRecord::getP99ResponseMs).sum();
        long p95Cnt = records.stream().filter(r -> r.getP95ResponseMs() != null).count();
        long p99Cnt = records.stream().filter(r -> r.getP99ResponseMs() != null).count();
        int p95Avg = p95Cnt > 0 ? (int) (p95Sum / p95Cnt) : 0;
        int p99Avg = p99Cnt > 0 ? (int) (p99Sum / p99Cnt) : 0;

        int downtimeSec = downMin * 60;
        int achieved = availabilityActual.compareTo(target.getAvailabilityTarget()) >= 0 ? 1 : 0;

        String summary = String.format(
                "服务 %s 在 %s 月可用性 %s%%（目标 %s%%），达成率 %s，停机 %d 秒（预算 %d 秒），故障 %d 次，P95=%dms P99=%dms 错误率 %s。",
                serviceCode, periodMonth, availabilityActual, target.getAvailabilityTarget(),
                achieved == 1 ? "✅ 达标" : "❌ 未达标",
                downtimeSec, target.getMonthlyDowntimeBudgetSec(),
                totalIncidents, p95Avg, p99Avg, errorRate);

        SlaReport r = new SlaReport();
        r.setTenantId(tenantId);
        r.setReportKey(serviceCode + "_" + periodMonth);
        r.setPeriodMonth(periodMonth);
        r.setServiceCode(serviceCode);
        r.setAvailabilityActual(availabilityActual);
        r.setAvailabilityTarget(target.getAvailabilityTarget());
        r.setAchieved(achieved);
        r.setDowntimeSeconds(downtimeSec);
        r.setDowntimeBudgetSec(target.getMonthlyDowntimeBudgetSec());
        r.setIncidentCount(totalIncidents);
        r.setP95AvgMs(p95Avg);
        r.setP99AvgMs(p99Avg);
        r.setErrorRate(errorRate);
        r.setSummary(summary);
        r.setStatus("GENERATED");
        reportMapper.insert(r);
        return Result.success("已生成", r);
    }

    @GetMapping("/report/list")
    public Result<List<SlaReport>> reportList(@RequestParam(required = false) String periodMonth) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<SlaReport> qw = new LambdaQueryWrapper<>();
        qw.eq(SlaReport::getTenantId, tenantId);
        if (periodMonth != null) qw.eq(SlaReport::getPeriodMonth, periodMonth);
        qw.orderByDesc(SlaReport::getId).last("LIMIT 50");
        return Result.success(reportMapper.selectList(qw));
    }

    @GetMapping("/report/{key}")
    public Result<SlaReport> getReport(@PathVariable String key) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(reportMapper.selectOne(
                new LambdaQueryWrapper<SlaReport>()
                        .eq(SlaReport::getTenantId, tenantId)
                        .eq(SlaReport::getReportKey, key)));
    }

    // ====================================================================
    // 工具：计算可用性
    // ====================================================================

    public static BigDecimal calcAvailability(int totalMin, int downMin) {
        if (totalMin <= 0) return BigDecimal.valueOf(100);
        return BigDecimal.valueOf(totalMin - downMin)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalMin), 4, RoundingMode.HALF_UP);
    }

    public static int computeDowntimeBudget(BigDecimal targetPercent) {
        // 月度预算：30天 = 2,592,000s
        double allowedDowntimePct = 1 - targetPercent.doubleValue() / 100;
        return (int) (2_592_000 * allowedDowntimePct);
    }
}