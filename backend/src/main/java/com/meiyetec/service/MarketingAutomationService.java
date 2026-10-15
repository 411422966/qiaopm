package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.channel.ChannelDispatcher;
import com.meiyetec.service.channel.ChannelSender;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-D：自动化营销与触达
 *  - T-D21 消息渠道适配层
 *  - T-D22 营销触达任务调度
 *  - T-D23 营销旅程画布
 *  - T-D24 触达效果统计
 */
@Service
@RequiredArgsConstructor
public class MarketingAutomationService {

    private final MktChannelConfigMapper channelConfigMapper;
    private final MktChannelTemplateMapper templateMapper;
    private final MarketingTouchTaskMapper taskMapper;
    private final MarketingTouchLogMapper logMapper;
    private final MktJourneyMapper journeyMapper;
    private final MktJourneyInstanceMapper instanceMapper;
    private final MarketingTouchConversionMapper conversionMapper;
    private final ChannelDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // T-D21 渠道配置 CRUD
    // ====================================================================

    public Result<List<Map<String, Object>>> channelList() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, tenant_id, brand_id, channel_type, channel_name, provider, " +
            "       signature, template_prefix, rate_limit_per_min, rate_limit_per_day, " +
            "       status, last_health_at, last_health_msg, retry_max, retry_interval_sec " +
            "FROM mkt_channel_config WHERE deleted = 0 AND tenant_id = ? " +
            "ORDER BY channel_type ASC",
            tenantId);
        return Result.success(Keys.camel(rows));
    }

    public Result<MktChannelConfig> createChannel(@RequestBody MktChannelConfig c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getChannelType() == null) return Result.error(BizCode.BAD_REQUEST, "channelType 必填");
        Long dup = channelConfigMapper.selectCount(new LambdaQueryWrapper<MktChannelConfig>()
                .eq(MktChannelConfig::getTenantId, tenantId)
                .eq(MktChannelConfig::getBrandId, c.getBrandId())
                .eq(MktChannelConfig::getChannelType, c.getChannelType()));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "同租户同品牌同渠道已存在");
        if (c.getStatus() == null) c.setStatus(1);
        if (c.getRetryMax() == null) c.setRetryMax(3);
        if (c.getRetryIntervalSec() == null) c.setRetryIntervalSec(60);
        if (c.getRateLimitPerMin() == null) c.setRateLimitPerMin(60);
        if (c.getRateLimitPerDay() == null) c.getRateLimitPerDay();
        c.setTenantId(tenantId);
        channelConfigMapper.insert(c);
        return Result.success("已创建", c);
    }

    public Result<MktChannelConfig> updateChannel(@PathVariable Long id, @RequestBody MktChannelConfig c) {
        MktChannelConfig old = channelConfigMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "渠道不存在");
        c.setId(id);
        c.setTenantId(old.getTenantId());
        channelConfigMapper.updateById(c);
        return Result.success("已更新", channelConfigMapper.selectById(id));
    }

    public Result<Void> deleteChannel(@PathVariable Long id) {
        channelConfigMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    public Result<Map<String, Object>> healthCheck() {
        return dispatcher.healthCheckAll();
    }

    // ====================================================================
    // T-D21 模板 CRUD
    // ====================================================================

    public Result<MktChannelTemplate> createTemplate(@RequestBody MktChannelTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getChannelType() == null || t.getScene() == null || t.getContent() == null)
            return Result.error(BizCode.BAD_REQUEST, "channelType / scene / content 必填");
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getAuditStatus() == null) t.setAuditStatus(1);
        if (t.getLinkType() == null) t.setLinkType("MINI");
        t.setTenantId(tenantId);
        templateMapper.insert(t);
        return Result.success("已创建", t);
    }

    public Result<List<Map<String, Object>>> templateList(@RequestParam(required = false) String channelType,
                                                           @RequestParam(required = false) String scene) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, channel_type, template_code, template_name, external_template_id, " +
            "       scene, subject, content, variables, link_url, link_type, audit_status, enabled " +
            "FROM mkt_channel_template WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (channelType != null && !channelType.isEmpty()) { sql.append("AND channel_type = ? "); args.add(channelType); }
        if (scene != null && !scene.isEmpty()) { sql.append("AND scene = ? "); args.add(scene); }
        sql.append("ORDER BY channel_type, scene");
        return Result.success(Keys.camel(jdbc.queryForList(sql.toString(), args.toArray())));
    }

    public Result<Void> auditTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("auditStatus") == null ? null : ((Number) body.get("auditStatus")).intValue();
        String remark = (String) body.get("auditRemark");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "auditStatus 必填");
        jdbc.update("UPDATE mkt_channel_template SET audit_status = ?, audit_remark = ? WHERE id = ?",
                status, remark, id);
        return Result.success("已审核", null);
    }

    // ====================================================================
    // T-D22 营销触达任务
    // ====================================================================

    /**
     * 创建触达任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<MarketingTouchTask> createTask(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String campaignName = (String) body.get("campaignName");
        String channelType = (String) body.get("channelType");
        Long templateId = body.get("templateId") == null ? null : ((Number) body.get("templateId")).longValue();
        String triggerEvent = (String) body.get("triggerEvent");
        LocalDateTime scheduledAt = body.get("scheduledAt") == null ? LocalDateTime.now()
                : LocalDateTime.parse((String) body.get("scheduledAt"));

        if (campaignName == null || channelType == null) return Result.error(BizCode.BAD_REQUEST, "campaignName / channelType 必填");

        // 拉模板快照
        MktChannelTemplate tpl = templateId != null ? templateMapper.selectById(templateId) : null;
        String templateSnapshot = tpl != null ? tpl.getContent() : (String) body.get("content");

        MarketingTouchTask t = new MarketingTouchTask();
        t.setTenantId(tenantId);
        t.setBrandId(body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue());
        t.setStoreId(body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
        t.setTaskNo("TT" + System.currentTimeMillis());
        t.setSourceType((String) body.getOrDefault("sourceType", "MANUAL"));
        t.setJourneyId(body.get("journeyId") == null ? null : ((Number) body.get("journeyId")).longValue());
        t.setJourneyNodeId(body.get("journeyNodeId") == null ? null : ((Number) body.get("journeyNodeId")).longValue());
        t.setCampaignName(campaignName);
        t.setChannelType(channelType);
        t.setTemplateId(templateId);
        t.setTemplateSnapshot(templateSnapshot);
        t.setTriggerEvent(triggerEvent);
        t.setTriggerOffsetMinutes(body.get("triggerOffsetMinutes") == null ? 0 : ((Number) body.get("triggerOffsetMinutes")).intValue());
        t.setAudienceFilter((String) body.get("audienceFilter"));
        t.setScheduledAt(scheduledAt);
        t.setStatus(1);
        t.setMaxReachPerDay(body.get("maxReachPerDay") == null ? null : ((Number) body.get("maxReachPerDay")).intValue());
        t.setRemark((String) body.get("remark"));
        t.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        taskMapper.insert(t);

        // 解析人群（JSON 简化版：memberIds 数组 或 过滤条件）
        List<Long> memberIds = resolveAudience(tenantId, t.getBrandId(), t.getStoreId(),
                t.getAudienceFilter());
        t.setTargetCount(memberIds.size());
        taskMapper.updateById(t);

        // 生成触达明细
        for (Long memberId : memberIds) {
            Map<String, Object> m = jdbc.queryForList(
                "SELECT id, name, phone FROM crm_member WHERE id = ? AND deleted = 0", memberId)
                .stream().findFirst().orElse(null);
            if (m == null) continue;
            MarketingTouchLog log = new MarketingTouchLog();
            log.setTenantId(tenantId);
            log.setTaskId(t.getId());
            log.setMemberId(memberId);
            log.setMemberName((String) m.get("name"));
            log.setMemberPhone((String) m.get("phone"));
            log.setChannelType(channelType);
            log.setContent(templateSnapshot);
            log.setStatus(1);
            log.setScheduledAt(scheduledAt);
            logMapper.insert(log);
        }
        return Result.success("已创建", t);
    }

    /**
     * 解析人群：优先 memberIds，否则按过滤条件
     */
    private List<Long> resolveAudience(Long tenantId, Long brandId, Long storeId, String filterJson) {
        List<Long> result = new ArrayList<>();
        try {
            if (filterJson != null && !filterJson.isEmpty()) {
                JsonNode node = JSON.readTree(filterJson);
                if (node.has("memberIds")) {
                    for (JsonNode n : node.get("memberIds")) {
                        result.add(n.asLong());
                    }
                    return result;
                }
            }
        } catch (Exception ignored) {}
        // 默认：所有当前 store 下的会员
        String sql = "SELECT id FROM crm_member WHERE deleted = 0 AND tenant_id = ?";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql += " AND store_id = ?"; args.add(storeId); }
        result.addAll(jdbc.queryForList(sql, Long.class, args.toArray()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> startTask(@PathVariable Long id) {
        MarketingTouchTask task = taskMapper.selectById(id);
        if (task == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");
        if (task.getStatus() != 1 && task.getStatus() != 4) return Result.error(BizCode.CONFLICT, "仅待执行/已暂停可启动");

        task.setStatus(2);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 拉明细，逐条发送
        List<MarketingTouchLog> logs = logMapper.selectList(new LambdaQueryWrapper<MarketingTouchLog>()
                .eq(MarketingTouchLog::getTaskId, id)
                .eq(MarketingTouchLog::getStatus, 1));
        int success = 0, fail = 0;
        for (MarketingTouchLog lg : logs) {
            // 限流
            if (!dispatcher.rateLimitCheck(task.getTenantId(), task.getBrandId(), task.getChannelType())) {
                jdbc.update("UPDATE marketing_touch_log SET status = 1 WHERE id = ?", lg.getId());
                continue;
            }
            // 渠道
            ChannelSender sender = dispatcher.getSender(task.getChannelType());
            if (sender == null) {
                jdbc.update("UPDATE marketing_touch_log SET status = 3, error_msg = 'no sender' WHERE id = ?", lg.getId());
                fail++;
                continue;
            }
            MktChannelTemplate tpl = task.getTemplateId() != null ? templateMapper.selectById(task.getTemplateId()) : null;
            if (tpl == null) {
                // 占位模板（用快照）
                tpl = new MktChannelTemplate();
                tpl.setChannelType(task.getChannelType());
                tpl.setContent(task.getTemplateSnapshot());
                tpl.setExternalTemplateId("");
            }
            Map<String, Object> vars = buildVars(lg);
            ChannelSender.SendResult res;
            try {
                String receiver = task.getChannelType().equals("MINI_PROGRAM")
                        ? "OPENID-" + lg.getMemberId() // 实际应使用 member.openid
                        : lg.getMemberPhone();
                res = sender.send(tpl, receiver, vars);
            } catch (Exception e) {
                res = ChannelSender.SendResult.fail(e.getMessage());
            }
            // 重试
            int retries = 0;
            int maxRetry = 3;
            while (!res.success && retries < maxRetry) {
                try { Thread.sleep(100L * (retries + 1)); } catch (InterruptedException ignored) {}
                retries++;
                lg.setRetryCount(retries);
                try {
                    res = sender.send(tpl, lg.getMemberPhone(), vars);
                } catch (Exception e) {
                    res = ChannelSender.SendResult.fail(e.getMessage());
                }
            }
            if (res.success) {
                jdbc.update("UPDATE marketing_touch_log SET status = 2, sent_at = NOW(), " +
                        "external_msg_id = ?, retry_count = ? WHERE id = ?",
                        res.externalMsgId, lg.getRetryCount(), lg.getId());
                success++;
            } else {
                jdbc.update("UPDATE marketing_touch_log SET status = 3, error_msg = ?, retry_count = ? WHERE id = ?",
                        res.errorMsg, lg.getRetryCount(), lg.getId());
                fail++;
            }
        }
        task.setSuccessCount(success);
        task.setFailCount(fail);
        task.setFinishedAt(LocalDateTime.now());
        task.setStatus(3);
        taskMapper.updateById(task);

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("successCount", success);
        data.put("failCount", fail);
        return Result.success("已完成", data);
    }

    public Result<Void> pauseTask(@PathVariable Long id) {
        jdbc.update("UPDATE marketing_touch_task SET status = 4 WHERE id = ? AND status = 2", id);
        return Result.success("已暂停", null);
    }

    public Result<Void> resumeTask(@PathVariable Long id) {
        jdbc.update("UPDATE marketing_touch_task SET status = 2 WHERE id = ? AND status = 4", id);
        return Result.success("已恢复", null);
    }

    public Result<Void> cancelTask(@PathVariable Long id) {
        jdbc.update("UPDATE marketing_touch_task SET status = 6 WHERE id = ?", id);
        jdbc.update("UPDATE marketing_touch_log SET status = 6 WHERE task_id = ? AND status = 1", id);
        return Result.success("已取消", null);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> taskPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, task_no, source_type, journey_id, campaign_name, channel_type, template_id, " +
            "       trigger_event, scheduled_at, started_at, finished_at, status, " +
            "       target_count, success_count, fail_count, read_count, convert_count, " +
            "       operator_name, create_time " +
            "FROM marketing_touch_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (channelType != null && !channelType.isEmpty()) { sql.append("AND channel_type = ? "); args.add(channelType); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND campaign_name LIKE ? ");
            args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM marketing_touch_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (channelType != null && !channelType.isEmpty()) { csql.append("AND channel_type = ? "); cargs.add(channelType); }
        if (keyword != null && !keyword.isEmpty()) { csql.append("AND campaign_name LIKE ? "); cargs.add("%" + keyword + "%"); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> taskLogPage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, task_id, member_id, member_name, member_phone, channel_type, status, " +
            "       error_msg, sent_at, read_at, convert_at, retry_count, external_msg_id " +
            "FROM marketing_touch_log WHERE deleted = 0 AND task_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        sql.append("ORDER BY id ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM marketing_touch_log WHERE deleted = 0 AND task_id = ? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, id);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    private Map<String, Object> buildVars(MarketingTouchLog lg) {
        Map<String, Object> vars = new HashMap<>();
        if (lg.getMemberName() != null) vars.put("name", lg.getMemberName());
        vars.put("date", LocalDateTime.now());
        vars.put("amount", "100");
        return vars;
    }

    // ====================================================================
    // T-D23 营销旅程
    // ====================================================================

    public Result<MktJourney> createJourney(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String journeyCode = (String) body.get("journeyCode");
        String journeyName = (String) body.get("journeyName");
        if (journeyCode == null || journeyName == null) return Result.error(BizCode.BAD_REQUEST, "编码/名称 必填");

        Long dup = journeyMapper.selectCount(new LambdaQueryWrapper<MktJourney>()
                .eq(MktJourney::getTenantId, tenantId).eq(MktJourney::getJourneyCode, journeyCode));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "编码已存在");

        MktJourney j = new MktJourney();
        j.setTenantId(tenantId);
        j.setBrandId(body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue());
        j.setJourneyCode(journeyCode);
        j.setJourneyName(journeyName);
        j.setJourneyType((String) body.getOrDefault("journeyType", "MARKETING"));
        j.setDescription((String) body.get("description"));
        j.setNodesJson(toJson(body.get("nodes")));
        j.setEdgesJson(toJson(body.get("edges")));
        j.setTriggerEvent((String) body.get("triggerEvent"));
        j.setTriggerFilter((String) body.get("triggerFilter"));
        j.setStatus(1);
        j.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        journeyMapper.insert(j);
        return Result.success("已创建", j);
    }

    public Result<MktJourney> updateJourney(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MktJourney old = journeyMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "旅程不存在");
        old.setJourneyName((String) body.getOrDefault("journeyName", old.getJourneyName()));
        old.setDescription((String) body.getOrDefault("description", old.getDescription()));
        old.setNodesJson(toJson(body.get("nodes")));
        old.setEdgesJson(toJson(body.get("edges")));
        old.setJourneyType((String) body.getOrDefault("journeyType", old.getJourneyType()));
        old.setTriggerEvent((String) body.getOrDefault("triggerEvent", old.getTriggerEvent()));
        journeyMapper.updateById(old);
        return Result.success("已更新", old);
    }

    public Result<Void> publishJourney(@PathVariable Long id) {
        jdbc.update("UPDATE mkt_journey SET status = 2, published_at = NOW() WHERE id = ?", id);
        return Result.success("已发布", null);
    }

    public Result<Void> pauseJourney(@PathVariable Long id) {
        jdbc.update("UPDATE mkt_journey SET status = 3 WHERE id = ?", id);
        return Result.success("已暂停", null);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> journeyPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String journeyType,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 条件片段 + 条件 args（修复 I073：与 pageArgs 分开，避免 COUNT 多余参数）
        StringBuilder cond = new StringBuilder(" WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> condArgs = new ArrayList<>();
        condArgs.add(tenantId);
        if (status != null) { cond.append("AND status = ? "); condArgs.add(status); }
        if (journeyType != null && !journeyType.isEmpty()) { cond.append("AND journey_type = ? "); condArgs.add(journeyType); }
        if (keyword != null && !keyword.isEmpty()) {
            cond.append("AND (journey_name LIKE ? OR journey_code LIKE ?) ");
            condArgs.add("%" + keyword + "%"); condArgs.add("%" + keyword + "%");
        }

        // SELECT 走完整条件 + 分页
        String sql = "SELECT id, journey_code, journey_name, journey_type, description, " +
                "       trigger_event, status, enter_count, complete_count, exit_count, " +
                "       operator_name, published_at, create_time " +
                "FROM mkt_journey" + cond + "ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(condArgs);
        pageArgs.add(pageSize); pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql, pageArgs.toArray());

        // COUNT 只用 condArgs（不含 pageSize/offset）
        String csql = "SELECT COUNT(*) FROM mkt_journey" + cond;
        Long total = jdbc.queryForObject(csql, Long.class, condArgs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<Map<String, Object>> journeyDetail(@PathVariable Long id) {
        Map<String, Object> row = jdbc.queryForList(
            "SELECT * FROM mkt_journey WHERE id = ?", id).stream().findFirst().orElse(null);
        if (row == null) return Result.error(BizCode.NOT_FOUND, "旅程不存在");

        Map<String, Object> data = new HashMap<>();
        data.put("journey", Keys.camel(row));
        // 解析 nodes / edges 为 JSON
        try {
            if (row.get("nodes_json") != null) {
                data.put("nodes", JSON.readTree(row.get("nodes_json").toString()));
                data.put("edges", JSON.readTree(row.get("edges_json").toString()));
            }
        } catch (Exception ignored) {}
        return Result.success(data);
    }

    /**
     * T-D23 触发会员进入旅程
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> enterJourney(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        Map<String, Object> m = jdbc.queryForList(
            "SELECT id, name FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (m == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // 幂等
        Long dup = instanceMapper.selectCount(new LambdaQueryWrapper<MktJourneyInstance>()
                .eq(MktJourneyInstance::getJourneyId, id)
                .eq(MktJourneyInstance::getMemberId, memberId)
                .eq(MktJourneyInstance::getStatus, 1));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "会员已在该旅程中");

        MktJourneyInstance inst = new MktJourneyInstance();
        inst.setTenantId(TenantContextHolder.get().getTenantId());
        inst.setJourneyId(id);
        inst.setMemberId(memberId);
        inst.setMemberName((String) m.get("name"));
        inst.setCurrentNodeId("n1");
        inst.setStatus(1);
        inst.setEnteredAt(LocalDateTime.now());
        inst.setContextJson(JSON.createObjectNode().toString());
        instanceMapper.insert(inst);

        jdbc.update("UPDATE mkt_journey SET enter_count = enter_count + 1 WHERE id = ?", id);
        Map<String, Object> data = new HashMap<>();
        data.put("id", inst.getId());
        data.put("journeyId", inst.getJourneyId());
        data.put("memberId", inst.getMemberId());
        data.put("status", inst.getStatus());
        return Result.success("已进入旅程", data);
    }

    /**
     * T-D23 执行下一节点（推进旅程）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> nextNode(@PathVariable Long instanceId) {
        MktJourneyInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) return Result.error(BizCode.NOT_FOUND, "实例不存在");
        if (inst.getStatus() != 1) return Result.error(BizCode.CONFLICT, "实例非进行中");
        MktJourney j = journeyMapper.selectById(inst.getJourneyId());
        if (j == null) return Result.error(BizCode.NOT_FOUND, "旅程不存在");

        try {
            JsonNode nodes = JSON.readTree(j.getNodesJson() == null ? "[]" : j.getNodesJson());
            JsonNode edges = JSON.readTree(j.getEdgesJson() == null ? "[]" : j.getEdgesJson());
            // 找当前节点的下一个
            String current = inst.getCurrentNodeId();
            String nextId = null;
            String nextType = null;
            for (JsonNode e : edges) {
                if (e.get("from").asText().equals(current)) {
                    nextId = e.get("to").asText();
                    break;
                }
            }
            if (nextId == null) {
                // 没有下一节点 → 结束
                inst.setStatus(2);
                inst.setCompletedAt(LocalDateTime.now());
                instanceMapper.updateById(inst);
                jdbc.update("UPDATE mkt_journey SET complete_count = complete_count + 1 WHERE id = ?", j.getId());
                return Result.success("旅程已结束", Map.of("id", inst.getId(), "status", 2));
            }
            // 找下一节点的 type
            for (JsonNode n : nodes) {
                if (n.get("id").asText().equals(nextId)) {
                    nextType = n.get("type") == null ? "" : n.get("type").asText();
                    // SMS/MP 节点：自动创建触达任务
                    if ("SMS".equals(nextType) || "MINI_PROGRAM".equals(nextType)) {
                        String tplCode = n.get("template") == null ? "" : n.get("template").asText();
                        Long tplId = jdbc.queryForObject(
                            "SELECT id FROM mkt_channel_template WHERE template_code = ? AND deleted = 0",
                            Long.class, tplCode);
                        if (tplId != null) {
                            Map<String, Object> taskBody = new HashMap<>();
                            taskBody.put("campaignName", "旅程任务: " + j.getJourneyName());
                            taskBody.put("channelType", n.get("type").asText());
                            taskBody.put("templateId", tplId);
                            taskBody.put("audienceFilter", "{\"memberIds\":[" + inst.getMemberId() + "]}");
                            taskBody.put("sourceType", "JOURNEY");
                            taskBody.put("journeyId", j.getId());
                            createTask(taskBody);
                        }
                    }
                    break;
                }
            }
            inst.setCurrentNodeId(nextId);
            instanceMapper.updateById(inst);
            Map<String, Object> data = new HashMap<>();
            data.put("id", inst.getId());
            data.put("currentNodeId", inst.getCurrentNodeId());
            data.put("status", inst.getStatus());
            data.put("nextNodeType", nextType);
            return Result.success("已推进", data);
        } catch (Exception e) {
            return Result.error(BizCode.BAD_REQUEST, "JSON 解析失败: " + e.getMessage());
        }
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try { return JSON.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    // ====================================================================
    // T-D24 触达效果统计（ROI + 转化漏斗）
    // ====================================================================

    /**
     * 漏斗：触达 → 已发送 → 已读 → 已转化
     */
    public Result<Map<String, Object>> conversionFunnel(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder("WHERE l.deleted = 0 AND l.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (startDate != null) { where.append("AND l.scheduled_at >= ? "); args.add(startDate); }
        if (endDate != null) { where.append("AND l.scheduled_at <= ? "); args.add(endDate); }

        String sql = "SELECT " +
                "  COUNT(*) AS reach, " +
                "  SUM(CASE WHEN l.status >= 2 THEN 1 ELSE 0 END) AS sent, " +
                "  SUM(CASE WHEN l.status >= 4 THEN 1 ELSE 0 END) AS read_, " +
                "  SUM(CASE WHEN l.status = 5 THEN 1 ELSE 0 END) AS converted " +
                "FROM marketing_touch_log l " + where;

        Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());
        long reach = ((Number) row.get("reach")).longValue();
        long sent = ((Number) row.getOrDefault("sent", 0)).longValue();
        long read = ((Number) row.getOrDefault("read_", 0)).longValue();
        long conv = ((Number) row.getOrDefault("converted", 0)).longValue();

        Map<String, Object> data = new HashMap<>();
        data.put("reach", reach);
        data.put("sent", sent);
        data.put("read", read);
        data.put("converted", conv);
        data.put("sentRate", reach == 0 ? 0 : Math.round(sent * 10000.0 / reach) / 100.0);
        data.put("readRate", sent == 0 ? 0 : Math.round(read * 10000.0 / sent) / 100.0);
        data.put("convertRate", read == 0 ? 0 : Math.round(conv * 10000.0 / read) / 100.0);
        return Result.success(data);
    }

    /**
     * ROI：触达 → 转化的金额回报
     */
    public Result<Map<String, Object>> conversionRoi(@RequestParam(required = false) Long taskId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder(" WHERE c.deleted = 0 AND c.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (taskId != null) { where.append(" AND c.task_id = ? "); args.add(taskId); }

        String sql = "SELECT " +
                "  COALESCE(SUM(amount), 0) AS total_revenue, " +
                "  COUNT(*) AS convert_count " +
                "FROM marketing_touch_conversion c " + where;
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(sql, args.toArray());
        } catch (DataAccessException e) {
            // 0 行匹配：返回默认空值（修复 I072 空数据兜底）
            row = new HashMap<>();
            row.put("total_revenue", 0);
            row.put("convert_count", 0);
        }
        BigDecimal totalRevenue = new BigDecimal(row.getOrDefault("total_revenue", 0).toString());
        long convertCount = ((Number) row.getOrDefault("convert_count", 0)).longValue();

        // 触达任务数（成本估算）
        String tcountSql = "SELECT COALESCE(SUM(CASE WHEN status >= 2 THEN 1 ELSE 0 END), 0) AS reach FROM marketing_touch_log " +
                "WHERE tenant_id = ? AND deleted = 0" + (taskId != null ? " AND task_id = ?" : "");
        List<Object> ta = new ArrayList<>();
        ta.add(tenantId);
        if (taskId != null) ta.add(taskId);
        Long reach;
        try {
            reach = jdbc.queryForObject(tcountSql, Long.class, ta.toArray());
        } catch (DataAccessException e) {
            // 0 行匹配：reach = 0
            reach = 0L;
        }
        if (reach == null) reach = 0L;
        BigDecimal costPerReach = BigDecimal.valueOf(0.05);
        BigDecimal totalCost = costPerReach.multiply(BigDecimal.valueOf(reach == null ? 0 : reach));

        BigDecimal roi = totalCost.signum() == 0 ? BigDecimal.ZERO
                : totalRevenue.subtract(totalCost).divide(totalCost, 4, java.math.RoundingMode.HALF_UP);

        Map<String, Object> data = new HashMap<>();
        data.put("totalRevenue", totalRevenue);
        data.put("totalCost", totalCost);
        data.put("roi", roi);
        data.put("convertCount", convertCount);
        data.put("reachCount", reach);
        return Result.success(data);
    }

    /**
     * 按渠道统计
     */
    public Result<List<Map<String, Object>>> byChannel() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT l.channel_type, " +
            "       COUNT(*) AS reach, " +
            "       SUM(CASE WHEN l.status = 2 THEN 1 ELSE 0 END) AS sent, " +
            "       SUM(CASE WHEN l.status = 5 THEN 1 ELSE 0 END) AS converted " +
            "FROM marketing_touch_log l " +
            "WHERE l.deleted = 0 AND l.tenant_id = ? " +
            "GROUP BY l.channel_type", tenantId);
        return Result.success(Keys.camel(rows));
    }

    /**
     * 注册转化（会员下单/注册时调用）
     */
    public Result<Void> trackConversion(@RequestBody Map<String, Object> body) {
        Long touchLogId = body.get("touchLogId") == null ? null : ((Number) body.get("touchLogId")).longValue();
        String type = (String) body.get("conversionType");
        Long relatedId = body.get("relatedId") == null ? null : ((Number) body.get("relatedId")).longValue();
        if (touchLogId == null || type == null) return Result.error(BizCode.BAD_REQUEST, "touchLogId / type 必填");
        BigDecimal amount = body.get("amount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("amount").toString());

        MarketingTouchLog lg = logMapper.selectById(touchLogId);
        if (lg == null) return Result.error(BizCode.NOT_FOUND, "触达记录不存在");

        MarketingTouchConversion conv = new MarketingTouchConversion();
        conv.setTenantId(lg.getTenantId());
        conv.setTouchLogId(touchLogId);
        conv.setTaskId(lg.getTaskId());
        conv.setConversionType(type);
        conv.setRelatedId(relatedId);
        conv.setRelatedNo((String) body.get("relatedNo"));
        conv.setAmount(amount);
        conv.setConvertedAt(LocalDateTime.now());
        conversionMapper.insert(conv);

        jdbc.update("UPDATE marketing_touch_log SET status = 5, convert_at = NOW(), related_order_id = ? WHERE id = ?",
                relatedId, touchLogId);
        jdbc.update("UPDATE marketing_touch_task SET convert_count = convert_count + 1 WHERE id = ?", lg.getTaskId());
        return Result.success("已记录", null);
    }

    /**
     * 标记已读
     */
    public Result<Void> markRead(@PathVariable Long id) {
        Long t = TenantContextHolder.get().getTenantId();
        // I071 修复：加 tenantId 过滤（防跨租户写）+ 校验 rows 影响数（消除 Fake Success）
        int logRows = jdbc.update(
                "UPDATE marketing_touch_log SET status = 4, read_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND status = 2",
                id, t);
        if (logRows == 0) {
            return Result.error(BizCode.NOT_FOUND, "触达记录不存在或非待读状态");
        }
        jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
                "(SELECT task_id FROM marketing_touch_log WHERE id = ? AND tenant_id = ?)",
                id, t);
        return Result.success("已标记", null);
    }
}
