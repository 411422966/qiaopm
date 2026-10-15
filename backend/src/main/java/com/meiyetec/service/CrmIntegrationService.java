package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.crm.CrmSyncScheduler;
import com.meiyetec.entity.CrmIntegration;
import com.meiyetec.entity.CrmSyncLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CrmIntegrationMapper;
import com.meiyetec.mapper.CrmSyncLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * CRM 集成（FEATURE_COMPARISON_MATRIX.md 280-287 P3-24）
 *
 * <p>EXT-24 升级：{@code /{id}/sync} 不再写空 log，直接复用 {@link CrmSyncScheduler#runIncremental} 排程逻辑。
 */
@Slf4j
@RestController
@RequestMapping("/crm-integration")
@RequiredArgsConstructor
public class CrmIntegrationService {

    private final CrmIntegrationMapper integrationMapper;
    private final CrmSyncLogMapper syncLogMapper;
    private final CrmSyncScheduler crmSyncScheduler;

    // I005-C4：platform 枚举校验（与 EXT-24 §0.6 / MockAdapter @ConditionalOnMissingBean fallback 对齐）
    // 前端 el-select 不显示 MOCK（仅 dev/内部 fallback），但后端仍接受 — 防止脏数据绕过前端直接 POST
    private static final Set<String> ALLOWED_PLATFORMS = Set.of("SALESFORCE", "HUBSPOT", "ZOHO", "MOCK");

    @PostMapping
    public Result<CrmIntegration> create(@RequestBody CrmIntegration i) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (i.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
        // I005-C4 (c)：platform 枚举校验
        if (!ALLOWED_PLATFORMS.contains(i.getPlatform())) {
            return Result.error(BizCode.BAD_REQUEST, "platform 仅支持 " + ALLOWED_PLATFORMS + "，当前：" + i.getPlatform());
        }
        if (i.getStatus() == null) i.setStatus("ACTIVE");
        // I005-C4 (b)：syncWindowMinutes 默认 30（与 CrmSyncScheduler.shouldRun 窗口语义一致）
        if (i.getSyncWindowMinutes() == null) i.setSyncWindowMinutes(30);
        // I005-C4 (a)：objectMappings 必须是合法 JSON；留空视为通过（后端 parseObjectMappings 自动降级 DEFAULT_FIELD_MAP）
        if (i.getObjectMappings() != null && !i.getObjectMappings().isEmpty()) {
            try {
                com.meiyetec.util.ObjectMapperHolder.MAPPER.readTree(i.getObjectMappings());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // getOriginalMessage 去除 [Source: ...] 位置信息，纯错误消息
                return Result.error(BizCode.BAD_REQUEST, "objectMappings JSON 格式错误：" + e.getOriginalMessage());
            } catch (Exception e) {
                return Result.error(BizCode.BAD_REQUEST, "objectMappings JSON 解析异常：" + e.getMessage());
            }
        }
        i.setTenantId(tenantId);
        i.setId(null);
        integrationMapper.insert(i);
        return Result.success("已创建", i);
    }

    @GetMapping("/list")
    public Result<List<CrmIntegration>> list(@RequestParam(required = false) String platform,
                                            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CrmIntegration> qw = new LambdaQueryWrapper<CrmIntegration>()
                .eq(CrmIntegration::getTenantId, tenantId)
                .orderByDesc(CrmIntegration::getId);
        if (platform != null && !platform.isEmpty()) qw.eq(CrmIntegration::getPlatform, platform);
        if (status != null && !status.isEmpty()) qw.eq(CrmIntegration::getStatus, status);
        return Result.success(integrationMapper.selectList(qw));
    }

    @PostMapping("/{id}")
    public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        i.setId(id);
        i.setTenantId(tenantId);
        integrationMapper.updateById(i);
        // 不调 selectById — 避免 I002 缺列抛错导致 Fake Failure（数据已 UPDATE 但 API 报 500）。
        // UPDATE 已成功提交，用入参 i（含本次提交字段）作为返回 data。
        return Result.success("已更新", i);
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmIntegration i = integrationMapper.selectById(id);
        if (i == null) return Result.error(BizCode.NOT_FOUND, "集成不存在");
        // I005-C1：toggle 入口 enum 校验，仅允许 EXT-24 §4 合法值（ACTIVE / DISABLED / ERROR）
        // - ACTIVE/DISABLED：用户主动切换
        // - ERROR：由 CrmSyncScheduler 同步失败时自动写入，禁止前端走 toggle 路径伪造
        String next = (String) body.get("status");
        if (!"ACTIVE".equals(next) && !"DISABLED".equals(next) && !"ERROR".equals(next)) {
            return Result.error(BizCode.BAD_REQUEST, "status 仅支持 ACTIVE / DISABLED / ERROR");
        }
        i.setStatus(next);
        integrationMapper.updateById(i);
        return Result.success("已切换", null);
    }

    /**
     * EXT-24：手动触发增量同步。
     * <p>复用 {@link CrmSyncScheduler#runIncremental}，返回真实同步日志（而非之前的空 log）。
     * <p>支持 {@code ?force=true} 跳过 {@link CrmSyncScheduler#shouldRun} 的 cron 窗口检查。
     *
     * @param force 1/true = 强制执行；空/0/false = 按 lastIncrementalSyncAt 判断（默认）
     */
    @PostMapping("/{id}/sync")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncNow(@PathVariable Long id,
                                               @RequestParam(required = false) Integer force) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CrmIntegration i = integrationMapper.selectById(id);
        if (i == null) return Result.error(BizCode.NOT_FOUND, "集成不存在");
        // 租户隔离：不带 dataScope=ALL 时，跨租户访问应失败
        if (!"ALL".equals(TenantContextHolder.get().getDataScope())
                && i.getTenantId() != null && !i.getTenantId().equals(tenantId)) {
            return Result.error(BizCode.FORBIDDEN, "跨租户访问被拒绝");
        }

        // 默认尊重排程窗口；?force=true 强制执行
        boolean forced = force != null && force == 1;
        if (!forced && !crmSyncScheduler.shouldRunForManualTrigger(i)) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("integrationId", id);
            skipped.put("skipped", true);
            skipped.put("reason", "not in cron window, use ?force=1 to override");
            skipped.put("lastIncrementalSyncAt", i.getLastIncrementalSyncAt());
            skipped.put("syncWindowMinutes", i.getSyncWindowMinutes());
            return Result.success("未到期，跳过", skipped);
        }

        // 真正调用排程逻辑
        CrmSyncLog realLog = crmSyncScheduler.runIncremental(i, "MANUAL");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", realLog == null ? null : realLog.getId());
        data.put("integrationId", id);
        data.put("platform", i.getPlatform());
        data.put("status", realLog == null ? "FAILED" : realLog.getStatus());
        data.put("successCount", realLog == null ? 0 : realLog.getSuccessCount());
        data.put("failCount", realLog == null ? 0 : realLog.getFailCount());
        data.put("recordsTotal", realLog == null ? 0 : realLog.getRecordsTotal());
        data.put("durationMs", realLog == null ? 0 : realLog.getDurationMs());
        data.put("errorMessage", realLog == null ? null : realLog.getErrorMessage());
        data.put("forced", forced);
        if (realLog != null && !"FAILED".equals(realLog.getStatus())) {
            return Result.success("已同步", data);
        }
        return Result.error(BizCode.SYSTEM_ERROR, "同步失败：" + (realLog == null ? "no log" : realLog.getErrorMessage()));
    }

    @GetMapping("/sync-log/list")
    public Result<List<CrmSyncLog>> listSyncLogs(@RequestParam(required = false) Long integrationId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CrmSyncLog> qw = new LambdaQueryWrapper<CrmSyncLog>()
                .eq(CrmSyncLog::getTenantId, tenantId)
                .orderByDesc(CrmSyncLog::getId);
        if (integrationId != null) {
            // 通过 integration 的 platform 字段过滤
            CrmIntegration integ = integrationMapper.selectById(integrationId);
            if (integ != null) qw.eq(CrmSyncLog::getPlatform, integ.getPlatform());
        }
        return Result.success(syncLogMapper.selectList(qw));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalIntegrations", integrationMapper.selectCount(new LambdaQueryWrapper<CrmIntegration>()
                    .eq(CrmIntegration::getTenantId, tenantId)));
            data.put("totalSyncLogs", syncLogMapper.selectCount(new LambdaQueryWrapper<CrmSyncLog>()
                    .eq(CrmSyncLog::getTenantId, tenantId)));
        } catch (Exception e) {
            log.error("[CRM-DASHBOARD] count failed for tenantId={}", tenantId, e);
        }
        return Result.success(data);
    }
}