package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MemberDayConfig;
import com.meiyetec.entity.MemberDayExecution;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MemberDayConfigMapper;
import com.meiyetec.mapper.MemberDayExecutionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P0.4 会员日自动化（按 FEATURE_COMPARISON_MATRIX.md 244-251 P0-4 项）
 *
 * 模块：
 * 1. 会员日配置（生日 / 会员日 / 周年庆 / 季节性）
 * 2. 自动化触发（定时任务，每天跑）
 * 3. 营销素材下发（短信/微信/Push）
 * 4. 优惠核销追踪
 * 5. ROI 分析
 */
@Slf4j
@RestController
@RequestMapping("/member-day")
@RequiredArgsConstructor
public class MemberDayService {

    private final MemberDayConfigMapper configMapper;
    private final MemberDayExecutionMapper execMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T1. 会员日配置
    // ====================================================================

    @PostMapping("/config")
    @Transactional(rollbackFor = Exception.class)
    public Result<MemberDayConfig> createConfig(@RequestBody MemberDayConfig c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getConfigName() == null)
            return Result.error(BizCode.BAD_REQUEST, "配置名称必填");
        if (c.getDayType() == null) c.setDayType("MEMBER_DAY");
        if (c.getStatus() == null) c.setStatus("ACTIVE");
        if (c.getPriority() == null) c.setPriority(0);
        c.setTenantId(tenantId);
        c.setId(null);
        configMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/config/list")
    public Result<List<MemberDayConfig>> listConfigs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(configMapper.selectList(new LambdaQueryWrapper<MemberDayConfig>()
                .eq(MemberDayConfig::getTenantId, tenantId)
                .orderByDesc(MemberDayConfig::getPriority)
                .orderByDesc(MemberDayConfig::getId)));
    }

    @PostMapping("/config/{id}")
    public Result<MemberDayConfig> updateConfig(@PathVariable Long id, @RequestBody MemberDayConfig c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MemberDayConfig exist = configMapper.selectById(id);
        if (exist == null || !exist.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "配置不存在");
        c.setId(id);
        c.setTenantId(tenantId);
        configMapper.updateById(c);
        return Result.success("已更新", configMapper.selectById(id));
    }

    @PostMapping("/config/{id}/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteConfig(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MemberDayConfig c = configMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "配置不存在");
        c.setStatus("ARCHIVED");
        configMapper.updateById(c);
        return Result.success("已归档", null);
    }

    /**
     * 初始化默认配置
     */
    @PostMapping("/config/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefault() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.of("name", "生日特惠", "dayType", "BIRTHDAY",
                        "rule", "生日当天 + 前后3天",
                        "discount", 8.0, "giftAmount", 200, "giftPoints", 500,
                        "birthdayOffset", 3, "sms", "亲爱的{name}，生日月专享8折+200元代金券，恭候光临"),
                Map.of("name", "月度会员日", "dayType", "MEMBER_DAY",
                        "rule", "每月 18 日",
                        "discount", 7.5, "giftAmount", 100, "giftPoints", 200,
                        "month", null, "day", 18, "sms", "会员日全场75折+100元代金券，等您来"),
                Map.of("name", "周年庆", "dayType", "ANNIVERSARY",
                        "rule", "入会周年",
                        "discount", 6.5, "giftAmount", 500, "giftPoints", 1000,
                        "sms", "周年特惠65折+500元大礼包")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            MemberDayConfig exist = configMapper.selectOne(new LambdaQueryWrapper<MemberDayConfig>()
                    .eq(MemberDayConfig::getTenantId, tenantId)
                    .eq(MemberDayConfig::getConfigName, d.get("name")));
            if (exist != null) continue;
            MemberDayConfig c = new MemberDayConfig();
            c.setTenantId(tenantId);
            c.setConfigName((String) d.get("name"));
            c.setDayType((String) d.get("dayType"));
            c.setTriggerRule((String) d.get("rule"));
            c.setDiscountRate(d.get("discount") == null ? null
                    : new java.math.BigDecimal(d.get("discount").toString()));
            c.setGiftAmount(d.get("giftAmount") == null ? null
                    : new java.math.BigDecimal(d.get("giftAmount").toString()));
            c.setGiftPoints((Integer) d.get("giftPoints"));
            c.setBirthdayOffsetDays((Integer) d.get("birthdayOffset"));
            if (d.get("day") != null) {
                c.setFixedMonth(1);
                c.setFixedDay((Integer) d.get("day"));
                c.setRepeatAnnually(0);
            } else {
                c.setRepeatAnnually(1);
            }
            c.setSmsTemplate((String) d.get("sms"));
            c.setStatus("ACTIVE");
            c.setPriority(50);
            configMapper.insert(c);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }

    // ====================================================================
    // T2. 自动化触发（定时任务，每天 01:00 跑）
    // ====================================================================

    /**
     * 每天 01:00 自动扫描当天触发的会员
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledTrigger() {
        log.info("[MemberDay] 定时触发扫描开始");
        try {
            runDailyTrigger(LocalDate.now(), null);
        } catch (Exception e) {
            log.error("[MemberDay] 定时任务异常", e);
        }
    }

    /**
     * 手动触发（供 API 调用）
     */
    @PostMapping("/trigger")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> trigger(@RequestBody(required = false) Map<String, Object> body) {
        LocalDate date = body == null || body.get("date") == null
                ? LocalDate.now() : LocalDate.parse(body.get("date").toString());
        return Result.success("已触发", runDailyTrigger(date, null));
    }

    /**
     * 按租户触发（运营手动重跑）
     */
    @PostMapping("/trigger-by-config/{configId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> triggerByConfig(@PathVariable Long configId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        LocalDate date = body == null || body.get("date") == null
                ? LocalDate.now() : LocalDate.parse(body.get("date").toString());
        return Result.success("已触发", runDailyTrigger(date, configId));
    }

    private Map<String, Object> runDailyTrigger(LocalDate date, Long onlyConfigId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<MemberDayConfig> configs = configMapper.selectList(new LambdaQueryWrapper<MemberDayConfig>()
                .eq(MemberDayConfig::getStatus, "ACTIVE"));
        if (onlyConfigId != null) {
            configs = configs.stream().filter(c -> c.getId().equals(onlyConfigId)).collect(Collectors.toList());
        }

        int totalSent = 0, totalSkip = 0, totalError = 0;
        for (MemberDayConfig cfg : configs) {
            try {
                List<Map<String, Object>> targets = scanTargets(cfg, date);
                for (Map<String, Object> t : targets) {
                    Long memberId = ((Number) t.get("member_id")).longValue();
                    // 防重复
                    MemberDayExecution dup = execMapper.selectOne(new LambdaQueryWrapper<MemberDayExecution>()
                            .eq(MemberDayExecution::getMemberId, memberId)
                            .eq(MemberDayExecution::getConfigId, cfg.getId())
                            .eq(MemberDayExecution::getTriggerDate, date));
                    if (dup != null) { totalSkip++; continue; }

                    MemberDayExecution exec = new MemberDayExecution();
                    exec.setTenantId(cfg.getTenantId());
                    exec.setConfigId(cfg.getId());
                    exec.setMemberId(memberId);
                    exec.setMemberName((String) t.get("member_name"));
                    exec.setMemberPhone((String) t.get("phone"));
                    exec.setTriggerReason(cfg.getDayType());
                    exec.setTriggerDate(date);
                    exec.setChannel("SMS");
                    exec.setDiscountAmount(cfg.getGiftAmount());
                    exec.setGiftAmount(cfg.getGiftAmount());
                    exec.setGiftPoints(cfg.getGiftPoints());
                    exec.setCouponCode(generateCoupon(cfg.getId(), memberId));
                    exec.setSendStatus("SENT");
                    exec.setSentAt(java.time.LocalDateTime.now());
                    execMapper.insert(exec);
                    totalSent++;
                }
            } catch (Exception e) {
                log.error("[MemberDay] 配置 {} 触发失败: {}", cfg.getConfigName(), e.getMessage());
                totalError++;
            }
        }
        summary.put("date", date);
        summary.put("sent", totalSent);
        summary.put("skipped", totalSkip);
        summary.put("errors", totalError);
        return summary;
    }

    /**
     * 扫描符合触发条件的会员
     */
    private List<Map<String, Object>> scanTargets(MemberDayConfig cfg, LocalDate date) {
        String sql = "SELECT id AS member_id, member_name, phone FROM crm_member WHERE tenant_id = ? AND deleted = 0";
        // 按会员等级
        if (cfg.getTargetLevel() != null && !"ALL".equals(cfg.getTargetLevel())) {
            sql += " AND member_level = '" + cfg.getTargetLevel() + "'";
        }
        // 生日触发
        if ("BIRTHDAY".equals(cfg.getDayType())) {
            int offset = cfg.getBirthdayOffsetDays() == null ? 0 : cfg.getBirthdayOffsetDays();
            sql += " AND birthday IS NOT NULL AND (\n";
            sql += "  DATE_FORMAT(birthday, '%m-%d') BETWEEN DATE_FORMAT(DATE_SUB('" + date + "', INTERVAL " + offset + " DAY), '%m-%d')\n";
            sql += "  AND DATE_FORMAT(DATE_ADD('" + date + "', INTERVAL " + offset + " DAY), '%m-%d')\n";
            sql += ")";
        }
        sql += " LIMIT 5000";
        try {
            return jdbc.queryForList(sql, cfg.getTenantId());
        } catch (Exception e) {
            log.warn("扫描失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String generateCoupon(Long configId, Long memberId) {
        return "MD" + configId + "M" + memberId + (System.currentTimeMillis() / 1000 % 10000);
    }

    // ====================================================================
    // T3. 执行记录查询
    // ====================================================================

    @GetMapping("/executions")
    public Result<List<MemberDayExecution>> listExecutions(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<MemberDayExecution> qw = new LambdaQueryWrapper<MemberDayExecution>()
                .eq(MemberDayExecution::getTenantId, tenantId)
                .orderByDesc(MemberDayExecution::getTriggerDate)
                .orderByDesc(MemberDayExecution::getId);
        if (date != null && !date.isEmpty()) {
            qw.eq(MemberDayExecution::getTriggerDate, LocalDate.parse(date));
        }
        if (status != null && !status.isEmpty()) {
            qw.eq(MemberDayExecution::getSendStatus, status);
        }
        return Result.success(execMapper.selectList(qw));
    }

    @PostMapping("/executions/{id}/mark-used")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markUsed(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        MemberDayExecution exec = execMapper.selectById(id);
        if (exec == null) return Result.error(BizCode.NOT_FOUND, "执行记录不存在");
        exec.setSendStatus("USED");
        exec.setUsedAt(java.time.LocalDateTime.now());
        if (body != null && body.get("orderId") != null) {
            exec.setOrderId(((Number) body.get("orderId")).longValue());
        }
        execMapper.updateById(exec);
        return Result.success("已标记使用", null);
    }

    // ====================================================================
    // T4. ROI 报告
    // ====================================================================

    @GetMapping("/roi")
    public Result<Map<String, Object>> roiReport(@RequestParam(defaultValue = "30") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            Map<String, Object> data = new LinkedHashMap<>();

            // 触发总数
            long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM member_day_execution WHERE tenant_id = ? AND trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                    Long.class, tenantId, days);
            // 已使用
            long used = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM member_day_execution WHERE tenant_id = ? AND send_status = 'USED' AND trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                    Long.class, tenantId, days);
            // 已发送
            long sent = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM member_day_execution WHERE tenant_id = ? AND send_status = 'SENT' AND trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                    Long.class, tenantId, days);

            // 关联订单（用了优惠的订单）
            Double revenue = 0.0;
            try {
                revenue = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(o.pay_amount), 0) FROM crm_order o JOIN member_day_execution e ON e.order_id = o.id WHERE o.tenant_id = ? AND e.send_status = 'USED' AND e.trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                        Double.class, tenantId, days);
            } catch (Exception ignore) {}

            data.put("days", days);
            data.put("totalTriggered", total);
            data.put("totalSent", sent);
            data.put("totalUsed", used);
            data.put("conversionRate", total > 0 ? Math.round(used * 10000.0 / total) / 100.0 : 0);
            data.put("revenue", revenue);
            data.put("giftCost", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(gift_amount), 0) FROM member_day_execution WHERE tenant_id = ? AND trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                    Double.class, tenantId, days));
            data.put("roi", revenue != null && revenue > 0
                    ? Math.round(revenue / Math.max(1, jdbc.queryForObject(
                            "SELECT COALESCE(SUM(gift_amount), 1) FROM member_day_execution WHERE tenant_id = ? AND trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)",
                            Double.class, tenantId, days)) * 100) / 100.0 : 0);

            // 按 dayType 分组
            List<Map<String, Object>> byType = jdbc.queryForList("""
                SELECT c.day_type, COUNT(*) AS cnt, SUM(CASE WHEN e.send_status = 'USED' THEN 1 ELSE 0 END) AS used_cnt
                FROM member_day_execution e JOIN member_day_config c ON c.id = e.config_id
                WHERE e.tenant_id = ? AND e.trigger_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                GROUP BY c.day_type
                """, tenantId, days);
            data.put("byDayType", byType);

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(BizCode.SYSTEM_ERROR, "ROI 报告失败: " + e.getMessage());
        }
    }

    // ====================================================================
    // T5. 即将到来的会员日
    // ====================================================================

    @GetMapping("/upcoming")
    public Result<List<Map<String, Object>>> upcoming(@RequestParam(defaultValue = "7") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, member_name, phone, birthday,
                       TIMESTAMPDIFF(YEAR, birthday, CURDATE()) AS age
                FROM crm_member
                WHERE tenant_id = ? AND deleted = 0 AND birthday IS NOT NULL
                ORDER BY DATE_FORMAT(birthday, '%m-%d') ASC
                LIMIT 100
                """, tenantId);
            // 过滤出 N 天内生日的
            List<Map<String, Object>> upcoming = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                java.sql.Date bday = (java.sql.Date) r.get("birthday");
                if (bday == null) continue;
                LocalDate bd = bday.toLocalDate();
                LocalDate thisYear = bd.withYear(today.getYear());
                if (thisYear.isBefore(today)) thisYear = thisYear.plusYears(1);
                long diff = java.time.temporal.ChronoUnit.DAYS.between(today, thisYear);
                if (diff <= days) {
                    r.put("days_until", diff);
                    upcoming.add(r);
                }
            }
            return Result.success(upcoming);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }
}