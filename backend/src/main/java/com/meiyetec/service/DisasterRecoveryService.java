package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R3.0-H P2.7：应急容灾（DR）
 *
 * <p>提供：
 * <ul>
 *   <li>灾备站点（PRIMARY / STANDBY / DISASTER）</li>
 *   <li>故障事件（Incident）跟踪</li>
 *   <li>应急预案（Runbook）</li>
 *   <li>主备切换（SwitchOver）含 RPO/RTO 计算</li>
 *   <li>备份记录管理</li>
 * </ul>
 *
 * <p>生产应替换为云厂商 DR 平台 / Zerto / Veeam。
 */
@Slf4j
@RestController
@RequestMapping("/dr")
@RequiredArgsConstructor
public class DisasterRecoveryService {

    private final DrSiteMapper siteMapper;
    private final DrRunbookMapper runbookMapper;
    private final DrIncidentMapper incidentMapper;
    private final DrSwitchLogMapper switchLogMapper;
    private final DrBackupRecordMapper backupMapper;

    private static final AtomicLong INCIDENT_SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    // ====================================================================
    // 1. 灾备站点
    // ====================================================================

    @PostMapping("/site")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrSite> upsertSite(@RequestBody DrSite site) {
        if (site.getSiteCode() == null) return Result.error(BizCode.BAD_REQUEST, "siteCode 必填");
        DrSite exist = siteMapper.selectOne(
                new LambdaQueryWrapper<DrSite>().eq(DrSite::getSiteCode, site.getSiteCode()));
        if (exist != null) {
            exist.setSiteName(site.getSiteName());
            exist.setSiteType(site.getSiteType());
            exist.setRegion(site.getRegion());
            exist.setZone(site.getZone());
            exist.setEndpoint(site.getEndpoint());
            exist.setRole(site.getRole() == null ? "ACTIVE" : site.getRole());
            exist.setPriority(site.getPriority() == null ? 100 : site.getPriority());
            exist.setRpoSeconds(site.getRpoSeconds());
            exist.setRtoSeconds(site.getRtoSeconds());
            siteMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (site.getRole() == null) site.setRole("ACTIVE");
        if (site.getPriority() == null) site.setPriority(100);
        siteMapper.insert(site);
        return Result.success("已创建", site);
    }

    @GetMapping("/site/list")
    public Result<List<DrSite>> listSites() {
        return Result.success(siteMapper.selectList(
                new LambdaQueryWrapper<DrSite>().orderByAsc(DrSite::getPriority)));
    }

    @PostMapping("/site/{code}/activate")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrSite> activateSite(@PathVariable String code) {
        DrSite s = siteMapper.selectOne(
                new LambdaQueryWrapper<DrSite>().eq(DrSite::getSiteCode, code));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "站点不存在");
        s.setRole("ACTIVE");
        s.setLastHeartbeat(LocalDateTime.now());
        siteMapper.updateById(s);
        return Result.success("已激活", s);
    }

    // ====================================================================
    // 2. 故障事件
    // ====================================================================

    @PostMapping("/incident/report")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrIncident> reportIncident(@RequestBody DrIncident incident) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        incident.setTenantId(tenantId);
        if (incident.getIncidentNo() == null) {
            incident.setIncidentNo("INC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%05d", INCIDENT_SEQ.incrementAndGet()));
        }
        if (incident.getStatus() == null) incident.setStatus("OPEN");
        if (incident.getSeverity() == null) incident.setSeverity("P3");
        if (incident.getReportedAt() == null) incident.setReportedAt(LocalDateTime.now());
        incidentMapper.insert(incident);
        return Result.success("已上报", incident);
    }

    @PostMapping("/incident/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrIncident> updateIncidentStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        DrIncident i = incidentMapper.selectById(id);
        if (i == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        if (body.get("status") != null) i.setStatus((String) body.get("status"));
        if (body.get("rootCause") != null) i.setRootCause((String) body.get("rootCause"));
        if (body.get("resolution") != null) i.setResolution((String) body.get("resolution"));
        if ("RESOLVED".equals(i.getStatus())) {
            i.setResolvedAt(LocalDateTime.now());
            if (i.getReportedAt() != null) {
                i.setDurationSeconds((int) Duration.between(i.getReportedAt(), i.getResolvedAt()).getSeconds());
            }
        }
        incidentMapper.updateById(i);
        return Result.success("已更新", i);
    }

    @GetMapping("/incident/{id}")
    public Result<DrIncident> getIncident(@PathVariable Long id) {
        return Result.success(incidentMapper.selectById(id));
    }

    @GetMapping("/incident/list")
    public Result<List<DrIncident>> listIncidents(@RequestParam(defaultValue = "20") Integer limit,
                                                     @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DrIncident> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(DrIncident::getStatus, status);
        qw.orderByDesc(DrIncident::getId).last("LIMIT " + limit);
        return Result.success(incidentMapper.selectList(qw));
    }

    @GetMapping("/incident/stats")
    public Result<Map<String, Object>> incidentStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = incidentMapper.selectMaps(
                new LambdaQueryWrapper<DrIncident>()
                        .select(DrIncident::getStatus, DrIncident::getSeverity)
                        .eq(DrIncident::getTenantId, tenantId)
                        .orderByAsc(DrIncident::getId));
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String s = (String) r.get("status");
            String v = (String) r.get("severity");
            byStatus.merge(s, 1, Integer::sum);
            bySeverity.merge(v, 1, Integer::sum);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byStatus", byStatus);
        data.put("bySeverity", bySeverity);
        data.put("total", rows.size());
        return Result.success(data);
    }

    // ====================================================================
    // 3. 应急预案
    // ====================================================================

    @PostMapping("/runbook")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrRunbook> upsertRunbook(@RequestBody DrRunbook runbook) {
        if (runbook.getRunbookCode() == null) return Result.error(BizCode.BAD_REQUEST, "runbookCode 必填");
        DrRunbook exist = runbookMapper.selectOne(
                new LambdaQueryWrapper<DrRunbook>().eq(DrRunbook::getRunbookCode, runbook.getRunbookCode()));
        if (exist != null) {
            exist.setTitle(runbook.getTitle());
            exist.setCategory(runbook.getCategory());
            exist.setSeverity(runbook.getSeverity());
            exist.setDescription(runbook.getDescription());
            exist.setSteps(runbook.getSteps());
            exist.setEscalation(runbook.getEscalation());
            exist.setEstimatedMinutes(runbook.getEstimatedMinutes());
            exist.setLastReviewedAt(LocalDateTime.now());
            exist.setOwner(runbook.getOwner());
            runbookMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (runbook.getSeverity() == null) runbook.setSeverity("P2");
        if (runbook.getEnabled() == null) runbook.setEnabled(1);
        runbookMapper.insert(runbook);
        return Result.success("已创建", runbook);
    }

    @GetMapping("/runbook/list")
    public Result<List<DrRunbook>> listRunbooks(@RequestParam(required = false) String category) {
        LambdaQueryWrapper<DrRunbook> qw = new LambdaQueryWrapper<>();
        if (category != null) qw.eq(DrRunbook::getCategory, category);
        qw.orderByAsc(DrRunbook::getSeverity);
        return Result.success(runbookMapper.selectList(qw));
    }

    @GetMapping("/runbook/{code}")
    public Result<DrRunbook> getRunbook(@PathVariable String code) {
        return Result.success(runbookMapper.selectOne(
                new LambdaQueryWrapper<DrRunbook>().eq(DrRunbook::getRunbookCode, code)));
    }

    // ====================================================================
    // 4. 主备切换
    // ====================================================================

    @PostMapping("/switchover")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrSwitchLog> switchover(@RequestBody Map<String, Object> body) {
        Long fromSiteId = ((Number) body.get("fromSiteId")).longValue();
        Long toSiteId = ((Number) body.get("toSiteId")).longValue();
        String switchType = (String) body.getOrDefault("switchType", "PLANNED");
        String triggeredBy = (String) body.getOrDefault("triggeredBy", "system");
        String reason = (String) body.getOrDefault("reason", "manual");

        DrSite from = siteMapper.selectById(fromSiteId);
        DrSite to = siteMapper.selectById(toSiteId);
        if (from == null || to == null)
            return Result.error(BizCode.NOT_FOUND, "站点不存在");

        DrSwitchLog log = new DrSwitchLog();
        log.setFromSiteId(fromSiteId);
        log.setToSiteId(toSiteId);
        log.setSwitchType(switchType);
        log.setTriggeredBy(triggeredBy);
        log.setReason(reason);
        log.setStartedAt(LocalDateTime.now());
        log.setStatus("IN_PROGRESS");
        switchLogMapper.insert(log);

        // 模拟切换
        from.setRole("STANDBY");
        to.setRole("ACTIVE");
        to.setLastHeartbeat(LocalDateTime.now());
        siteMapper.updateById(from);
        siteMapper.updateById(to);

        // 计算 RPO / RTO
        long dataLossSec = Math.min(from.getRpoSeconds() != null ? from.getRpoSeconds() : 60, 60);
        long downtimeSec = Math.min(to.getRtoSeconds() != null ? to.getRtoSeconds() : 300, 300);

        log.setDataLossSeconds((int) dataLossSec);
        log.setDowntimeSeconds((int) downtimeSec);
        log.setCompletedAt(LocalDateTime.now());
        log.setStatus("SUCCESS");
        log.setDetail(String.format("Switched from %s to %s. RPO=%ds RTO=%ds",
                from.getSiteCode(), to.getSiteCode(), dataLossSec, downtimeSec));
        switchLogMapper.updateById(log);

        return Result.success("切换完成", log);
    }

    @GetMapping("/switchover/log")
    public Result<List<DrSwitchLog>> switchLog(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(switchLogMapper.selectList(
                new LambdaQueryWrapper<DrSwitchLog>().orderByDesc(DrSwitchLog::getId).last("LIMIT " + limit)));
    }

    // ====================================================================
    // 5. 备份记录
    // ====================================================================

    @PostMapping("/backup")
    @Transactional(rollbackFor = Exception.class)
    public Result<DrBackupRecord> createBackup(@RequestBody DrBackupRecord b) {
        if (b.getStartedAt() == null) b.setStartedAt(LocalDateTime.now());
        if (b.getStatus() == null) b.setStatus("SUCCESS");
        if (b.getDurationSeconds() == null && b.getCompletedAt() != null) {
            b.setDurationSeconds((int) Duration.between(b.getStartedAt(), b.getCompletedAt()).getSeconds());
        }
        backupMapper.insert(b);
        return Result.success("已记录", b);
    }

    @GetMapping("/backup/list")
    public Result<List<DrBackupRecord>> listBackups(@RequestParam(required = false) String backupTarget,
                                                       @RequestParam(defaultValue = "20") Integer limit) {
        LambdaQueryWrapper<DrBackupRecord> qw = new LambdaQueryWrapper<>();
        if (backupTarget != null) qw.eq(DrBackupRecord::getBackupTarget, backupTarget);
        qw.orderByDesc(DrBackupRecord::getId).last("LIMIT " + limit);
        return Result.success(backupMapper.selectList(qw));
    }

    // ====================================================================
    // 工具
    // ====================================================================

    /**
     * 计算 RPO / RTO 是否达标
     */
    public static boolean isRpoOk(DrSite site) {
        // 行业标准：RPO ≤ 60s（关键系统）；RTO ≤ 300s
        return site.getRpoSeconds() != null && site.getRpoSeconds() <= 60;
    }

    public static boolean isRtoOk(DrSite site) {
        return site.getRtoSeconds() != null && site.getRtoSeconds() <= 300;
    }
}