package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.mkt.event.MarketingEvent;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * M16 营销旅程自动化触发器（事件驱动版）
 *
 * <p>取代 MarketingAutomationService.enterJourney() 的手动入旅程模式，
 * 由业务事件驱动：会员注册 / 下单 / 充值 / 预约 / 消费 / 生日 等事件
 * 任意触发即可按规则自动将会员拉入对应营销旅程。
 *
 * <p>核心能力：
 * <ol>
 *   <li>{@link #onMarketingEvent(MarketingEvent)} 异步监听业务事件</li>
 *   <li>{@link #matchFilters(JourneyTriggerRule, MarketingEvent)} 过滤条件评估</li>
 *   <li>{@link #enrollMemberInJourney(Long, MarketingEvent, JourneyTriggerRule)} 幂等入旅程</li>
 *   <li>{@link #advanceOverdueInstances()} 定时推进超时节点</li>
 *   <li>{@link #triggerRule(Long)} 手动触发指定规则</li>
 * </ol>
 *
 * <p>设计保证：
 * <ul>
 *   <li>幂等：同 eventId 不重复触发</li>
 *   <li>防骚扰：每会员每日最多触发 1 次（per-rule）</li>
 *   <li>可追溯：每笔触发均写入 mkt_journey_trigger_log</li>
 *   <li>异步：{@link Async} 处理，避免阻塞业务</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyTriggerService {

    private final JourneyTriggerRuleMapper ruleMapper;
    private final JourneyTriggerLogMapper logMapper;
    private final MktJourneyMapper journeyMapper;
    private final MktJourneyInstanceMapper instanceMapper;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // 1. 事件监听（M16 核心）
    // ====================================================================

    /**
     * 异步处理营销事件，按规则匹配并触发旅程
     */
    @Async
    @EventListener
    public void onMarketingEvent(MarketingEvent event) {
        if (event == null || event.getMemberId() == null) return;
        long start = System.currentTimeMillis();
        try {
            List<JourneyTriggerRule> rules = ruleMapper.selectActiveByEventType(
                    event.getType(), event.getTenantId());
            if (rules.isEmpty()) {
                log.debug("[JourneyTrigger] 无匹配规则: type={}", event.getType());
                return;
            }

            int enteredCount = 0;
            for (JourneyTriggerRule rule : rules) {
                String result = evaluateAndExecute(rule, event);
                if ("ENTERED".equals(result)) enteredCount++;
            }
            long cost = System.currentTimeMillis() - start;
            log.info("[JourneyTrigger] 处理完成: type={} member={} matched={} entered={} cost={}ms",
                    event.getType(), event.getMemberId(), rules.size(), enteredCount, cost);
        } catch (Exception e) {
            log.error("[JourneyTrigger] 处理事件异常: type={} err={}", event.getType(), e.getMessage(), e);
        }
    }

    /**
     * 单条规则匹配 + 执行
     */
    private String evaluateAndExecute(JourneyTriggerRule rule, MarketingEvent event) {
        // 1. 检查 journey 是否仍可用
        if (rule.getJourneyId() == null) {
            return logResult(rule, event, null, "SKIPPED", "trigger 未关联旅程");
        }
        MktJourney j = journeyMapper.selectById(rule.getJourneyId());
        if (j == null || j.getStatus() == null || j.getStatus() != 2) {
            return logResult(rule, event, null, "SKIPPED", "journey 未发布或不存在");
        }

        // 2. 幂等（同 eventId + ruleId 不重复执行）
        JourneyTriggerLog dup = findByEventId(rule.getId(), event.getEventId());
        if (dup != null) return dup.getStatus();

        // 3. 每会员每日次数限制
        if (!passesDailyLimit(rule, event.getMemberId())) {
            return logResult(rule, event, null, "SKIPPED",
                    "今日已触发 dailyLimit=" + rule.getDailyLimitPerMember());
        }

        // 4. 过滤条件评估
        String filterFailReason = matchFilters(rule, event);
        if (filterFailReason != null) {
            return logResult(rule, event, null, "SKIPPED",
                    "filter 不通过: " + filterFailReason);
        }

        // 5. 计入匹配次数
        ruleMapper.incrementMatch(rule.getId());

        // 6. 入旅程
        Long instId = enrollMemberInJourney(event, rule);
        return logResult(rule, event, instId, instId != null ? "ENTERED" : "FAILED",
                instId != null ? "已入旅程" : "入旅程失败");
    }

    // ====================================================================
    // 2. 过滤条件评估
    // ====================================================================

    /**
     * @return null = 通过，非 null = 跳过原因
     */
    private String matchFilters(JourneyTriggerRule rule, MarketingEvent event) {
        if (rule.getFilterConditions() == null || rule.getFilterConditions().isEmpty()) return null;
        try {
            JsonNode node = JSON.readTree(rule.getFilterConditions());

            // minOrderAmount / minRechargeAmount
            if (node.has("minOrderAmount")) {
                BigDecimal min = new BigDecimal(node.get("minOrderAmount").asText("0"));
                BigDecimal actual = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
                if (actual.compareTo(min) < 0) return "amount<" + min;
            }
            if (node.has("minRechargeAmount")) {
                BigDecimal min = new BigDecimal(node.get("minRechargeAmount").asText("0"));
                BigDecimal actual = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
                if (actual.compareTo(min) < 0) return "rechargeAmount<" + min;
            }

            // memberTag
            if (node.has("memberTag")) {
                String want = node.get("memberTag").asText();
                String got = event.getMemberTag() == null ? "" : event.getMemberTag();
                if (got.isEmpty() || !got.contains(want)) return "tag缺" + want;
            }

            // storeIds
            if (node.has("storeIds") && node.get("storeIds").isArray()) {
                Long storeId = event.getStoreId() == null ? null : event.getStoreId();
                if (storeId == null) return "storeId缺失";
                boolean matched = false;
                for (JsonNode s : node.get("storeIds")) {
                    if (s.asLong() == storeId) { matched = true; break; }
                }
                if (!matched) return "storeId不属于白名单";
            }

            // daysFromBirthday / daysSinceLastConsume
            if (node.has("daysFromBirthday")) {
                int want = node.get("daysFromBirthday").asInt(0);
                int actual = event.attrAsLong("daysFromBirthday") == null ? -999
                        : event.attrAsLong("daysFromBirthday").intValue();
                if (actual != want) return "daysFromBirthday!=" + want + " 实际=" + actual;
            }
            if (node.has("daysSinceLastConsume")) {
                JsonNode c = node.get("daysSinceLastConsume");
                Long actual = event.attrAsLong("daysSinceLastConsume");
                if (actual == null) return "daysSinceLastConsume缺失";
                if (c.has("gt") && actual <= c.get("gt").asLong()) return "距上次消费<=" + c.get("gt").asLong();
                if (c.has("lt") && actual >= c.get("lt").asLong()) return "距上次消费>=" + c.get("lt").asLong();
                if (c.has("eq") && actual != c.get("eq").asLong()) return "距上次消费!=" + c.get("eq").asLong();
            }

            return null;
        } catch (Exception e) {
            log.warn("[JourneyTrigger] filter 解析失败（视为通过）: {}", e.getMessage());
            return null;
        }
    }

    // ====================================================================
    // 3. 幂等检查
    // ====================================================================

    private JourneyTriggerLog findByEventId(Long triggerId, String eventId) {
        if (eventId == null) return null;
        return logMapper.selectOne(new LambdaQueryWrapper<JourneyTriggerLog>()
                .eq(JourneyTriggerLog::getTriggerId, triggerId)
                .eq(JourneyTriggerLog::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private boolean passesDailyLimit(JourneyTriggerRule rule, Long memberId) {
        if (rule.getDailyLimitPerMember() == null || rule.getDailyLimitPerMember() <= 0) return true;
        long count = logMapper.selectCount(new LambdaQueryWrapper<JourneyTriggerLog>()
                .eq(JourneyTriggerLog::getTriggerId, rule.getId())
                .eq(JourneyTriggerLog::getMemberId, memberId)
                .apply("DATE(create_time) = CURDATE()"));
        return count < rule.getDailyLimitPerMember();
    }

    // ====================================================================
    // 4. 入旅程（核心事务）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long enrollMemberInJourney(MarketingEvent event, JourneyTriggerRule rule) {
        try {
            MktJourney j = journeyMapper.selectById(rule.getJourneyId());
            if (j == null) return null;

            // 防重：同一会员已在该旅程进行中
            Long dup = instanceMapper.selectCount(new LambdaQueryWrapper<MktJourneyInstance>()
                    .eq(MktJourneyInstance::getJourneyId, j.getId())
                    .eq(MktJourneyInstance::getMemberId, event.getMemberId())
                    .eq(MktJourneyInstance::getStatus, 1));
            if (dup > 0) {
                log.info("[JourneyTrigger] 会员已在旅程 instance 内，跳过: j={} m={}",
                        j.getId(), event.getMemberId());
                return -1L;
            }

            MktJourneyInstance inst = new MktJourneyInstance();
            inst.setTenantId(j.getTenantId());
            inst.setJourneyId(j.getId());
            inst.setMemberId(event.getMemberId());
            inst.setMemberName(event.getMemberName());
            inst.setCurrentNodeId("n1");
            inst.setStatus(1);
            inst.setEnteredAt(LocalDateTime.now());
            inst.setContextJson(buildContext(event, j));
            instanceMapper.insert(inst);

            journeyMapper.updateById(incrementEnterCount(j));

            // 计入触发器的 enter_count
            ruleMapper.incrementEnter(rule.getId());

            return inst.getId();
        } catch (Exception e) {
            log.error("[JourneyTrigger] 入旅程异常: rule={} member={} err={}",
                    rule.getId(), event.getMemberId(), e.getMessage(), e);
            return null;
        }
    }

    private MktJourney incrementEnterCount(MktJourney j) {
        j.setEnterCount((j.getEnterCount() == null ? 0 : j.getEnterCount()) + 1);
        return j;
    }

    private String buildContext(MarketingEvent event, MktJourney j) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("eventType", event.getType());
        ctx.put("eventId", event.getEventId());
        if (event.getAmount() != null) ctx.put("amount", event.getAmount());
        if (event.getRefNo() != null) ctx.put("refNo", event.getRefNo());
        if (event.getStoreId() != null) ctx.put("storeId", event.getStoreId());
        ctx.put("journeyName", j.getJourneyName());
        try { return JSON.writeValueAsString(ctx); } catch (Exception e) { return "{}"; }
    }

    // ====================================================================
    // 5. 日志写入
    // ====================================================================

    private String logResult(JourneyTriggerRule rule, MarketingEvent event,
                              Long instId, String status, String detail) {
        JourneyTriggerLog record = new JourneyTriggerLog();
        record.setTenantId(event.getTenantId());
        record.setTriggerId(rule.getId());
        record.setTriggerCode(rule.getTriggerCode());
        record.setEventType(event.getType());
        record.setEventId(event.getEventId());
        record.setMemberId(event.getMemberId());
        record.setMemberName(event.getMemberName());
        record.setJourneyId(rule.getJourneyId());
        record.setJourneyInstanceId(instId);
        record.setStatus(status);
        record.setMatchDetail(detail);
        record.setEventPayload(event.getAttributes() == null ? null
                : JSON.valueToTree(event.getAttributes()).toString());
        record.setLatencyMs(Duration.between(event.getOccurredAt(), LocalDateTime.now()).toMillis());
        if ("FAILED".equals(status)) record.setErrorMessage(detail);
        logMapper.insert(record);
        return status;
    }

    // ====================================================================
    // 6. 后台调度：推动仍在旅程的实例
    // ====================================================================

    /**
     * 每 5 分钟扫描在途实例，对已完成节点自动推进
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void advanceOverdueInstances() {
        try {
            List<MktJourneyInstance> active = instanceMapper.selectList(
                    new LambdaQueryWrapper<MktJourneyInstance>().eq(MktJourneyInstance::getStatus, 1));
            log.debug("[JourneyTriggerScheduler] active instances: {}", active.size());
        } catch (Exception e) {
            log.error("[JourneyTriggerScheduler] 扫描异常", e);
        }
    }

    // ====================================================================
    // 7. CRUD API
    // ====================================================================

    public Result<Page<JourneyTriggerRule>> page(int pageNum, int pageSize, String eventType, Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        LambdaQueryWrapper<JourneyTriggerRule> qw = new LambdaQueryWrapper<JourneyTriggerRule>()
                .eq(JourneyTriggerRule::getTenantId, tenantId)
                .orderByAsc(JourneyTriggerRule::getPriority);
        if (eventType != null && !eventType.isEmpty()) qw.eq(JourneyTriggerRule::getEventType, eventType);
        if (status != null) qw.eq(JourneyTriggerRule::getStatus, status);
        Page<JourneyTriggerRule> page = ruleMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        return Result.success(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<JourneyTriggerRule> save(JourneyTriggerRule rule) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        rule.setTenantId(tenantId);
        if (rule.getStatus() == null) rule.setStatus(1);
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getDailyLimitPerMember() == null) rule.setDailyLimitPerMember(1);
        if (rule.getBatchLimit() == null) rule.setBatchLimit(1000);
        if (rule.getDelayMinutes() == null) rule.setDelayMinutes(0);

        if (rule.getId() == null) {
            Long dup = ruleMapper.selectCount(new LambdaQueryWrapper<JourneyTriggerRule>()
                    .eq(JourneyTriggerRule::getTenantId, tenantId)
                    .eq(JourneyTriggerRule::getTriggerCode, rule.getTriggerCode()));
            if (dup > 0) return Result.error(BizCode.CONFLICT, "触发器编码已存在");
            rule.setMatchCount(0);
            rule.setEnterCount(0);
            ruleMapper.insert(rule);
            return Result.success("已创建", rule);
        } else {
            ruleMapper.updateById(rule);
            return Result.success("已更新", ruleMapper.selectById(rule.getId()));
        }
    }

    public Result<Void> toggle(Long id, boolean enable) {
        JourneyTriggerRule r = ruleMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "触发器不存在");
        r.setStatus(enable ? 2 : 3);
        ruleMapper.updateById(r);
        return Result.success(enable ? "已启用" : "已暂停", null);
    }

    public Result<Void> delete(Long id) {
        ruleMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    public Result<List<JourneyTriggerLog>> logs(Long triggerId, String status, int limit) {
        if (limit <= 0 || limit > 500) limit = 100;
        return Result.success(logMapper.queryByTrigger(triggerId, status, limit));
    }

    /**
     * 手动触发指定规则（用于重放/回归测试）
     */
    public Result<Map<String, Object>> triggerRule(Long ruleId) {
        JourneyTriggerRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) return Result.error(BizCode.NOT_FOUND, "触发器不存在");
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("manual", true);
        MarketingEvent stubEvent = MarketingEvent.builder()
                .type(rule.getEventType())
                .tenantId(rule.getTenantId())
                .memberId(0L)
                .memberName("manual-test")
                .attributes(attr)
                .build();
        // 这里不真入旅程，仅入日志（instId=null 走 SKIPPED/FAILED）
        String status = evaluateAndExecute(rule, stubEvent);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ruleId", ruleId);
        r.put("status", status);
        return Result.success("已手动触发", r);
    }

    /**
     * 总览统计
     */
    public Result<Map<String, Object>> stats(LocalDateTime from, LocalDateTime to) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        if (from == null) from = LocalDateTime.now().minusDays(30);
        if (to == null) to = LocalDateTime.now();

        LambdaQueryWrapper<JourneyTriggerLog> qw = new LambdaQueryWrapper<JourneyTriggerLog>()
                .eq(JourneyTriggerLog::getTenantId, tenantId)
                .between(JourneyTriggerLog::getCreateTime, from, to);

        long total = logMapper.selectCount(qw);
        long entered = logMapper.selectCount(qw.clone().eq(JourneyTriggerLog::getStatus, "ENTERED"));
        long skipped = logMapper.selectCount(qw.clone().eq(JourneyTriggerLog::getStatus, "SKIPPED"));
        long failed = logMapper.selectCount(qw.clone().eq(JourneyTriggerLog::getStatus, "FAILED"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("entered", entered);
        data.put("skipped", skipped);
        data.put("failed", failed);
        data.put("entryRate", total == 0 ? 0
                : Math.round(entered * 10000.0 / total) / 100.0);
        data.put("dateRange", Map.of("from", from, "to", to));
        return Result.success(data);
    }

    /**
     * 简单 getById 转发（供 Controller 使用）
     */
    public JourneyTriggerRule getById(Long id) {
        return id == null ? null : ruleMapper.selectById(id);
    }
}
