package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.config.KioskAlertProperties;
import com.meiyetec.entity.KioskDevice;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.mapper.KioskDeviceMapper;
import com.meiyetec.mapper.MktChannelConfigMapper;
import com.meiyetec.mapper.MktChannelTemplateMapper;
import com.meiyetec.service.channel.ChannelDispatcher;
import com.meiyetec.service.channel.ChannelSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiosk 离线告警推送服务（EXT-P2）
 *
 * <p>职责：
 * <ol>
 *   <li>每 {@code kiosk.alert.scan-interval-ms} 毫秒扫描所有租户的活跃设备</li>
 *   <li>对 ACTIVE 但超过 {@code kiosk.alert.info-minutes} 未心跳的设备触发推送</li>
 *   <li>通过 {@link ChannelDispatcher} 按租户配置的渠道（IN_APP / SMS / MINI_PROGRAM）下发</li>
 *   <li>同一设备在 {@code kiosk.alert.cooldown-minutes} 内不重复推送（避免告警风暴）</li>
 * </ol>
 *
 * <p>不依赖 request/response 线程，可在 @PostConstruct 启动后台扫描。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KioskOfflineAlertService {

    private final KioskDeviceMapper        deviceMapper;
    private final KioskAlertProperties     alertProps;
    private final ChannelDispatcher        channelDispatcher;

    @Autowired(required = false)
    private MktChannelConfigMapper  channelConfigMapper;
    @Autowired(required = false)
    private MktChannelTemplateMapper templateMapper;

    /**
     * 推送冷却记录：deviceCode → lastPushedAt
     * 同一设备在 cooldown 内不重复推送
     */
    private final ConcurrentHashMap<String, LocalDateTime> cooldownMap = new ConcurrentHashMap<>();

    /**
     * 推送日志（in-memory 队列，最近 100 条）— 便于排障 + 前端查询
     */
    private final LinkedList<Map<String, Object>> recentLogs = new LinkedList<>();
    private static final int MAX_LOGS = 100;

    /**
     * 兜底租户列表：当 tenant_id 维度无法在 SQL 中取到时，用 sys_tenant 缓存
     * （实际生产建议从 SysTenantMapper 注入；此处走最简路径：所有 device 的 tenantId）
     */
    @PostConstruct
    public void init() {
        log.info("[KioskAlert] init enabled={} pushEnabled={} scanInterval={}ms thresholds=INFO:{}min WARN:{}min CRITICAL:{}min",
                alertProps.isEnabled(),
                alertProps.isPushEnabled(),
                alertProps.getScanIntervalMs(),
                alertProps.getInfoMinutes(),
                alertProps.getWarnMinutes(),
                alertProps.getCriticalMinutes());
    }

    /**
     * 周期扫描（fixedDelay 单位 ms）。
     * <p>默认从 alertProps 读取间隔；如需硬编码可注释下行 + 写 {@code @Scheduled(fixedDelay = 60000)}。
     */
    @Scheduled(fixedDelayString = "${kiosk.alert.scan-interval-ms:60000}", initialDelay = 30_000L)
    public void scanOfflineDevices() {
        if (!alertProps.isEnabled()) return;
        try {
            List<KioskDevice> all = deviceMapper.selectList(new LambdaQueryWrapper<KioskDevice>()
                    .eq(KioskDevice::getStatus, "ACTIVE"));
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(alertProps.getInfoMinutes());

            for (KioskDevice d : all) {
                if (d.getLastHeartbeat() != null && d.getLastHeartbeat().isAfter(threshold)) {
                    continue;   // 在线
                }
                long offlineMin = (d.getLastHeartbeat() == null) ? -1
                        : ChronoUnit.MINUTES.between(d.getLastHeartbeat(), LocalDateTime.now());
                handleOffline(d, offlineMin);
            }
        } catch (Exception ex) {
            log.error("[KioskAlert] scan error", ex);
        }
    }

    private void handleOffline(KioskDevice d, long offlineMin) {
        String severity = alertProps.severityOf(offlineMin);
        if ("OK".equals(severity)) return;     // 在 INFO 阈值内不告警

        // 冷却检查
        LocalDateTime last = cooldownMap.get(d.getDeviceCode());
        LocalDateTime now  = LocalDateTime.now();
        if (last != null && ChronoUnit.MINUTES.between(last, now) < alertProps.getCooldownMinutes()) {
            log.debug("[KioskAlert] cooldown active device={} skip", d.getDeviceCode());
            return;
        }

        // 记录日志（即使不推送也写）
        appendLog(d, severity, offlineMin, "DETECTED");

        // 推送（受 push-enabled 控制）
        if (!alertProps.isPushEnabled()) {
            log.debug("[KioskAlert] push disabled, skip device={}", d.getDeviceCode());
            return;
        }

        try {
            boolean sent = sendOfflineAlert(d, severity, offlineMin);
            cooldownMap.put(d.getDeviceCode(), now);
            appendLog(d, severity, offlineMin, sent ? "SENT" : "FAILED");
        } catch (Exception ex) {
            log.error("[KioskAlert] push failed device={}", d.getDeviceCode(), ex);
            appendLog(d, severity, offlineMin, "ERROR: " + ex.getMessage());
        }
    }

    /**
     * 实际推送：通过 ChannelDispatcher 找到租户启用的渠道 + 模板。
     * <p>当前实现：取租户 + 渠道 = alertProps.getPushChannel() 的 enabled 配置；
     * 若无配置，走 in-memory 兜底（仅记录日志）。
     */
    private boolean sendOfflineAlert(KioskDevice d, String severity, long offlineMin) {
        String channelType = alertProps.getPushChannel();
        Long tenantId = d.getTenantId();
        if (tenantId == null) {
            log.warn("[KioskAlert] device {} has no tenantId, skip", d.getDeviceCode());
            return false;
        }
        ChannelSender sender = channelDispatcher.getSender(channelType);
        if (sender == null) {
            log.warn("[KioskAlert] no sender for channel={} (device={})", channelType, d.getDeviceCode());
            return false;
        }

        // 找模板（templateCode = KIOSK_OFFLINE_ALERT），找不到就用兜底内容
        MktChannelTemplate tpl = null;
        if (templateMapper != null) {
            try {
                tpl = templateMapper.selectOne(new LambdaQueryWrapper<MktChannelTemplate>()
                        .eq(MktChannelTemplate::getTenantId, tenantId)
                        .eq(MktChannelTemplate::getChannelType, channelType)
                        .eq(MktChannelTemplate::getTemplateCode, "KIOSK_OFFLINE_ALERT")
                        .eq(MktChannelTemplate::getEnabled, 1)
                        .last("LIMIT 1"));
            } catch (Exception ignored) {}
        }

        // 兜底模板
        if (tpl == null) {
            tpl = new MktChannelTemplate();
            tpl.setSubject("【Kiosk 告警】设备离线");
            tpl.setContent("设备 ${deviceCode}（${location}）已离线 ${minutes} 分钟，严重度 ${severity}。请尽快处理。");
            tpl.setChannelType(channelType);
            tpl.setTenantId(tenantId);
        }

        // 找接收人：默认取设备的 storeId → 在 mkt_channel_config 中找 receiver（缺省为 "ops"）
        String receiver = resolveReceiver(tenantId, d);
        if (receiver == null || receiver.isEmpty()) {
            log.info("[KioskAlert] no receiver configured for tenant={} device={}, fallback to ops",
                    tenantId, d.getDeviceCode());
            receiver = "ops";
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("deviceCode", d.getDeviceCode());
        vars.put("deviceName", d.getDeviceName());
        vars.put("location",   d.getLocation());
        vars.put("minutes",    offlineMin < 0 ? "未知" : String.valueOf(offlineMin));
        vars.put("severity",   severity);
        vars.put("lastHeartbeat", d.getLastHeartbeat() == null ? "从未心跳" : d.getLastHeartbeat().toString());

        ChannelSender.SendResult r = sender.send(tpl, receiver, vars);
        if (r.success) {
            log.info("[KioskAlert] sent device={} channel={} extId={}",
                    d.getDeviceCode(), channelType, r.externalMsgId);
        } else {
            log.warn("[KioskAlert] send failed device={} channel={} err={}",
                    d.getDeviceCode(), channelType, r.errorMsg);
        }
        return r.success;
    }

    /**
     * 取租户配置的告警接收人：
     * 1) mkt_channel_config.configJson.alertReceiver（IN_APP 时是 userId，SMS 是手机号，MINI 是 openId）
     * 2) 缺省 → 0（IN_APP 兜底：所有运维）
     */
    private String resolveReceiver(Long tenantId, KioskDevice d) {
        if (channelConfigMapper == null) return null;
        try {
            MktChannelConfig cfg = channelConfigMapper.selectOne(new LambdaQueryWrapper<MktChannelConfig>()
                    .eq(MktChannelConfig::getTenantId, tenantId)
                    .eq(MktChannelConfig::getChannelType, alertProps.getPushChannel())
                    .eq(MktChannelConfig::getStatus, 1)
                    .last("LIMIT 1"));
            if (cfg == null || cfg.getConfigJson() == null) return null;
            // 简单提取：alertReceiver 字段（JSON 解析可选；此处直接关键字匹配）
            String json = cfg.getConfigJson();
            int idx = json.indexOf("alertReceiver");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote1 > 0 && quote2 > quote1) {
                return json.substring(quote1 + 1, quote2);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void appendLog(KioskDevice d, String severity, long offlineMin, String action) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("deviceCode",    d.getDeviceCode());
        log.put("tenantId",      d.getTenantId());
        log.put("severity",      severity);
        log.put("offlineMinutes", offlineMin);
        log.put("action",        action);
        log.put("at",            LocalDateTime.now());
        synchronized (recentLogs) {
            recentLogs.addFirst(log);
            while (recentLogs.size() > MAX_LOGS) recentLogs.removeLast();
        }
    }

    /** 暴露给运维查询 */
    public List<Map<String, Object>> recentLogs() {
        synchronized (recentLogs) {
            return new ArrayList<>(recentLogs);
        }
    }

    /** 暴露给运维查询当前冷却表 */
    public Map<String, LocalDateTime> cooldownMap() {
        return new LinkedHashMap<>(cooldownMap);
    }
}
