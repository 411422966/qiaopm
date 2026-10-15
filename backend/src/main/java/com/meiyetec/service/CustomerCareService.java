package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-C：评价回访与客诉 —— 业务实现层（纯 Service）
 *  - T-C21 回访任务
 *  - T-C22 投诉工单状态机
 *  - T-C23 评价 + 模板 + 小程序触达
 *
 * 注：所有 HTTP 映射统一由 CustomerCareController 提供，避免 Service 层承担 Controller 职责。
 */
@Service
@RequiredArgsConstructor
public class CustomerCareService {

    private final CrmFollowUpTaskMapper followTaskMapper;
    private final CrmReviewMapper reviewMapper;
    private final CrmReviewTemplateMapper reviewTemplateMapper;
    private final CrmComplaintTicketMapper complaintMapper;
    private final CrmComplaintLogMapper complaintLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T-C21 回访任务
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<CrmFollowUpTask> createFollowTask(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String taskType = (String) body.get("taskType");
        String subject = (String) body.get("subject");
        if (memberId == null || taskType == null) return Result.error(BizCode.BAD_REQUEST, "memberId / taskType 必填");
        if (subject == null) subject = "回访任务";

        // 拉会员
        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name, phone, store_id FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // SLA：默认 1440 分钟（24h）
        Integer slaMinutes = body.get("slaMinutes") == null ? 1440
                : ((Number) body.get("slaMinutes")).intValue();
        LocalDateTime plannedAt = body.get("plannedAt") == null ? LocalDateTime.now()
                : LocalDateTime.parse((String) body.get("plannedAt"));

        CrmFollowUpTask t = new CrmFollowUpTask();
        t.setTenantId(tenantId);
        t.setBrandId(jdbc.queryForObject(
            "SELECT brand_id FROM pos_store WHERE id = ?", Long.class, member.get("store_id")));
        t.setStoreId(member.get("store_id") == null ? 1L : ((Number) member.get("store_id")).longValue());
        t.setTaskNo("T" + System.currentTimeMillis());
        t.setTaskType(taskType);
        t.setPlanId(body.get("planId") == null ? null : ((Number) body.get("planId")).longValue());
        t.setMemberId(memberId);
        t.setMemberName((String) member.get("name"));
        t.setMemberPhone((String) member.get("phone"));
        t.setSourceType((String) body.get("sourceType"));
        t.setSourceId(body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue());
        t.setSourceNo((String) body.get("sourceNo"));
        t.setPlannedAt(plannedAt);
        t.setSlaMinutes(slaMinutes);
        t.setSlaExpireAt(plannedAt.plusMinutes(slaMinutes));
        t.setPriority(body.get("priority") == null ? 1 : ((Number) body.get("priority")).intValue());
        t.setSubject(subject);
        t.setContent((String) body.get("content"));
        t.setSuggestedScript((String) body.get("suggestedScript"));
        t.setOwnerId(body.get("ownerId") == null ? null : ((Number) body.get("ownerId")).longValue());
        t.setOwnerName((String) body.get("ownerName"));
        t.setStatus(1);
        t.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        followTaskMapper.insert(t);
        return Result.success("已创建", t);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> followTaskPage(
            int pageNum, int pageSize, Integer status, String taskType, Long ownerId, String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, task_no, task_type, member_id, member_name, member_phone, store_id, " +
            "       subject, priority, status, owner_id, owner_name, planned_at, sla_expire_at, " +
            "       started_at, finished_at, result_type, rating, touch_count, create_time " +
            "FROM crm_follow_up_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (taskType != null && !taskType.isEmpty()) { sql.append("AND task_type = ? "); args.add(taskType); }
        if (ownerId != null) { sql.append("AND owner_id = ? "); args.add(ownerId); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (member_name LIKE ? OR member_phone LIKE ? OR subject LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY priority DESC, planned_at ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM crm_follow_up_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (taskType != null && !taskType.isEmpty()) { csql.append("AND task_type = ? "); cargs.add(taskType); }
        if (ownerId != null) { csql.append("AND owner_id = ? "); cargs.add(ownerId); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (member_name LIKE ? OR member_phone LIKE ? OR subject LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    /** 首页用：回访待处理任务数 */
    public Result<Map<String, Object>> pendingCount() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String sql =
            "SELECT COUNT(*) AS total, " +
            "  SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS pending, " +
            "  SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS inProgress, " +
            "  SUM(CASE WHEN priority = 3 AND status IN (1,2) THEN 1 ELSE 0 END) AS urgent " +
            "FROM crm_follow_up_task WHERE deleted = 0 AND tenant_id = ? " +
            "  AND status IN (1, 2) AND (sla_expire_at IS NULL OR sla_expire_at > NOW())";
        Map<String, Object> row = jdbc.queryForMap(sql, tenantId);
        Map<String, Object> data = new HashMap<>();
        data.put("total", row.getOrDefault("total", 0));
        data.put("pending", row.getOrDefault("pending", 0));
        data.put("inProgress", row.getOrDefault("inProgress", 0));
        data.put("urgent", row.getOrDefault("urgent", 0));
        return Result.success(data);
    }

    public Result<Map<String, Object>> followTaskDetail(Long id) {
        Map<String, Object> t = jdbc.queryForList(
            "SELECT * FROM crm_follow_up_task WHERE id = ?", id).stream().findFirst().orElse(null);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");
        return Result.success(Keys.camel(t));
    }

    public Result<Void> startFollowTask(Long id) {
        jdbc.update("UPDATE crm_follow_up_task SET status = 2, started_at = NOW() WHERE id = ?", id);
        return Result.success("已开始", null);
    }

    public Result<Void> closeFollowTask(Long id) {
        jdbc.update("UPDATE crm_follow_up_task SET status = 4, finished_at = NOW() WHERE id = ?", id);
        return Result.success("已关闭", null);
    }

    public Result<Void> finishFollowTask(Long id, Map<String, Object> body) {
        String resultType = (String) body.get("resultType");
        String resultNote = (String) body.get("resultNote");
        Integer rating = body.get("rating") == null ? null : ((Number) body.get("rating")).intValue();
        jdbc.update("UPDATE crm_follow_up_task SET status = 3, finished_at = NOW(), " +
                "result_type = ?, result_note = ?, rating = ? WHERE id = ?",
                resultType, resultNote, rating, id);
        return Result.success("已完成", null);
    }

    public Result<Void> assignFollowTask(Long id, Map<String, Object> body) {
        Long ownerId = body.get("ownerId") == null ? null : ((Number) body.get("ownerId")).longValue();
        String ownerName = (String) body.get("ownerName");
        jdbc.update("UPDATE crm_follow_up_task SET owner_id = ?, owner_name = ? WHERE id = ?",
                ownerId, ownerName, id);
        return Result.success("已分配", null);
    }

    // ====================================================================
    // T-C22 投诉工单（状态机）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<CrmComplaintTicket> createComplaint(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String complaintType = (String) body.get("complaintType");
        String subject = (String) body.get("subject");
        if (complaintType == null || subject == null) return Result.error(BizCode.BAD_REQUEST, "complaintType / subject 必填");

        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        Long brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);

        CrmComplaintTicket t = new CrmComplaintTicket();
        t.setTenantId(tenantId);
        t.setBrandId(brandId);
        t.setStoreId(storeId);
        t.setTicketNo("TC" + System.currentTimeMillis());
        t.setMemberId(memberId);
        t.setMemberName((String) body.get("memberName"));
        t.setMemberPhone((String) body.get("memberPhone"));
        t.setComplaintType(complaintType);
        t.setLevel(body.get("level") == null ? 1 : ((Number) body.get("level")).intValue());
        t.setSubject(subject);
        t.setContent((String) body.get("content"));
        t.setEvidence((String) body.get("evidence"));
        t.setSourceType((String) body.get("sourceType"));
        t.setSourceId(body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue());
        t.setSourceNo((String) body.get("sourceNo"));
        t.setSlaMinutes(body.get("slaMinutes") == null ? 1440 : ((Number) body.get("slaMinutes")).intValue());
        t.setSlaExpireAt(LocalDateTime.now().plusMinutes(t.getSlaMinutes()));
        t.setStatus(1);
        t.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        complaintMapper.insert(t);

        // 写日志
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(tenantId);
        log.setTicketId(t.getId());
        log.setActionType("CREATE");
        log.setToStatus(1);
        log.setContent("工单创建: " + subject);
        log.setOperatorName(t.getOperatorName());
        complaintLogMapper.insert(log);

        return Result.success("已创建", t);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> assignComplaint(Long id, Map<String, Object> body) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        if (t.getStatus() != 1 && t.getStatus() != 7) return Result.error(BizCode.CONFLICT, "当前状态不可分配");

        Long handlerId = body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue();
        String handlerName = (String) body.get("handlerName");
        jdbc.update("UPDATE crm_complaint_ticket SET handler_id = ?, handler_name = ?, status = 2 WHERE id = ?",
                handlerId, handlerName, id);

        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("ASSIGN");
        log.setFromStatus(t.getStatus());
        log.setToStatus(2);
        log.setContent("分配给: " + handlerName);
        complaintLogMapper.insert(log);
        return Result.success("已分配", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> processComplaint(Long id) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        if (t.getStatus() != 2) return Result.error(BizCode.CONFLICT, "已分配状态才能开始处理");
        jdbc.update("UPDATE crm_complaint_ticket SET status = 3 WHERE id = ?", id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("REPLY");
        log.setFromStatus(2);
        log.setToStatus(3);
        log.setContent("开始处理");
        complaintLogMapper.insert(log);
        return Result.success("已开始处理", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> replyComplaint(Long id, Map<String, Object> body) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        String content = (String) body.get("content");
        if (content == null) return Result.error(BizCode.BAD_REQUEST, "回复内容必填");
        jdbc.update("UPDATE crm_complaint_ticket SET status = 4 WHERE id = ?", id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("REPLY");
        log.setFromStatus(3);
        log.setToStatus(4);
        log.setContent(content);
        log.setOperatorName((String) body.get("operatorName"));
        complaintLogMapper.insert(log);
        return Result.success("已回复", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> resolveComplaint(Long id, Map<String, Object> body) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        String solution = (String) body.get("solution");
        String solutionType = (String) body.get("solutionType");
        Object refundAmount = body.get("refundAmount");
        if (solutionType == null) return Result.error(BizCode.BAD_REQUEST, "solutionType 必填");

        jdbc.update("UPDATE crm_complaint_ticket SET status = 5, solution = ?, solution_type = ?, " +
                "refund_amount = ?, resolved_at = NOW() WHERE id = ?",
                solution, solutionType, refundAmount == null ? 0 : new java.math.BigDecimal(refundAmount.toString()), id);

        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("RESOLVE");
        log.setFromStatus(t.getStatus());
        log.setToStatus(5);
        log.setContent("解决方案: " + solutionType + " - " + solution);
        complaintLogMapper.insert(log);
        return Result.success("已解决", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> closeComplaint(Long id, Map<String, Object> body) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        if (t.getStatus() != 5) return Result.error(BizCode.CONFLICT, "仅已解决工单可关闭");

        Integer rating = body.get("satisfactionRating") == null ? null : ((Number) body.get("satisfactionRating")).intValue();
        String note = (String) body.get("satisfactionNote");
        jdbc.update("UPDATE crm_complaint_ticket SET status = 6, satisfaction_rating = ?, satisfaction_note = ? WHERE id = ?",
                rating, note, id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("CLOSE");
        log.setFromStatus(5);
        log.setToStatus(6);
        log.setContent("客户满意度: " + rating + " - " + note);
        complaintLogMapper.insert(log);
        return Result.success("已关闭", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> escalateComplaint(Long id) {
        CrmComplaintTicket t = complaintMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        jdbc.update("UPDATE crm_complaint_ticket SET status = 7, escalate_count = escalate_count + 1 WHERE id = ?", id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("ESCALATE");
        log.setFromStatus(t.getStatus());
        log.setToStatus(7);
        log.setContent("升级处理（升级次数 +1）");
        complaintLogMapper.insert(log);
        return Result.success("已升级", null);
    }

    public Result<Map<String, Object>> complaintDetail(Long id) {
        Map<String, Object> t = jdbc.queryForList(
            "SELECT t.*, ss.store_name, su.handler_name " +
            "FROM crm_complaint_ticket t " +
            "LEFT JOIN pos_store ss ON ss.id = t.store_id " +
            "WHERE t.id = ?", id).stream().findFirst().orElse(null);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");
        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT * FROM crm_complaint_log WHERE ticket_id = ? ORDER BY id ASC", id);
        Map<String, Object> data = new HashMap<>();
        data.put("ticket", Keys.camel(t));
        data.put("logs", Keys.camel(logs));
        return Result.success(data);
    }

    /** 投诉工单汇总看板 */
    public Result<Map<String, Object>> complaintStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();
        data.put("total", jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=?",
            Long.class, tenantId));
        data.put("pendingAssign", jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status=1",
            Long.class, tenantId));
        data.put("inProgress", jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status IN (2,3)",
            Long.class, tenantId));
        data.put("overdue", jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status NOT IN (5,6) AND sla_expire_at < NOW()",
            Long.class, tenantId));
        return Result.success(data);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> complaintPage(
            int pageNum, int pageSize, Integer status, Integer level, String complaintType, String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.ticket_no, t.member_id, t.member_name, t.member_phone, " +
            "       t.complaint_type, t.level, t.subject, t.handler_id, t.handler_name, " +
            "       t.status, t.sla_expire_at, t.refund_amount, t.escalate_count, " +
            "       t.create_time, s.store_name " +
            "FROM crm_complaint_ticket t " +
            "LEFT JOIN pos_store s ON s.id = t.store_id " +
            "WHERE t.deleted = 0 AND t.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND t.status = ? "); args.add(status); }
        if (level != null) { sql.append("AND t.level = ? "); args.add(level); }
        if (complaintType != null && !complaintType.isEmpty()) {
            sql.append("AND t.complaint_type = ? "); args.add(complaintType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (t.member_name LIKE ? OR t.member_phone LIKE ? OR t.subject LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY t.level DESC, t.sla_expire_at ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM crm_complaint_ticket t WHERE t.deleted = 0 AND t.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (status != null) { csql.append("AND t.status = ? "); cargs.add(status); }
        if (level != null) { csql.append("AND t.level = ? "); cargs.add(level); }
        if (complaintType != null && !complaintType.isEmpty()) {
            csql.append("AND t.complaint_type = ? "); cargs.add(complaintType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (t.member_name LIKE ? OR t.member_phone LIKE ? OR t.subject LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    // ====================================================================
    // T-C23 评价
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<CrmReview> createReview(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String sourceType = (String) body.get("sourceType");
        Long sourceId = body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue();
        Integer overallRating = body.get("overallRating") == null ? null
                : ((Number) body.get("overallRating")).intValue();
        if (sourceType == null || sourceId == null) return Result.error(BizCode.BAD_REQUEST, "sourceType / sourceId 必填");
        if (overallRating == null || overallRating < 1 || overallRating > 5)
            return Result.error(BizCode.BAD_REQUEST, "overallRating 必填 1-5");

        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        Long brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);

        CrmReview r = new CrmReview();
        r.setTenantId(tenantId);
        r.setBrandId(brandId);
        r.setStoreId(storeId);
        r.setSourceType(sourceType);
        r.setSourceId(sourceId);
        r.setSourceNo((String) body.get("sourceNo"));
        r.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        r.setMemberName((String) body.get("memberName"));
        r.setMemberPhone((String) body.get("memberPhone"));
        r.setOverallRating(overallRating);
        r.setServiceRating(body.get("serviceRating") == null ? null : ((Number) body.get("serviceRating")).intValue());
        r.setSkillRating(body.get("skillRating") == null ? null : ((Number) body.get("skillRating")).intValue());
        r.setEnvironmentRating(body.get("environmentRating") == null ? null : ((Number) body.get("environmentRating")).intValue());
        r.setPriceRating(body.get("priceRating") == null ? null : ((Number) body.get("priceRating")).intValue());
        r.setTags((String) body.get("tags"));
        r.setContent((String) body.get("content"));
        r.setImages((String) body.get("images"));
        r.setAnonymous(body.get("anonymous") == null ? 0 : (((Number) body.get("anonymous")).intValue()));
        r.setChannel((String) body.getOrDefault("channel", "INTERNAL"));
        r.setStatus(2);
        r.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        reviewMapper.insert(r);

        // 自动触发投诉（如 1-2 星）
        if (overallRating <= 2 && r.getMemberId() != null) {
            CrmComplaintTicket ct = new CrmComplaintTicket();
            ct.setTenantId(tenantId);
            ct.setBrandId(brandId);
            ct.setStoreId(storeId);
            ct.setTicketNo("TC" + System.currentTimeMillis());
            ct.setMemberId(r.getMemberId());
            ct.setMemberName(r.getMemberName());
            ct.setMemberPhone(r.getMemberPhone());
            ct.setComplaintType("SERVICE");
            ct.setLevel(overallRating == 1 ? 4 : 2);
            ct.setSubject("低分评价: " + r.getContent());
            ct.setContent(r.getContent());
            ct.setSourceType("REVIEW");
            ct.setSourceId(r.getId());
            ct.setSourceNo(r.getSourceNo());
            ct.setSlaMinutes(1440);
            ct.setSlaExpireAt(LocalDateTime.now().plusHours(24));
            ct.setStatus(1);
            ct.setOperatorName("system-auto");
            complaintMapper.insert(ct);

            CrmComplaintLog log = new CrmComplaintLog();
            log.setTenantId(tenantId);
            log.setTicketId(ct.getId());
            log.setActionType("CREATE");
            log.setToStatus(1);
            log.setContent("由评价自动创建（" + overallRating + " 星）");
            log.setOperatorName("system");
            complaintLogMapper.insert(log);
        }
        return Result.success("已发布", r);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> reviewPage(
            int pageNum, int pageSize, String sourceType, Integer overallRating, Integer status, String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, source_type, source_id, source_no, member_id, member_name, member_phone, " +
            "       overall_rating, service_rating, skill_rating, environment_rating, price_rating, " +
            "       tags, content, channel, status, reply_content, reply_at, reply_by, create_time " +
            "FROM crm_review WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (sourceType != null && !sourceType.isEmpty()) { sql.append("AND source_type = ? "); args.add(sourceType); }
        if (overallRating != null) { sql.append("AND overall_rating = ? "); args.add(overallRating); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (member_name LIKE ? OR content LIKE ? OR tags LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM crm_review WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (sourceType != null && !sourceType.isEmpty()) { csql.append("AND source_type = ? "); cargs.add(sourceType); }
        if (overallRating != null) { csql.append("AND overall_rating = ? "); cargs.add(overallRating); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (member_name LIKE ? OR content LIKE ? OR tags LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<Void> replyReview(Long id, Map<String, Object> body) {
        String content = (String) body.get("content");
        String by = (String) body.get("replyBy");
        if (content == null) return Result.error(BizCode.BAD_REQUEST, "回复内容必填");
        jdbc.update("UPDATE crm_review SET reply_content = ?, reply_at = NOW(), reply_by = ? WHERE id = ?",
                content, by, id);
        return Result.success("已回复", null);
    }

    public Result<Void> hideReview(Long id) {
        jdbc.update("UPDATE crm_review SET status = 3 WHERE id = ?", id);
        return Result.success("已隐藏", null);
    }

    // ====================================================================
    // T-C23 评价模板
    // ====================================================================

    public Result<CrmReviewTemplate> createTemplate(CrmReviewTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getTemplateCode() == null || t.getTemplateName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码 / 名称 必填");
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getTouchDelayMinutes() == null) t.setTouchDelayMinutes(60);
        t.setTenantId(tenantId);
        reviewTemplateMapper.insert(t);
        return Result.success("已创建", t);
    }

    /** 模板分页列表 */
    public Result<Map<String, Object>> templateList(int pageNum, int pageSize, String keyword, Integer enabled) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CrmReviewTemplate> q = new LambdaQueryWrapper<CrmReviewTemplate>()
                .eq(CrmReviewTemplate::getTenantId, tenantId)
                .orderByDesc(CrmReviewTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(CrmReviewTemplate::getTemplateCode, keyword)
                    .or().like(CrmReviewTemplate::getTemplateName, keyword));
        }
        if (enabled != null) q.eq(CrmReviewTemplate::getEnabled, enabled);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CrmReviewTemplate> p =
                reviewTemplateMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), q);
        Map<String, Object> out = new HashMap<>();
        out.put("records", p.getRecords());
        out.put("total", p.getTotal());
        out.put("pageNum", p.getCurrent());
        out.put("pageSize", p.getSize());
        out.put("pages", p.getPages());
        return Result.success(out);
    }

    /** 模板统计：总数 / 启用 / 平均延迟 / 触发事件分布 / 渠道分布 */
    public Result<Map<String, Object>> templateStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<CrmReviewTemplate> all = reviewTemplateMapper.selectList(
                new LambdaQueryWrapper<CrmReviewTemplate>().eq(CrmReviewTemplate::getTenantId, tenantId));
        long total = all.size();
        long enabled = all.stream().filter(t -> t.getEnabled() != null && t.getEnabled() == 1).count();
        long disabled = total - enabled;
        double avgDelay = 0;
        if (!all.isEmpty()) {
            int sum = 0, cnt = 0;
            for (CrmReviewTemplate t : all) {
                if (t.getTouchDelayMinutes() != null) { sum += t.getTouchDelayMinutes(); cnt++; }
            }
            avgDelay = cnt == 0 ? 0 : (double) sum / cnt;
        }
        Map<String, Long> triggerDist = new LinkedHashMap<>();
        Map<String, Long> channelDist = new LinkedHashMap<>();
        for (CrmReviewTemplate t : all) {
            String ev = t.getTriggerEvent() == null ? "OTHER" : t.getTriggerEvent();
            triggerDist.merge(ev, 1L, Long::sum);
            String ch = t.getTouchChannel() == null ? "OTHER" : t.getTouchChannel();
            channelDist.merge(ch, 1L, Long::sum);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("total", total);
        out.put("enabled", enabled);
        out.put("disabled", disabled);
        out.put("avgDelay", Math.round(avgDelay * 10.0) / 10.0);
        out.put("triggerDist", triggerDist);
        out.put("channelDist", channelDist);
        return Result.success(out);
    }

    /** 模板启停 */
    public Result<Void> templateToggle(Long id, Integer enabled) {
        CrmReviewTemplate t = reviewTemplateMapper.selectById(id);
        if (t == null) return Result.success(null);
        if (enabled != null) t.setEnabled(enabled);
        reviewTemplateMapper.updateById(t);
        return Result.success(null);
    }

    /** 克隆模板 */
    public Result<Map<String, Object>> templateClone(Long id) {
        CrmReviewTemplate src = reviewTemplateMapper.selectById(id);
        if (src == null) return Result.success(new HashMap<>());
        CrmReviewTemplate c = new CrmReviewTemplate();
        c.setTenantId(src.getTenantId());
        c.setBrandId(src.getBrandId());
        c.setTemplateCode(src.getTemplateCode() + "-COPY-" + System.currentTimeMillis());
        c.setTemplateName(src.getTemplateName() + " (副本)");
        c.setTriggerEvent(src.getTriggerEvent());
        c.setDimensions(src.getDimensions());
        c.setTagsPool(src.getTagsPool());
        c.setIntroText(src.getIntroText());
        c.setShareImage(src.getShareImage());
        c.setTouchChannel(src.getTouchChannel());
        c.setTouchDelayMinutes(src.getTouchDelayMinutes());
        c.setEnabled(0);
        c.setOperatorName(src.getOperatorName());
        reviewTemplateMapper.insert(c);
        Map<String, Object> out = new HashMap<>();
        out.put("id", c.getId());
        out.put("templateCode", c.getTemplateCode());
        return Result.success(out);
    }

    /** 删除模板（软删） */
    public Result<Void> templateDelete(Long id) {
        CrmReviewTemplate t = reviewTemplateMapper.selectById(id);
        if (t != null) { t.setDeleted(1); reviewTemplateMapper.updateById(t); }
        return Result.success(null);
    }

    /** 模拟推送 */
    public Result<Map<String, Object>> templateSimulate(Long id) {
        CrmReviewTemplate t = reviewTemplateMapper.selectById(id);
        if (t == null) return Result.success(new HashMap<>());
        Map<String, Object> out = new HashMap<>();
        out.put("templateId", t.getId());
        out.put("templateCode", t.getTemplateCode());
        out.put("templateName", t.getTemplateName());
        out.put("channel", t.getTouchChannel());
        out.put("delayMinutes", t.getTouchDelayMinutes());
        out.put("triggerEvent", t.getTriggerEvent());
        out.put("simulateAt", LocalDateTime.now());
        out.put("willSendAt", LocalDateTime.now().plusMinutes(t.getTouchDelayMinutes() == null ? 0 : t.getTouchDelayMinutes()));
        out.put("previewUrl", "https://example.com/mp/review/" + t.getId());
        out.put("note", "DEMO 模拟推送（dev 环境不会真正下发）");
        return Result.success(out);
    }

    /** 灌入 5 条示例模板 */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> templateDemoSeed() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        reviewTemplateMapper.delete(new LambdaQueryWrapper<CrmReviewTemplate>()
                .eq(CrmReviewTemplate::getTenantId, tenantId)
                .eq(CrmReviewTemplate::getOperatorName, "demo-seed"));
        long stamp = System.currentTimeMillis() % 100000;
        String[][] presets = {
            {"TPL-ORDER-001-" + stamp, "服务订单评价", "ORDER_COMPLETED", "服务,技术,环境,价格", "专业,细致,热情,手法好,环境好",
                "感谢您选择美业，您的反馈是我们前进的动力！", "MINI_PROGRAM", "60"},
            {"TPL-CARD-001-" + stamp, "套餐交付评价", "CARD_DELIVERED", "服务,效果,性价比", "效果好,值得推荐,会再来",
                "您购买的套餐已交付，期待您的使用反馈~", "MINI_PROGRAM", "30"},
            {"TPL-SVC-001-" + stamp, "单次服务评价", "SERVICE_COMPLETED", "服务,技师,环境", "手法好,态度好,耐心",
                "本次服务已完成，期待您给本次体验打分！", "MINI_PROGRAM", "15"},
            {"TPL-BIRTHDAY-001-" + stamp, "生日关怀评价", "BIRTHDAY", "关怀,惊喜,服务", "贴心,有仪式感,感动",
                "美业祝您生日快乐！您的感受对我们很重要", "SMS", "0"},
            {"TPL-EXPIRE-001-" + stamp, "到期关怀评价", "EXPIRE", "续卡意愿,服务", "想继续,会有优惠吗",
                "您的权益即将到期，希望听到您的反馈~", "SMS", "1440"}
        };
        int n = presets.length;
        for (int i = 0; i < n; i++) {
            CrmReviewTemplate t = new CrmReviewTemplate();
            t.setTenantId(tenantId);
            t.setBrandId(1L);
            t.setTemplateCode(presets[i][0]);
            t.setTemplateName(presets[i][1]);
            t.setTriggerEvent(presets[i][2]);
            t.setDimensions(presets[i][3]);
            t.setTagsPool(presets[i][4]);
            t.setIntroText(presets[i][5]);
            t.setTouchChannel(presets[i][6]);
            t.setTouchDelayMinutes(Integer.parseInt(presets[i][7]));
            t.setEnabled(i % 2 == 0 ? 1 : 0);
            t.setOperatorName("demo-seed");
            reviewTemplateMapper.insert(t);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("inserted", n);
        return Result.success(out);
    }

    /** 客户评价汇总看板 */
    public Result<Map<String, Object>> reviewStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=?",
            Long.class, tenantId);
        Long today = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND DATE(create_time)=CURDATE()",
            Long.class, tenantId);
        Long lowRating = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND overall_rating <= 2",
            Long.class, tenantId);

        Long replied = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND reply_content IS NOT NULL AND reply_content <> ''",
            Long.class, tenantId);
        Long published = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2",
            Long.class, tenantId);
        String replyRate = published == 0 ? "0%" :
            String.format("%d%%", Math.round(replied * 100.0 / published));

        // 趋势：近 7 天（按 DATE(create_time) 分组），倒序填入
        List<Map<String, Object>> trendRows = jdbc.queryForList(
            "SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS d, COUNT(*) AS c " +
            "FROM crm_review WHERE deleted=0 AND tenant_id=? " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY d ORDER BY d ASC", tenantId);
        Map<String, Long> trendMap = new HashMap<>();
        for (Map<String, Object> r : trendRows) trendMap.put((String) r.get("d"), ((Number) r.get("c")).longValue());
        java.time.LocalDate today0 = java.time.LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String d = today0.minusDays(i).toString();
            Map<String, Object> it = new HashMap<>();
            it.put("date", d.substring(5)); // MM-dd
            it.put("count", trendMap.getOrDefault(d, 0L));
            trend.add(it);
        }

        // 标签 Top 10（按 tags 整串聚合）
        List<Map<String, Object>> tagRows = jdbc.queryForList(
            "SELECT tags AS name, COUNT(*) AS c FROM crm_review " +
            "WHERE deleted=0 AND tenant_id=? AND tags IS NOT NULL AND tags <> '' " +
            "GROUP BY tags ORDER BY c DESC LIMIT 10", tenantId);
        List<Map<String, Object>> topTags = new ArrayList<>();
        for (Map<String, Object> r : tagRows) {
            Map<String, Object> it = new HashMap<>();
            it.put("name", r.get("name"));
            it.put("count", ((Number) r.get("c")).longValue());
            topTags.add(it);
        }

        // 近 7 日好评 / 差评
        Long good7 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 " +
            "AND overall_rating >= 4 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)",
            Long.class, tenantId);
        Long bad7 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 " +
            "AND overall_rating <= 2 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)",
            Long.class, tenantId);
        Map<String, Object> week7 = new HashMap<>();
        week7.put("good", good7);
        week7.put("bad", bad7);

        // 各维度平均分
        Map<String, Object> dimAvg = new HashMap<>();
        dimAvg.put("service", jdbc.queryForObject(
            "SELECT COALESCE(AVG(service_rating), 0) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND service_rating IS NOT NULL",
            Double.class, tenantId));
        dimAvg.put("skill", jdbc.queryForObject(
            "SELECT COALESCE(AVG(skill_rating), 0) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND skill_rating IS NOT NULL",
            Double.class, tenantId));
        dimAvg.put("environment", jdbc.queryForObject(
            "SELECT COALESCE(AVG(environment_rating), 0) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND environment_rating IS NOT NULL",
            Double.class, tenantId));
        dimAvg.put("price", jdbc.queryForObject(
            "SELECT COALESCE(AVG(price_rating), 0) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND price_rating IS NOT NULL",
            Double.class, tenantId));

        data.put("total", total);
        data.put("todayReviews", today);
        data.put("lowRating", lowRating);
        data.put("replyRate", replyRate);
        data.put("trend", trend);
        data.put("topTags", topTags);
        data.put("week7GoodBad", week7);
        data.put("dimAvg", dimAvg);
        return Result.success(data);
    }

    // ====================================================================
    // T-C24 客户运营菜单（汇总看板）
    // ====================================================================

    public Result<Map<String, Object>> opsOverview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();

        // 回访待办
        Long pendingFollow = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_follow_up_task WHERE deleted=0 AND tenant_id=? AND status IN (1,2)",
            Long.class, tenantId);
        Long overdueFollow = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_follow_up_task WHERE deleted=0 AND tenant_id=? AND status NOT IN (3,4) AND sla_expire_at < NOW()",
            Long.class, tenantId);
        data.put("pendingFollow", pendingFollow);
        data.put("overdueFollow", overdueFollow);

        // 工单
        Long pendingTicket = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status IN (1,2,3,7)",
            Long.class, tenantId);
        Long overdueTicket = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status NOT IN (5,6) AND sla_expire_at < NOW()",
            Long.class, tenantId);
        data.put("pendingTicket", pendingTicket);
        data.put("overdueTicket", overdueTicket);

        // 评价
        Double avgRating = jdbc.queryForObject(
            "SELECT COALESCE(AVG(overall_rating), 0) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2",
            Double.class, tenantId);
        Long totalReviews = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2",
            Long.class, tenantId);
        Long lowRating = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 AND overall_rating <= 2",
            Long.class, tenantId);
        data.put("avgRating", avgRating == null ? 0 : Math.round(avgRating * 10) / 10.0);
        data.put("totalReviews", totalReviews);
        data.put("lowRating", lowRating);

        return Result.success(data);
    }

    /** 灌入 12 条示例评价（低评≤2 自动建投诉工单） */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> reviewDemoSeed() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 清旧 demo
        reviewMapper.delete(new LambdaQueryWrapper<CrmReview>()
                .eq(CrmReview::getTenantId, tenantId)
                .apply("operator_name = 'demo-seed'"));
        String[] names = {"李婧", "王珊", "张婷", "刘芳", "陈琳", "赵敏", "孙洁", "周悦", "吴倩", "郑璇", "黄萍", "何菁"};
        String[] phones = {"13800138001", "13800138002", "13800138003", "13800138004", "13800138005", "13800138006",
                "13800138007", "13800138008", "13800138009", "13800138010", "13800138011", "13800138012"};
        String[] sourceTypes = {"ORDER", "STAFF", "SERVICE", "STORE"};
        String[] tagGroups = {"专业,细致", "热情,手法好", "环境,舒适", "性价比高", "耐心,推荐", "服务到位", "技师专业,环境好", "满意"};
        String[] contents = {
                "做完疗程效果非常满意，技师手法专业，会推荐给朋友！",
                "环境优雅，服务周到，下次还会再来。",
                "性价比很高，比外面便宜。",
                "技师不太耐心，下次可能不来了。",
                "服务还行，没有特别的感受。",
                "体验非常好，皮肤明显改善了。",
                "预约等待时间太长，扣一星。",
                "前台接待很热情，技师手法也好。",
                "项目流程清晰，不会强推销。",
                "客服回复很及时，问题处理得不错。",
                "整体一般，没有惊喜。",
                "非常糟糕的一次体验，完全不推荐。"
        };
        int[] ratings = {5, 5, 4, 1, 3, 5, 2, 4, 4, 4, 3, 1};
        int[] statuses = {2, 2, 2, 3, 2, 2, 2, 2, 2, 1, 2, 1};
        Random rnd = new Random(42);
        int n = names.length;
        for (int i = 0; i < n; i++) {
            CrmReview r = new CrmReview();
            r.setTenantId(tenantId);
            r.setBrandId(1L);
            r.setStoreId((long) (1 + rnd.nextInt(3)));
            r.setSourceType(sourceTypes[i % sourceTypes.length]);
            r.setSourceId((long) (1000 + i));
            r.setSourceNo("D" + String.format("%06d", 200000 + i));
            r.setMemberId((long) (100 + i));
            r.setMemberName(names[i]);
            r.setMemberPhone(phones[i]);
            r.setOverallRating(ratings[i]);
            r.setServiceRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setSkillRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setEnvironmentRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setPriceRating(Math.max(1, ratings[i] + (rnd.nextInt(2) - 1)));
            r.setTags(tagGroups[i % tagGroups.length]);
            r.setContent(contents[i]);
            r.setChannel(i % 2 == 0 ? "MINI_PROGRAM" : "INTERNAL");
            r.setStatus(statuses[i]);
            r.setOperatorName("demo-seed");
            r.setCreateTime(LocalDateTime.now().minusHours(rnd.nextInt(168)));
            reviewMapper.insert(r);
            // 低评自动建投诉工单
            if (ratings[i] <= 2) {
                CrmComplaintTicket t = new CrmComplaintTicket();
                t.setTenantId(tenantId);
                t.setStoreId(r.getStoreId());
                t.setTicketNo("C" + System.currentTimeMillis() + i);
                t.setMemberId(r.getMemberId());
                t.setMemberName(r.getMemberName());
                t.setMemberPhone(r.getMemberPhone());
                t.setComplaintType("SERVICE");
                t.setLevel(ratings[i] == 1 ? 3 : 2);
                t.setSubject("低评自动升级 · demo");
                t.setContent(r.getContent());
                t.setSourceType("REVIEW");
                t.setSourceId(r.getId());
                t.setSourceNo(r.getSourceNo());
                t.setStatus(2);
                t.setSlaMinutes(120);
                t.setSlaExpireAt(LocalDateTime.now().plusMinutes(120));
                t.setOperatorName("demo-seed");
                complaintMapper.insert(t);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("inserted", n);
        return Result.success(out);
    }
}

