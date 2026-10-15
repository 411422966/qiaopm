package com.meiyetec.crm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmContactMap;
import com.meiyetec.entity.CrmIntegration;
import com.meiyetec.entity.CrmSyncLog;
import com.meiyetec.mapper.CrmIntegrationMapper;
import com.meiyetec.mapper.CrmSyncLogMapper;
import com.meiyetec.service.CrmContactMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EXT-15：CRM 同步排程（默认每 30 分钟一次增量）
 *
 * <p>每条 crm_integration 行都有一条 cron 表达式（sync_schedule，默认 "0 *&#x2F;30 * * * *"）。
 * 但所有集成一起跑用一个调度循环 + 每条自己决定是否触发（基于 lastIncrementalSyncAt 与 cron 间隔）。
 *
 * <p>实际策略：
 * <ol>
 *   <li>扫描所有 status=ACTIVE 且 sync_enabled=1 的集成</li>
 *   <li>每条根据 lastIncrementalSyncAt + syncWindowMinutes 判断是否到期</li>
 *   <li>调用对应 adapter 做增量拉取 → 写 crm_contact_map + crm_sync_log</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmSyncScheduler {

    private final CrmAdapterRouter router;
    private final CrmIntegrationMapper integrationMapper;
    private final CrmContactMapService contactMapService;
    private final CrmSyncLogMapper syncLogMapper;

    /**
     * 主排程 — 每 5 分钟触发一次（精细度），由本类自己判定每条集成是否到期。
     * cron 表达式来自 crm_integration.sync_schedule 字段。
     */
    @Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}",
               initialDelay = 60_000L)
    public void tick() {
        List<CrmIntegration> enabled;
        try {
            enabled = integrationMapper.selectList(new LambdaQueryWrapper<CrmIntegration>()
                    .eq(CrmIntegration::getStatus, "ACTIVE")
                    .eq(CrmIntegration::getSyncEnabled, 1));
        } catch (Exception e) {
            log.warn("[CRM-Scheduler] 拉取集成列表失败：{}", e.getMessage());
            return;
        }
        if (enabled.isEmpty()) return;
        for (CrmIntegration cfg : enabled) {
            try {
                if (!shouldRun(cfg)) continue;
                runIncremental(cfg);
            } catch (Exception e) {
                log.warn("[CRM-Scheduler] 集成 {} 同步失败：{}", cfg.getPlatform(), e.getMessage());
            }
        }
    }

    /**
     * 是否到该跑的时机：lastIncrementalSyncAt + syncWindowMinutes <= now
     * <p>EXT-24：暴露为 public，供 {@code CrmIntegrationService.syncNow} 调用。
     */
    public boolean shouldRun(CrmIntegration cfg) {
        Integer window = cfg.getSyncWindowMinutes() == null ? 30 : cfg.getSyncWindowMinutes();
        LocalDateTime last = cfg.getLastIncrementalSyncAt();
        if (last == null) return true;
        return last.plusMinutes(window).isBefore(LocalDateTime.now());
    }

    /**
     * EXT-24：手动触发专用判断。
     * <p>与排程相同语义 — 但允许调用方通过 separate {@code force} 参数绕过（由 controller 处理）。
     */
    public boolean shouldRunForManualTrigger(CrmIntegration cfg) {
        return shouldRun(cfg);
    }

    public CrmSyncLog runIncremental(CrmIntegration cfg) {
        return runIncremental(cfg, "INCREMENTAL");
    }

    /**
     * 执行一次增量同步：拉取 → 写 crm_contact_map → 更新 sync_token
     *
     * @param cfg        集成配置
     * @param syncType   日志类型，{@code INCREMENTAL}（排程）或 {@code MANUAL}（手动）
     */
    public CrmSyncLog runIncremental(CrmIntegration cfg, String syncType) {
        long start = System.currentTimeMillis();
        CrmSyncLog logRow = new CrmSyncLog();
        logRow.setTenantId(cfg.getTenantId());
        logRow.setPlatform(cfg.getPlatform());
        logRow.setSyncType(syncType == null ? "INCREMENTAL" : syncType);
        logRow.setDirection(cfg.getSyncDirection());
        logRow.setStatus("RUNNING");
        // I083 fix: 默认 0 初始化，避免 catch 分支未赋值导致下游 Integer.intValue() NPE
        logRow.setSuccessCount(0);
        logRow.setFailCount(0);
        logRow.setRecordsTotal(0);
        logRow.setRecordsFiltered(0);
        syncLogMapper.insert(logRow);

        try {
            CrmAdapter adapter = router.getAdapter(cfg.getPlatform());
            CrmAdapter.SyncCursor cursor = new CrmAdapter.SyncCursor();
            cursor.sinceEpochMs = cfg.getLastIncrementalSyncAt() == null
                    ? null
                    : cfg.getLastIncrementalSyncAt()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            cursor.syncToken = cfg.getSyncToken();
            // EXT-24：填充已存在的 externalId 集合，让适配器做"差集"增量
            cursor.knownExternalIds = loadKnownExternalIds(cfg);

            List<CrmAdapter.ExternalContact> contacts = adapter.fetchContactsIncremental(cfg, cursor);

            int ok = 0, fail = 0;
            for (CrmAdapter.ExternalContact ec : contacts) {
                try {
                    upsertContactMap(cfg, ec);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                }
            }

            cfg.setLastIncrementalSyncAt(LocalDateTime.now());
            cfg.setLastSyncAt(LocalDateTime.now());
            cfg.setStatus("ACTIVE");
            cfg.setErrorMessage(null);
            integrationMapper.updateById(cfg);

            logRow.setSuccessCount(ok);
            logRow.setFailCount(fail);
            logRow.setRecordsTotal(contacts.size());
            logRow.setRecordsFiltered(contacts.size());
            logRow.setStatus("SUCCESS");
            logRow.setDurationMs((int) (System.currentTimeMillis() - start));
            logRow.setSyncToken(cfg.getSyncToken());
            syncLogMapper.updateById(logRow);
            log.info("[CRM-Scheduler] 集成 {} 增量同步完成：total={} ok={} fail={} elapsed={}ms",
                    cfg.getPlatform(), contacts.size(), ok, fail,
                    System.currentTimeMillis() - start);
            return logRow;
        } catch (Exception e) {
            logRow.setStatus("FAILED");
            logRow.setErrorMessage(e.getMessage());
            logRow.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLogMapper.updateById(logRow);
            cfg.setStatus("ERROR");
            cfg.setErrorMessage(e.getMessage());
            integrationMapper.updateById(cfg);
            log.warn("[CRM-Scheduler] 集成 {} 失败：{}", cfg.getPlatform(), e.getMessage());
            return logRow;
        }
    }

    private void upsertContactMap(CrmIntegration cfg, CrmAdapter.ExternalContact ec) {
        CrmContactMap existing = contactMapService.getOne(new LambdaQueryWrapper<CrmContactMap>()
                .eq(CrmContactMap::getPlatform, cfg.getPlatform())
                .eq(CrmContactMap::getExternalId, ec.externalId));

        if (existing == null) {
            CrmContactMap m = new CrmContactMap();
            m.setTenantId(cfg.getTenantId());
            m.setPlatform(cfg.getPlatform());
            m.setExternalId(ec.externalId);
            m.setExternalEtag(ec.etag);
            m.setSyncDirection("INBOUND");
            m.setLastInboundAt(LocalDateTime.now());
            m.setLastFullSyncAt(LocalDateTime.now());
            contactMapService.save(m);
        } else {
            existing.setExternalEtag(ec.etag);
            existing.setLastInboundAt(LocalDateTime.now());
            existing.setLastFullSyncAt(LocalDateTime.now());
            existing.setConflictFlag(0);
            contactMapService.updateById(existing);
        }
    }

    /**
     * EXT-24：从 {@code crm_contact_map} 拉出某 tenant + platform 的全部 externalId。
     * <p>用于"差集"增量：把已知 ID 传给 adapter，让其跳过已入库的部分。
     * <p>size 上限保护：返回的 ID 集合受 {@link #KNOWN_IDS_LIMIT} 限制，避免 SOQL {@code WHERE Id NOT IN (10K+)} 性能问题。
     */
    public static final int KNOWN_IDS_LIMIT = 5000;

    java.util.Set<String> loadKnownExternalIds(CrmIntegration cfg) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            List<CrmContactMap> all = contactMapService.list(new LambdaQueryWrapper<CrmContactMap>()
                    .eq(CrmContactMap::getTenantId, cfg.getTenantId())
                    .eq(CrmContactMap::getPlatform, cfg.getPlatform())
                    .select(CrmContactMap::getExternalId)
                    .last("LIMIT " + KNOWN_IDS_LIMIT));
            for (CrmContactMap m : all) {
                if (m.getExternalId() != null) out.add(m.getExternalId());
            }
            if (!out.isEmpty()) {
                log.debug("[CRM-Scheduler] loadKnownExternalIds: tenant={} platform={} count={}",
                        cfg.getTenantId(), cfg.getPlatform(), out.size());
            }
        } catch (Exception e) {
            log.warn("[CRM-Scheduler] loadKnownExternalIds failed: {}", e.getMessage());
        }
        return out;
    }
}
