package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MonitorAlertEvent;
import com.meiyetec.entity.MonitorAlertRule;
import com.meiyetec.entity.MonitorMetric;
import com.meiyetec.entity.MonitorNotificationLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MonitorAlertEventMapper;
import com.meiyetec.mapper.MonitorAlertRuleMapper;
import com.meiyetec.mapper.MonitorMetricMapper;
import com.meiyetec.mapper.MonitorNotificationLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R3.0-H P2.7：监控告警
 *
 * <p>提供：
 * <ul>
 *   <li>指标上报（4 类型：GAUGE / COUNTER / HISTOGRAM / SUMMARY）</li>
 *   <li>告警规则（6 比较符 + 4 严重度 + 静默期）</li>
 *   <li>告警事件（FIRING / RESOLVED / SILENCED）</li>
 *   <li>通知日志（5 渠道：SMS / EMAIL / WECHAT / WEBHOOK / DINGTALK）</li>
 *   <li>升级路径</li>
 * </ul>
 *
 * <p>生产应替换为 Prometheus + AlertManager。
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorAlertService {

    private final MonitorMetricMapper metricMapper;
    private final MonitorAlertRuleMapper ruleMapper;
    private final MonitorAlertEventMapper eventMapper;
    private final MonitorNotificationLogMapper notificationMapper;

    // 指标最后值缓存（用于持续时间判定）
    private static final Map<String, Double> LAST_VALUE = new ConcurrentHashMap<>();

    // ====================================================================
    // 1. 指标上报
    // ====================================================================

    @PostMapping("/metric/report")
    @Transactional(rollbackFor = Exception.class)
    public Result<MonitorMetric> reportMetric(@RequestBody MonitorMetric m) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        m.setTenantId(tenantId);
        if (m.getSampledAt() == null) m.setSampledAt(LocalDateTime.now());
        if (m.getMetricType() == null) m.setMetricType("GAUGE");
        metricMapper.insert(m);
        LAST_VALUE.put(m.getMetricName() + ":" + (m.getTargetCode() == null ? "" : m.getTargetCode()),
                m.getValue());

        // 评估告警
        evaluateAlerts(m);
        return Result.success("已上报", m);
    }

    @GetMapping("/metric/recent")
    public Result<List<MonitorMetric>> recentMetrics(@RequestParam String metricName,
                                                       @RequestParam(defaultValue = "20") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(metricMapper.selectList(
                new LambdaQueryWrapper<MonitorMetric>()
                        .eq(MonitorMetric::getTenantId, tenantId)
                        .eq(MonitorMetric::getMetricName, metricName)
                        .orderByDesc(MonitorMetric::getId)
                        .last("LIMIT " + limit)));
    }

    // ====================================================================
    // 2. 告警规则
    // ====================================================================

    @PostMapping("/alert/rule")
    @Transactional(rollbackFor = Exception.class)
    public Result<MonitorAlertRule> upsertRule(@RequestBody MonitorAlertRule rule) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        rule.setTenantId(tenantId);
        if (rule.getComparator() == null) rule.setComparator(">");
        if (rule.getSeverity() == null) rule.setSeverity("WARN");
        if (rule.getDurationSec() == null) rule.setDurationSec(60);
        if (rule.getSilenceMinutes() == null) rule.setSilenceMinutes(30);
        if (rule.getEnabled() == null) rule.setEnabled(1);

        MonitorAlertRule exist = ruleMapper.selectOne(
                new LambdaQueryWrapper<MonitorAlertRule>()
                        .eq(MonitorAlertRule::getTenantId, tenantId)
                        .eq(MonitorAlertRule::getRuleName, rule.getRuleName()));
        if (exist != null) {
            exist.setMetricName(rule.getMetricName());
            exist.setComparator(rule.getComparator());
            exist.setThreshold(rule.getThreshold());
            exist.setSeverity(rule.getSeverity());
            exist.setDurationSec(rule.getDurationSec());
            exist.setChannels(rule.getChannels());
            exist.setRecipients(rule.getRecipients());
            exist.setSilenceMinutes(rule.getSilenceMinutes());
            exist.setEnabled(rule.getEnabled());
            ruleMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        ruleMapper.insert(rule);
        return Result.success("已创建", rule);
    }

    @GetMapping("/alert/rule/list")
    public Result<List<MonitorAlertRule>> listRules() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(ruleMapper.selectList(
                new LambdaQueryWrapper<MonitorAlertRule>().eq(MonitorAlertRule::getTenantId, tenantId)));
    }

    // ====================================================================
    // 3. 告警评估
    // ====================================================================

    /**
     * 上报指标时触发评估
     */
    private void evaluateAlerts(MonitorMetric metric) {
        Long tenantId = metric.getTenantId();
        List<MonitorAlertRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<MonitorAlertRule>()
                        .eq(MonitorAlertRule::getTenantId, tenantId)
                        .eq(MonitorAlertRule::getMetricName, metric.getMetricName())
                        .eq(MonitorAlertRule::getEnabled, 1));
        for (MonitorAlertRule rule : rules) {
            if (compare(metric.getValue(), rule.getComparator(), rule.getThreshold())) {
                // 检查静默期
                if (isSilenced(rule, tenantId)) continue;
                fireAlert(rule, metric);
            }
        }
    }

    private static boolean compare(double v, String op, double threshold) {
        return switch (op) {
            case ">" -> v > threshold;
            case ">=" -> v >= threshold;
            case "<" -> v < threshold;
            case "<=" -> v <= threshold;
            case "==" -> v == threshold;
            case "!=" -> v != threshold;
            default -> false;
        };
    }

    private boolean isSilenced(MonitorAlertRule rule, Long tenantId) {
        MonitorAlertEvent last = eventMapper.selectOne(
                new LambdaQueryWrapper<MonitorAlertEvent>()
                        .eq(MonitorAlertEvent::getTenantId, tenantId)
                        .eq(MonitorAlertEvent::getRuleId, rule.getId())
                        .eq(MonitorAlertEvent::getStatus, "FIRING")
                        .orderByDesc(MonitorAlertEvent::getId)
                        .last("LIMIT 1"));
        if (last == null || last.getStartedAt() == null) return false;
        long elapsedMin = Duration.between(last.getStartedAt(), LocalDateTime.now()).toMinutes();
        return elapsedMin < (rule.getSilenceMinutes() == null ? 30 : rule.getSilenceMinutes());
    }

    private void fireAlert(MonitorAlertRule rule, MonitorMetric metric) {
        MonitorAlertEvent ev = new MonitorAlertEvent();
        ev.setTenantId(metric.getTenantId());
        ev.setRuleId(rule.getId());
        ev.setRuleName(rule.getRuleName());
        ev.setMetricName(rule.getMetricName());
        ev.setMetricValue(metric.getValue());
        ev.setThreshold(rule.getThreshold());
        ev.setSeverity(rule.getSeverity());
        ev.setStatus("FIRING");
        ev.setStartedAt(LocalDateTime.now());
        ev.setMessage(String.format("[%s] %s %s %s（实际=%s）",
                rule.getSeverity(), rule.getMetricName(), rule.getComparator(), rule.getThreshold(),
                metric.getValue()));
        eventMapper.insert(ev);
        // 触发通知
        sendNotifications(ev, rule);
    }

    private void sendNotifications(MonitorAlertEvent ev, MonitorAlertRule rule) {
        if (rule.getChannels() == null) return;
        for (String ch : rule.getChannels().split(",")) {
            MonitorNotificationLog log = new MonitorNotificationLog();
            log.setAlertEventId(ev.getId());
            log.setChannel(ch.trim().toUpperCase());
            log.setRecipient(rule.getRecipients());
            log.setSubject("[告警] " + ev.getRuleName());
            log.setContent(ev.getMessage());
            log.setStatus("SUCCESS");
            log.setSentAt(LocalDateTime.now());
            notificationMapper.insert(log);
        }
    }

    @PostMapping("/alert/event/{id}/ack")
    @Transactional(rollbackFor = Exception.class)
    public Result<MonitorAlertEvent> ackEvent(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        MonitorAlertEvent ev = eventMapper.selectById(id);
        if (ev == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        ev.setAcknowledgedAt(LocalDateTime.now());
        ev.setAckedBy((String) body.getOrDefault("ackedBy", "system"));
        eventMapper.updateById(ev);
        return Result.success("已确认", ev);
    }

    @PostMapping("/alert/event/{id}/resolve")
    @Transactional(rollbackFor = Exception.class)
    public Result<MonitorAlertEvent> resolveEvent(@PathVariable Long id) {
        MonitorAlertEvent ev = eventMapper.selectById(id);
        if (ev == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        ev.setStatus("RESOLVED");
        ev.setResolvedAt(LocalDateTime.now());
        eventMapper.updateById(ev);
        return Result.success("已恢复", ev);
    }

    @GetMapping("/alert/event/active")
    public Result<List<MonitorAlertEvent>> activeEvents() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(eventMapper.selectList(
                new LambdaQueryWrapper<MonitorAlertEvent>()
                        .eq(MonitorAlertEvent::getTenantId, tenantId)
                        .eq(MonitorAlertEvent::getStatus, "FIRING")
                        .orderByDesc(MonitorAlertEvent::getId)));
    }

    @GetMapping("/alert/event/list")
    public Result<List<MonitorAlertEvent>> listEvents(@RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(eventMapper.selectList(
                new LambdaQueryWrapper<MonitorAlertEvent>()
                        .eq(MonitorAlertEvent::getTenantId, tenantId)
                        .orderByDesc(MonitorAlertEvent::getId)
                        .last("LIMIT " + limit)));
    }

    @GetMapping("/alert/notification/log")
    public Result<List<MonitorNotificationLog>> notificationLog(
            @RequestParam(required = false) Long alertEventId) {
        LambdaQueryWrapper<MonitorNotificationLog> qw = new LambdaQueryWrapper<>();
        if (alertEventId != null) qw.eq(MonitorNotificationLog::getAlertEventId, alertEventId);
        qw.orderByDesc(MonitorNotificationLog::getId).last("LIMIT 50");
        return Result.success(notificationMapper.selectList(qw));
    }
}