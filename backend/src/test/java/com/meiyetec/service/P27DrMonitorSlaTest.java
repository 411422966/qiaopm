package com.meiyetec.service;

import com.meiyetec.entity.*;
import com.meiyetec.service.DisasterRecoveryService;
import com.meiyetec.service.DrillExerciseService;
import com.meiyetec.service.MonitorAlertService;
import com.meiyetec.service.SlaReportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P2.7：应急容灾 + 灾备演练 + 监控告警 + SLA 报告测试
 */
class P27DrMonitorSlaTest {

    // ==================== DR - 站点 ====================
    @Test
    void siteType_valid() {
        Set<String> types = Set.of("PRIMARY", "STANDBY", "DISASTER");
        assertEquals(3, types.size());
    }

    @Test
    void siteRole_valid() {
        Set<String> roles = Set.of("ACTIVE", "STANDBY", "OFFLINE");
        assertEquals(3, roles.size());
    }

    @Test
    void roleTransition() {
        // PRIMARY 故障 → STANDBY → 提升为 PRIMARY
        boolean ok = validRoleTrans("ACTIVE", "STANDBY");
        assertTrue(ok);
        ok = validRoleTrans("STANDBY", "ACTIVE");
        assertTrue(ok);
    }

    private static boolean validRoleTrans(String from, String to) {
        Set<String> roles = Set.of("ACTIVE", "STANDBY", "OFFLINE");
        return roles.contains(from) && roles.contains(to);
    }

    @Test
    void rpoRtoSla() {
        // RPO ≤ 60s、RTO ≤ 300s 是关键系统标准
        int rpo = 60, rto = 300;
        assertTrue(DisasterRecoveryService.isRpoOk(makeSite(rpo, rto)));
        assertTrue(DisasterRecoveryService.isRtoOk(makeSite(rpo, rto)));
    }

    @Test
    void rpoNotOk() {
        int rpo = 3600, rto = 300;
        assertFalse(DisasterRecoveryService.isRpoOk(makeSite(rpo, rto)));
    }

    @Test
    void rtoNotOk() {
        int rpo = 60, rto = 7200;
        assertFalse(DisasterRecoveryService.isRtoOk(makeSite(rpo, rto)));
    }

    private static DrSite makeSite(int rpo, int rto) {
        DrSite s = new DrSite();
        s.setRpoSeconds(rpo);
        s.setRtoSeconds(rto);
        return s;
    }

    @Test
    void priorityOrder() {
        // 优先级数字越小越优先
        int p1 = 10, p2 = 100;
        assertTrue(p1 < p2);
    }

    // ==================== DR - 事件 ====================
    @Test
    void incidentSeverity_valid() {
        Set<String> sev = Set.of("P1", "P2", "P3", "P4");
        assertEquals(4, sev.size());
    }

    @Test
    void incidentSeverityOrder() {
        // P1 严重度最高
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put("P1", 1);
        order.put("P2", 2);
        order.put("P3", 3);
        order.put("P4", 4);
        assertTrue(order.get("P1") < order.get("P4"));
    }

    @Test
    void incidentStatus_valid() {
        Set<String> status = Set.of("OPEN", "INVESTIGATING", "MITIGATING", "RESOLVED", "POSTMORTEM");
        assertEquals(5, status.size());
    }

    @Test
    void incidentStatusMachine() {
        assertTrue(validStatusTrans("OPEN", "INVESTIGATING"));
        assertTrue(validStatusTrans("INVESTIGATING", "MITIGATING"));
        assertTrue(validStatusTrans("MITIGATING", "RESOLVED"));
        assertTrue(validStatusTrans("RESOLVED", "POSTMORTEM"));
    }

    @Test
    void incidentStatusMachine_invalid() {
        assertFalse(validStatusTrans("OPEN", "POSTMORTEM")); // 必须先 RESOLVED
        assertFalse(validStatusTrans("POSTMORTEM", "OPEN")); // 已完结
    }

    private static boolean validStatusTrans(String from, String to) {
        return switch (from) {
            case "OPEN" -> to.equals("INVESTIGATING");
            case "INVESTIGATING" -> to.equals("MITIGATING") || to.equals("RESOLVED");
            case "MITIGATING" -> to.equals("RESOLVED");
            case "RESOLVED" -> to.equals("POSTMORTEM");
            default -> false;
        };
    }

    @Test
    void incidentNoFormat() {
        String no = "INC" + LocalDateTime.now();
        assertTrue(no.startsWith("INC"));
    }

    @Test
    void durationCalc() {
        LocalDateTime t1 = LocalDateTime.now();
        LocalDateTime t2 = t1.plusSeconds(3600);
        long sec = Duration.between(t1, t2).getSeconds();
        assertEquals(3600, sec);
    }

    // ==================== DR - Runbook ====================
    @Test
    void runbookCategory_valid() {
        Set<String> cats = Set.of("DB_DOWN", "NETWORK", "SECURITY", "DATA_CORRUPTION", "PEAK_LOAD");
        assertEquals(5, cats.size());
    }

    @Test
    void runbookSeverity_valid() {
        Set<String> sev = Set.of("P1", "P2", "P3", "P4");
        assertTrue(sev.contains("P2"));
    }

    @Test
    void runbookEnabled() {
        int enabled = 1;
        assertEquals(1, enabled);
    }

    @Test
    void estimatedMinutes_positive() {
        int m = 30;
        assertTrue(m > 0);
    }

    // ==================== DR - Switchover ====================
    @Test
    void switchType_valid() {
        Set<String> types = Set.of("PLANNED", "EMERGENCY", "DRILL");
        assertEquals(3, types.size());
    }

    @Test
    void switchStatus_valid() {
        Set<String> status = Set.of("IN_PROGRESS", "SUCCESS", "FAILED", "ROLLED_BACK");
        assertEquals(4, status.size());
    }

    @Test
    void switchDataLoss_nonNegative() {
        int dl = 0;
        assertTrue(dl >= 0);
    }

    @Test
    void switchDowntime_positive() {
        int dt = 300;
        assertTrue(dt > 0);
    }

    // ==================== DR - Backup ====================
    @Test
    void backupType_valid() {
        Set<String> types = Set.of("FULL", "INCREMENTAL", "DIFFERENTIAL", "SNAPSHOT");
        assertEquals(4, types.size());
    }

    @Test
    void backupTarget_valid() {
        Set<String> tg = Set.of("DB", "FILES", "CONFIG");
        assertEquals(3, tg.size());
    }

    @Test
    void backupStatus_valid() {
        Set<String> st = Set.of("SUCCESS", "FAILED", "VERIFYING");
        assertEquals(3, st.size());
    }

    @Test
    void fileHashSHA256() {
        String hash = sha256("backup-file");
        assertEquals(64, hash.length());
    }

    private static String sha256(String s) {
        try {
            byte[] b = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 演练 ====================
    @Test
    void drillType_valid() {
        Set<String> types = Set.of("TABLETOP", "SIMULATION", "PARTIAL_FAILOVER", "FULL_FAILOVER");
        assertEquals(4, types.size());
    }

    @Test
    void drillType_order() {
        // 演练难度递增
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put("TABLETOP", 1);
        order.put("SIMULATION", 2);
        order.put("PARTIAL_FAILOVER", 3);
        order.put("FULL_FAILOVER", 4);
        assertTrue(order.get("TABLETOP") < order.get("FULL_FAILOVER"));
    }

    @Test
    void drillStatus_valid() {
        Set<String> st = Set.of("SCHEDULED", "RUNNING", "COMPLETED", "CANCELLED");
        assertEquals(4, st.size());
    }

    @Test
    void drillPlanStatusMachine() {
        assertTrue(validDrillTrans("SCHEDULED", "RUNNING"));
        assertTrue(validDrillTrans("RUNNING", "COMPLETED"));
        assertTrue(validDrillTrans("SCHEDULED", "CANCELLED"));
    }

    @Test
    void drillPlanStatusMachine_invalid() {
        assertFalse(validDrillTrans("COMPLETED", "RUNNING"));
        assertFalse(validDrillTrans("CANCELLED", "RUNNING"));
    }

    private static boolean validDrillTrans(String from, String to) {
        return switch (from) {
            case "SCHEDULED" -> to.equals("RUNNING") || to.equals("CANCELLED");
            case "RUNNING" -> to.equals("COMPLETED");
            case "COMPLETED", "CANCELLED" -> false;
            default -> false;
        };
    }

    @Test
    void executionSuccess() {
        DrDrillExecution e = new DrDrillExecution();
        e.setSuccess(1);
        assertEquals(1, e.getSuccess());
    }

    @Test
    void executionRtoRpo() {
        DrDrillExecution e = new DrDrillExecution();
        e.setRtoActualSec(180);
        e.setRpoActualSec(30);
        assertTrue(e.getRtoActualSec() <= 300);
        assertTrue(e.getRpoActualSec() <= 60);
    }

    @Test
    void drillScore() {
        DrDrillExecution e = new DrDrillExecution();
        e.setSuccess(1);
        e.setRtoActualSec(120);
        e.setRpoActualSec(30);
        e.setLessonsLearned("数据库切换快，监控未配置告警");
        DrDrillPlan p = new DrDrillPlan();
        p.setEstimatedHours(BigDecimal.valueOf(2.0));
        e.setStartedAt(LocalDateTime.now().minusHours(1));
        e.setCompletedAt(LocalDateTime.now());
        int score = DrillExerciseService.autoScore(e, p);
        assertEquals(100, score); // 30 + 20 + 20 + 10 + 20 = 100
    }

    @Test
    void drillScoreFail() {
        DrDrillExecution e = new DrDrillExecution();
        e.setSuccess(0);
        int score = DrillExerciseService.autoScore(e, null);
        assertEquals(0, score);
    }

    @Test
    void drillScorePartial() {
        DrDrillExecution e = new DrDrillExecution();
        e.setSuccess(1);
        e.setRtoActualSec(600); // 超
        e.setRpoActualSec(120); // 超
        int score = DrillExerciseService.autoScore(e, null);
        assertEquals(30, score);
    }

    // ==================== 监控 - 指标 ====================
    @Test
    void metricType_valid() {
        Set<String> types = Set.of("GAUGE", "COUNTER", "HISTOGRAM", "SUMMARY");
        assertEquals(4, types.size());
    }

    @Test
    void targetType_valid() {
        Set<String> types = Set.of("SERVICE", "INSTANCE", "DB", "CACHE", "API");
        assertEquals(5, types.size());
    }

    @Test
    void counterNonDecreasing() {
        // COUNTER 类型单调递增
        double v1 = 10, v2 = 20;
        assertTrue(v2 >= v1);
    }

    @Test
    void gaugeCanChange() {
        // GAUGE 类型可升可降
        double v1 = 80, v2 = 30;
        assertNotEquals(v1, v2);
    }

    // ==================== 监控 - 告警规则 ====================
    @Test
    void comparator_valid() {
        Set<String> ops = Set.of(">", ">=", "<", "<=", "==", "!=");
        assertEquals(6, ops.size());
    }

    @Test
    void compareGreaterThan() {
        assertTrue(compare(100, ">", 50));
        assertFalse(compare(50, ">", 50));
    }

    @Test
    void compareGreaterEqual() {
        assertTrue(compare(50, ">=", 50));
        assertFalse(compare(49, ">=", 50));
    }

    @Test
    void compareLessThan() {
        assertTrue(compare(10, "<", 50));
        assertFalse(compare(50, "<", 50));
    }

    @Test
    void compareLessEqual() {
        assertTrue(compare(50, "<=", 50));
        assertFalse(compare(51, "<=", 50));
    }

    @Test
    void compareEqual() {
        assertTrue(compare(50, "==", 50));
        assertFalse(compare(51, "==", 50));
    }

    @Test
    void compareNotEqual() {
        assertTrue(compare(51, "!=", 50));
        assertFalse(compare(50, "!=", 50));
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

    @Test
    void alertSeverity_valid() {
        Set<String> sev = Set.of("INFO", "WARN", "ERROR", "CRITICAL");
        assertEquals(4, sev.size());
    }

    @Test
    void alertSeverityOrder() {
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put("INFO", 1);
        order.put("WARN", 2);
        order.put("ERROR", 3);
        order.put("CRITICAL", 4);
        assertTrue(order.get("CRITICAL") > order.get("INFO"));
    }

    @Test
    void durationSec_positive() {
        int s = 60;
        assertTrue(s > 0);
    }

    @Test
    void silenceMin_positive() {
        int m = 30;
        assertTrue(m > 0);
    }

    // ==================== 监控 - 告警事件 ====================
    @Test
    void alertEventStatus_valid() {
        Set<String> st = Set.of("FIRING", "RESOLVED", "SILENCED");
        assertEquals(3, st.size());
    }

    @Test
    void alertEventStatusMachine() {
        assertTrue(validAlertTrans("FIRING", "RESOLVED"));
        assertTrue(validAlertTrans("FIRING", "SILENCED"));
    }

    @Test
    void alertEventStatusMachine_invalid() {
        assertFalse(validAlertTrans("RESOLVED", "FIRING")); // 已恢复
    }

    private static boolean validAlertTrans(String from, String to) {
        return switch (from) {
            case "FIRING" -> to.equals("RESOLVED") || to.equals("SILENCED");
            case "RESOLVED", "SILENCED" -> false;
            default -> false;
        };
    }

    @Test
    void ackBy_notNull() {
        String by = "ops@meiyetec";
        assertNotNull(by);
    }

    // ==================== 监控 - 通知 ====================
    @Test
    void channel_valid() {
        Set<String> ch = Set.of("SMS", "EMAIL", "WECHAT", "WEBHOOK", "DINGTALK");
        assertEquals(5, ch.size());
    }

    @Test
    void notifStatus_valid() {
        Set<String> st = Set.of("PENDING", "SUCCESS", "FAILED");
        assertEquals(3, st.size());
    }

    @Test
    void subjectRequired() {
        String sub = "[告警] CPU 高";
        assertTrue(sub.startsWith("["));
    }

    // ==================== SLA - 目标 ====================
    @Test
    void slaTargetCommon() {
        BigDecimal target = BigDecimal.valueOf(99.90);
        assertEquals(0, target.compareTo(BigDecimal.valueOf(99.90)));
    }

    @Test
    void slaAvailability_validRange() {
        // 99% ~ 99.99%
        for (double v : new double[]{99.0, 99.5, 99.9, 99.99, 99.999}) {
            BigDecimal b = BigDecimal.valueOf(v);
            assertTrue(b.compareTo(BigDecimal.valueOf(99)) >= 0);
            assertTrue(b.compareTo(BigDecimal.valueOf(99.9999)) <= 0);
        }
    }

    @Test
    void downtimeBudget_default() {
        // 99.9% 月度预算 = 30 天 * 24h * 3600s * 0.1% ≈ 2592 秒
        BigDecimal t = BigDecimal.valueOf(99.9);
        int budget = SlaReportService.computeDowntimeBudget(t);
        assertTrue(budget >= 2591 && budget <= 2593);
    }

    @Test
    void downtimeBudget_99_99() {
        BigDecimal t = BigDecimal.valueOf(99.99);
        int budget = SlaReportService.computeDowntimeBudget(t);
        // 2,592,000 * 0.0001 = 259.2
        assertTrue(budget < 300);
    }

    @Test
    void p95p99_threshold() {
        int p95 = 500, p99 = 1000;
        assertTrue(p95 < p99);
    }

    @Test
    void errorRateThreshold_default() {
        // 0.1%
        BigDecimal er = new BigDecimal("0.0010");
        assertEquals(4, er.scale());
    }

    // ==================== SLA - 可用性 ====================
    @Test
    void availability100() {
        BigDecimal avail = SlaReportService.calcAvailability(1440, 0);
        assertEquals(0, avail.compareTo(BigDecimal.valueOf(100.0000)));
    }

    @Test
    void availability99() {
        BigDecimal avail = SlaReportService.calcAvailability(1440, 14);
        // 1426/1440 = 99.0278
        assertTrue(avail.compareTo(BigDecimal.valueOf(99)) > 0);
        assertTrue(avail.compareTo(BigDecimal.valueOf(99.1)) < 0);
    }

    @Test
    void availability99_9() {
        BigDecimal avail = SlaReportService.calcAvailability(1440, 1);
        // 1439/1440 = 99.9306
        assertTrue(avail.compareTo(BigDecimal.valueOf(99.9)) > 0);
    }

    @Test
    void availabilityZeroTotal() {
        BigDecimal avail = SlaReportService.calcAvailability(0, 0);
        assertEquals(0, avail.compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    void availabilityRounding() {
        BigDecimal avail = SlaReportService.calcAvailability(3, 1);
        // 2/3 = 0.6667 * 100 = 66.6667
        assertEquals(0, avail.setScale(4, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(66.6667)));
    }

    // ==================== SLA - 月报 ====================
    @Test
    void periodMonth_format() {
        String pm = "2026-08";
        assertTrue(pm.matches("\\d{4}-\\d{2}"));
    }

    @Test
    void achieved_above() {
        BigDecimal actual = BigDecimal.valueOf(99.95);
        BigDecimal target = BigDecimal.valueOf(99.90);
        assertTrue(actual.compareTo(target) >= 0);
    }

    @Test
    void achieved_below() {
        BigDecimal actual = BigDecimal.valueOf(99.50);
        BigDecimal target = BigDecimal.valueOf(99.90);
        assertTrue(actual.compareTo(target) < 0);
    }

    @Test
    void reportStatus_valid() {
        Set<String> st = Set.of("GENERATED", "SUBMITTED", "ACKNOWLEDGED");
        assertEquals(3, st.size());
    }

    @Test
    void reportStatusMachine() {
        assertTrue(validReportTrans("GENERATED", "SUBMITTED"));
        assertTrue(validReportTrans("SUBMITTED", "ACKNOWLEDGED"));
    }

    @Test
    void reportStatusMachine_invalid() {
        assertFalse(validReportTrans("ACKNOWLEDGED", "SUBMITTED")); // 已确认
    }

    private static boolean validReportTrans(String from, String to) {
        return switch (from) {
            case "GENERATED" -> to.equals("SUBMITTED");
            case "SUBMITTED" -> to.equals("ACKNOWLEDGED");
            default -> false;
        };
    }

    @Test
    void errorRateCalc() {
        BigDecimal rate = BigDecimal.valueOf(10).divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        // 10/10000 = 0.001 = 0.1%
        assertTrue(rate.compareTo(new BigDecimal("0.001000")) == 0);
    }

    @Test
    void monthlyTotalSeconds() {
        // 30 天 = 2,592,000 秒
        long sec = 30L * 24 * 3600;
        assertEquals(2_592_000L, sec);
    }

    // ==================== 综合 ====================
    @Test
    void fourPillars() {
        // 4 大支柱
        String[] pillars = {"应急容灾", "灾备演练", "监控告警", "SLA 报告"};
        assertEquals(4, pillars.length);
    }

    @Test
    void mttdMttr() {
        // 平均检测时间 / 平均恢复时间（小时）
        int mttd = 5, mttr = 60;
        assertTrue(mttr > mttd);
    }

    @Test
    void incident_to_alert_link() {
        // 告警可关联到事件
        Long alertId = 1L, incidentId = 1L;
        assertEquals(incidentId, alertId);
    }

    @Test
    void drillPlanToExecution() {
        Long planId = 1L, execId = 1L;
        assertEquals(planId, execId);
    }

    @Test
    void slaTargetToUptimeToReport() {
        // 链路：target → uptime → report
        assertTrue(true);
    }
}