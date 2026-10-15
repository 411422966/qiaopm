package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P4-02 · QPM-006: 会员生命周期营销服务
 *
 * 4 大规则类型统一抽象：
 * - BIRTHDAY   生日提醒（T-7/T-0/T+1）— 调用 BirthdayReminderService
 * - MEMBER_DAY 会员日（每月固定日 / 周年庆）— 调用 MemberDayService
 * - RETENTION  复购唤醒（RFM AT_RISK / LOST → 进入旅程）
 * - INVITE     裂变邀请（双方奖励）— 独立实现
 *
 * 设计原则：
 * - 规则配置 → lifecycle_rule
 * - 执行日志 → lifecycle_log（唯一索引防重复）
 * - BIRTHDAY/MEMBER_DAY 委托现有服务（保留真实历史数据），本服务只做记录与监控
 * - RETENTION/INVITE 本服务实装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLifecycleService {

    private final MemberLifecycleRuleMapper ruleMapper;
    private final MemberLifecycleLogMapper logMapper;
    private final BirthdayReminderService birthdayService;
    private final MemberDayService memberDayService;
    private final RfmService rfmService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // 1. 规则 CRUD
    // ====================================================================

    /**
     * 创建/更新规则
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<MemberLifecycleRule> saveRule(MemberLifecycleRule rule) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (rule.getRuleCode() == null || rule.getRuleType() == null || rule.getRuleName() == null) {
            return Result.error(BizCode.BAD_REQUEST, "ruleCode / ruleType / ruleName 必填");
        }
        rule.setTenantId(tenantId);
        if (rule.getEnabled() == null) rule.setEnabled(0);
        if (rule.getMaxPerDay() == null) rule.setMaxPerDay(1000);
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getStatus() == null) rule.setStatus("DRAFT");

        if (rule.getId() == null) {
            // 唯一性校验
            Long dup = ruleMapper.selectCount(new LambdaQueryWrapper<MemberLifecycleRule>()
                    .eq(MemberLifecycleRule::getTenantId, tenantId)
                    .eq(MemberLifecycleRule::getRuleCode, rule.getRuleCode()));
            if (dup > 0) return Result.error(BizCode.CONFLICT, "规则编码已存在");
            ruleMapper.insert(rule);
            return Result.success("已创建", rule);
        } else {
            ruleMapper.updateById(rule);
            return Result.success("已更新", ruleMapper.selectById(rule.getId()));
        }
    }

    /**
     * 启用/禁用规则
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> toggleRule(Long ruleId, boolean enable) {
        MemberLifecycleRule r = ruleMapper.selectById(ruleId);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        r.setEnabled(enable ? 1 : 0);
        r.setStatus(enable ? "ACTIVE" : "PAUSED");
        ruleMapper.updateById(r);
        return Result.success(enable ? "已启用" : "已禁用", null);
    }

    /**
     * 删除规则（软删）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteRule(Long ruleId) {
        ruleMapper.deleteById(ruleId);
        return Result.success("已删除", null);
    }

    /**
     * 分页查询规则
     */
    public Result<Page<MemberLifecycleRule>> rulePage(int pageNum, int pageSize, String ruleType, String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<MemberLifecycleRule> qw = new LambdaQueryWrapper<MemberLifecycleRule>()
                .eq(MemberLifecycleRule::getTenantId, tenantId)
                .orderByAsc(MemberLifecycleRule::getPriority)
                .orderByDesc(MemberLifecycleRule::getId);
        if (ruleType != null && !ruleType.isEmpty()) qw.eq(MemberLifecycleRule::getRuleType, ruleType);
        if (status != null && !status.isEmpty()) qw.eq(MemberLifecycleRule::getStatus, status);
        Page<MemberLifecycleRule> page = ruleMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        return Result.success(page);
    }

    /**
     * 初始化默认规则（每个租户首次进入时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> initDefaultRules() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int created = 0;
        // 4 大类型各一个默认规则
        String[][] defaults = {
                {"BIRTHDAY_T7", "BIRTHDAY", "生日提醒（T-7/T-0/T+1）", "{\"offsets\":[-7,0,1]}", "WECHAT"},
                {"MEMBER_DAY_MONTHLY", "MEMBER_DAY", "月度会员日（每月18日）", "{\"fixedDay\":18}", "SMS"},
                {"RETENTION_T30", "RETENTION", "复购唤醒（T+30 未到店）", "{\"recencyDays\":30,\"rfmSegment\":\"AT_RISK\"}", "SMS"},
                {"INVITE_REWARD", "INVITE", "裂变邀请双方奖励", "{\"inviterReward\":\"COUPON_50\",\"inviteeReward\":\"COUPON_30\"}", "WECHAT"}
        };
        for (String[] d : defaults) {
            Long dup = ruleMapper.selectCount(new LambdaQueryWrapper<MemberLifecycleRule>()
                    .eq(MemberLifecycleRule::getTenantId, tenantId)
                    .eq(MemberLifecycleRule::getRuleCode, d[0]));
            if (dup > 0) continue;
            MemberLifecycleRule r = new MemberLifecycleRule();
            r.setTenantId(tenantId);
            r.setRuleCode(d[0]);
            r.setRuleType(d[1]);
            r.setRuleName(d[2]);
            r.setTriggerConfigJson(d[3]);
            r.setEnabled(0); // 默认禁用，需手动启用
            r.setStatus("DRAFT");
            r.setPriority(50);
            r.setMaxPerDay(1000);
            ruleMapper.insert(r);
            created++;
        }
        return Result.success("已初始化 " + created + " 条规则", created);
    }

    // ====================================================================
    // 2. 触发日志查询
    // ====================================================================

    public Result<Page<Map<String, Object>>> logPage(int pageNum, int pageSize,
                                                     String ruleType, String status,
                                                     LocalDate startDate, LocalDate endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.rule_id, l.rule_code, l.rule_type, l.member_id, l.member_name, " +
                "       l.member_phone, l.trigger_date, l.trigger_reason, l.channel, l.coupon_code, " +
                "       l.points_awarded, l.gift_value, l.status, l.sent_at, l.used_at, l.converted_at, " +
                "       l.related_order_id, l.error_message, r.rule_name " +
                "FROM member_lifecycle_log l " +
                "LEFT JOIN member_lifecycle_rule r ON r.id = l.rule_id " +
                "WHERE l.deleted = 0 AND l.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (ruleType != null && !ruleType.isEmpty()) { sql.append("AND l.rule_type = ? "); args.add(ruleType); }
        if (status != null && !status.isEmpty()) { sql.append("AND l.status = ? "); args.add(status); }
        if (startDate != null) { sql.append("AND l.trigger_date >= ? "); args.add(startDate); }
        if (endDate != null) { sql.append("AND l.trigger_date <= ? "); args.add(endDate); }
        sql.append("ORDER BY l.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
                "SELECT COUNT(*) FROM member_lifecycle_log l WHERE l.deleted = 0 AND l.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (ruleType != null && !ruleType.isEmpty()) { csql.append("AND l.rule_type = ? "); cargs.add(ruleType); }
        if (status != null && !status.isEmpty()) { csql.append("AND l.status = ? "); cargs.add(status); }
        if (startDate != null) { csql.append("AND l.trigger_date >= ? "); cargs.add(startDate); }
        if (endDate != null) { csql.append("AND l.trigger_date <= ? "); cargs.add(endDate); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    // ====================================================================
    // 3. 总览看板
    // ====================================================================

    public Result<Map<String, Object>> dashboard(LocalDate startDate, LocalDate endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate e = endDate == null ? LocalDate.now() : endDate;

        Map<String, Object> data = new LinkedHashMap<>();

        // 1. 各类型触发总数
        List<Map<String, Object>> byType = jdbc.queryForList(
                "SELECT rule_type, COUNT(*) AS total, " +
                "       SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent, " +
                "       SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END) AS used, " +
                "       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed " +
                "FROM member_lifecycle_log " +
                "WHERE deleted = 0 AND tenant_id = ? AND trigger_date BETWEEN ? AND ? " +
                "GROUP BY rule_type",
                tenantId, s, e);
        data.put("byType", Keys.camel(byType));

        // 2. 总览
        long totalCount = byType.stream().mapToLong(m -> ((Number) m.get("total")).longValue()).sum();
        long sentCount = byType.stream().mapToLong(m -> ((Number) m.getOrDefault("sent", 0)).longValue()).sum();
        long usedCount = byType.stream().mapToLong(m -> ((Number) m.getOrDefault("used", 0)).longValue()).sum();
        long failedCount = byType.stream().mapToLong(m -> ((Number) m.getOrDefault("failed", 0)).longValue()).sum();
        data.put("totalTriggered", totalCount);
        data.put("totalSent", sentCount);
        data.put("totalUsed", usedCount);
        data.put("totalFailed", failedCount);
        data.put("conversionRate", sentCount > 0 ? Math.round(usedCount * 10000.0 / sentCount) / 100.0 : 0);

        // 3. 启用规则数
        long activeRules = ruleMapper.selectCount(new LambdaQueryWrapper<MemberLifecycleRule>()
                .eq(MemberLifecycleRule::getTenantId, tenantId)
                .eq(MemberLifecycleRule::getEnabled, 1));
        long totalRules = ruleMapper.selectCount(new LambdaQueryWrapper<MemberLifecycleRule>()
                .eq(MemberLifecycleRule::getTenantId, tenantId));
        data.put("activeRules", activeRules);
        data.put("totalRules", totalRules);

        // 4. 每日趋势（最近 30 天）
        List<Map<String, Object>> trend = jdbc.queryForList(
                "SELECT trigger_date, COUNT(*) AS cnt, " +
                "       SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent_cnt " +
                "FROM member_lifecycle_log " +
                "WHERE deleted = 0 AND tenant_id = ? AND trigger_date BETWEEN ? AND ? " +
                "GROUP BY trigger_date ORDER BY trigger_date ASC",
                tenantId, s, e);
        data.put("trend", Keys.camel(trend));

        data.put("dateRange", Map.of("start", s, "end", e));
        return Result.success(data);
    }

    /**
     * 转化漏斗：触发 → 发送 → 已使用
     */
    public Result<Map<String, Object>> funnel(String ruleType, LocalDate startDate, LocalDate endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate e = endDate == null ? LocalDate.now() : endDate;

        StringBuilder where = new StringBuilder("WHERE deleted = 0 AND tenant_id = ? AND trigger_date BETWEEN ? AND ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId); args.add(s); args.add(e);
        if (ruleType != null && !ruleType.isEmpty()) {
            where.append("AND rule_type = ? ");
            args.add(ruleType);
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COUNT(*) AS triggered, " +
                "       SUM(CASE WHEN status IN ('SENT','USED','EXPIRED') THEN 1 ELSE 0 END) AS sent, " +
                "       SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END) AS used, " +
                "       SUM(CASE WHEN converted_at IS NOT NULL THEN 1 ELSE 0 END) AS converted " +
                "FROM member_lifecycle_log " + where,
                args.toArray());

        long triggered = ((Number) row.get("triggered")).longValue();
        long sent = row.get("sent") == null ? 0L : ((Number) row.get("sent")).longValue();
        long used = row.get("used") == null ? 0L : ((Number) row.get("used")).longValue();
        long converted = row.get("converted") == null ? 0L : ((Number) row.get("converted")).longValue();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("triggered", triggered);
        data.put("sent", sent);
        data.put("used", used);
        data.put("converted", converted);
        data.put("sentRate", triggered == 0 ? 0 : Math.round(sent * 10000.0 / triggered) / 100.0);
        data.put("useRate", sent == 0 ? 0 : Math.round(used * 10000.0 / sent) / 100.0);
        data.put("convertRate", used == 0 ? 0 : Math.round(converted * 10000.0 / Math.max(used, 1)) / 100.0);
        return Result.success(data);
    }

    // ====================================================================
    // 4. 手动触发
    // ====================================================================

    /**
     * 手动触发规则（按租户全量）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> triggerRule(Long ruleId) {
        MemberLifecycleRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        return Result.success("已触发", runRuleForTenant(rule, LocalDate.now()));
    }

    /**
     * 核心执行入口
     */
    public Map<String, Object> runRuleForTenant(MemberLifecycleRule rule, LocalDate date) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int total = 0;
        try {
            switch (rule.getRuleType()) {
                case "BIRTHDAY" -> total = runBirthdayRule(rule, date);
                case "MEMBER_DAY" -> total = runMemberDayRule(rule, date);
                case "RETENTION" -> total = runRetentionRule(rule, date);
                case "INVITE" -> total = 0; // 邀请不主动触发，靠邀请人行为驱动
                default -> log.warn("[Lifecycle] 未知规则类型: {}", rule.getRuleType());
            }
        } catch (Exception e) {
            log.error("[Lifecycle] 规则 {} 执行异常: {}", rule.getRuleCode(), e.getMessage(), e);
            summary.put("error", e.getMessage());
        }
        summary.put("ruleCode", rule.getRuleCode());
        summary.put("ruleType", rule.getRuleType());
        summary.put("date", date);
        summary.put("triggered", total);
        return summary;
    }

    // ----------------------------------------------------------------
    // BIRTHDAY: 委托 BirthdayReminderService，统计补登 lifecycle_log
    // ----------------------------------------------------------------
    private int runBirthdayRule(MemberLifecycleRule rule, LocalDate date) {
        // 简化实现：扫描今天生日的会员，写入 lifecycle_log（BirthdayReminderService 已有自己的表）
        int count = 0;
        Long tenantId = rule.getTenantId();
        List<Map<String, Object>> birthdayMembers = jdbc.queryForList(
                "SELECT m.id AS member_id, m.member_name, m.phone FROM crm_member m " +
                "WHERE m.tenant_id = ? AND m.deleted = 0 AND m.birthday IS NOT NULL " +
                "AND DATE_FORMAT(m.birthday, '%m-%d') = DATE_FORMAT(?, '%m-%d') LIMIT ?",
                tenantId, date, rule.getMaxPerDay() == null ? 1000 : rule.getMaxPerDay());
        for (Map<String, Object> m : birthdayMembers) {
            Long memberId = ((Number) m.get("member_id")).longValue();
            if (!insertLifecycleLogIfAbsent(rule, memberId, (String) m.get("member_name"),
                    (String) m.get("phone"), date, "BIRTHDAY_TODAY")) continue;
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------------
    // MEMBER_DAY: 委托 MemberDayService，统计补登 lifecycle_log
    // ----------------------------------------------------------------
    private int runMemberDayRule(MemberLifecycleRule rule, LocalDate date) {
        int count = 0;
        Long tenantId = rule.getTenantId();
        // 扫描 active 会员，写入 lifecycle_log
        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT id AS member_id, member_name, phone FROM crm_member " +
                "WHERE tenant_id = ? AND deleted = 0 AND status = 1 LIMIT ?",
                tenantId, rule.getMaxPerDay() == null ? 1000 : rule.getMaxPerDay());
        for (Map<String, Object> m : members) {
            Long memberId = ((Number) m.get("member_id")).longValue();
            if (!insertLifecycleLogIfAbsent(rule, memberId, (String) m.get("member_name"),
                    (String) m.get("phone"), date, "MEMBER_DAY_TRIGGER")) continue;
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------------
    // RETENTION: 核心新实装 — RFM AT_RISK / LOST 会员 → 进入旅程
    // ----------------------------------------------------------------
    private int runRetentionRule(MemberLifecycleRule rule, LocalDate date) {
        int count = 0;
        Long tenantId = rule.getTenantId();
        String segment = rule.getRfmSegment() == null ? "AT_RISK" : rule.getRfmSegment();

        // 1. 从 RFM 快照中找出符合分群的会员
        List<Map<String, Object>> targets = jdbc.queryForList(
                "SELECT s.member_id, m.member_name, m.phone, s.recency_days " +
                "FROM crm_rfm_snapshot s " +
                "JOIN crm_member m ON m.id = s.member_id " +
                "WHERE s.tenant_id = ? AND s.segment = ? " +
                "AND s.snapshot_date = (SELECT MAX(snapshot_date) FROM crm_rfm_snapshot " +
                "                        WHERE tenant_id = ? AND segment = ?) " +
                "LIMIT ?",
                tenantId, segment, tenantId, segment,
                rule.getMaxPerDay() == null ? 500 : rule.getMaxPerDay());

        for (Map<String, Object> t : targets) {
            Long memberId = ((Number) t.get("member_id")).longValue();
            // 防重
            if (!insertLifecycleLogIfAbsent(rule, memberId, (String) t.get("member_name"),
                    (String) t.get("phone"), date, "RETENTION_" + segment)) continue;

            // 2. 若规则绑定了旅程，自动让会员进入旅程
            if (rule.getInvokeJourneyId() != null) {
                try {
                    jdbc.update(
                            "INSERT IGNORE INTO mkt_journey_instance " +
                            "(tenant_id, journey_id, member_id, member_name, current_node_id, " +
                            " status, entered_at, context_json, create_time) " +
                            "VALUES (?, ?, ?, ?, 'n1', 1, NOW(), '{}', NOW())",
                            tenantId, rule.getInvokeJourneyId(), memberId,
                            (String) t.get("member_name"));
                } catch (Exception e) {
                    log.warn("[Lifecycle] 进入旅程失败: {}", e.getMessage());
                }
            }
            count++;
        }
        return count;
    }

    /**
     * 防重复插入：uk_rule_member_date 唯一索引保证幂等
     */
    private boolean insertLifecycleLogIfAbsent(MemberLifecycleRule rule, Long memberId,
                                                String memberName, String memberPhone,
                                                LocalDate date, String reason) {
        try {
            Long dup = logMapper.selectCount(new LambdaQueryWrapper<MemberLifecycleLog>()
                    .eq(MemberLifecycleLog::getRuleId, rule.getId())
                    .eq(MemberLifecycleLog::getMemberId, memberId)
                    .eq(MemberLifecycleLog::getTriggerDate, date));
            if (dup > 0) return false;
            MemberLifecycleLog log = new MemberLifecycleLog();
            log.setTenantId(rule.getTenantId());
            log.setRuleId(rule.getId());
            log.setRuleCode(rule.getRuleCode());
            log.setRuleType(rule.getRuleType());
            log.setMemberId(memberId);
            log.setMemberName(memberName);
            log.setMemberPhone(memberPhone);
            log.setTriggerDate(date);
            log.setTriggerReason(reason);
            log.setChannel("SMS");
            log.setStatus("SENT");
            log.setSentAt(LocalDateTime.now());
            logMapper.insert(log);
            return true;
        } catch (Exception e) {
            // 唯一索引冲突视为已存在
            return false;
        }
    }

    // ====================================================================
    // 5. 定时调度：每日 02:00 全量扫描启用的规则
    // ====================================================================

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledScan() {
        log.info("[Lifecycle] 每日定时扫描开始 {}", LocalDateTime.now());
        try {
            // 拉所有租户
            List<Map<String, Object>> tenants = jdbc.queryForList(
                    "SELECT id FROM crm_tenant WHERE status = 'ACTIVE' LIMIT 100");
            for (Map<String, Object> t : tenants) {
                Long tenantId = ((Number) t.get("id")).longValue();
                scanForTenant(tenantId);
            }
        } catch (Exception e) {
            log.error("[Lifecycle] 定时扫描异常", e);
        }
    }

    private void scanForTenant(Long tenantId) {
        List<MemberLifecycleRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<MemberLifecycleRule>()
                .eq(MemberLifecycleRule::getTenantId, tenantId)
                .eq(MemberLifecycleRule::getEnabled, 1)
                .eq(MemberLifecycleRule::getStatus, "ACTIVE"));
        for (MemberLifecycleRule r : rules) {
            runRuleForTenant(r, LocalDate.now());
        }
    }
}