package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.ComplaintCategory;
import com.meiyetec.entity.ComplaintSatisfaction;
import com.meiyetec.entity.ComplaintSlaPolicy;
import com.meiyetec.entity.CrmComplaintLog;
import com.meiyetec.entity.CrmComplaintTicket;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.ComplaintCategoryMapper;
import com.meiyetec.mapper.ComplaintSatisfactionMapper;
import com.meiyetec.mapper.ComplaintSlaPolicyMapper;
import com.meiyetec.mapper.CrmComplaintLogMapper;
import com.meiyetec.mapper.CrmComplaintTicketMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/crm/care/complaint")
@RequiredArgsConstructor
public class ComplaintTicketController {

    private final CrmComplaintTicketMapper ticketMapper;
    private final CrmComplaintLogMapper logMapper;
    private final ComplaintSatisfactionMapper satisfactionMapper;
    private final ComplaintCategoryMapper categoryMapper;
    private final ComplaintSlaPolicyMapper slaPolicyMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Long effTenant() {
        Long t = TenantContextHolder.get().getTenantId();
        return (t == null || t == 0L) ? 1L : t;
    }

    private String currentUser() {
        return Optional.ofNullable(TenantContextHolder.get().getUsername()).orElse("admin");
    }

    @GetMapping("/stats")
    @RequirePermission("crm:care:complaint:list")
    public Result<Map<String, Object>> stats() {
        Long tenantId = effTenant();
        Map<String, Object> data = new LinkedHashMap<>();

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=?",
            Long.class, tenantId);
        Long pendingAssign = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status=1",
            Long.class, tenantId);
        Long inProgress = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status IN (2,3,4)",
            Long.class, tenantId);
        Long overdue = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? "
            + "AND status NOT IN (5,6) AND sla_expire_at IS NOT NULL AND sla_expire_at < NOW()",
            Long.class, tenantId);
        Long resolved = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_complaint_ticket WHERE deleted=0 AND tenant_id=? AND status IN (5,6)",
            Long.class, tenantId);

        data.put("total", total == null ? 0 : total);
        data.put("pendingAssign", pendingAssign == null ? 0 : pendingAssign);
        data.put("inProgress", inProgress == null ? 0 : inProgress);
        data.put("overdue", overdue == null ? 0 : overdue);
        data.put("resolved", resolved == null ? 0 : resolved);
        return Result.success(data);
    }

    @GetMapping("/page")
    @RequirePermission("crm:care:complaint:list")
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String complaintType,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sla,
            @RequestParam(required = false) String dateRange) {
        Long tenantId = effTenant();

        StringBuilder sql = new StringBuilder(
            "SELECT t.id, t.ticket_no, t.member_id, t.member_name, t.member_phone, "
            + "t.complaint_type, t.level, t.subject, t.handler_id, t.handler_name, "
            + "t.store_id, t.status, t.sla_expire_at, t.refund_amount, t.escalate_count, "
            + "t.solution_type, t.solution, t.create_time, s.store_name "
            + "FROM crm_complaint_ticket t "
            + "LEFT JOIN pos_store s ON s.id = t.store_id "
            + "WHERE t.deleted = 0 AND t.tenant_id = ? ");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM crm_complaint_ticket t WHERE t.deleted = 0 AND t.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        List<Object> cargs = new ArrayList<>();
        args.add(tenantId); cargs.add(tenantId);

        if (status != null) { sql.append("AND t.status = ? "); args.add(status); csql.append("AND t.status = ? "); cargs.add(status); }
        if (level != null) { sql.append("AND t.level = ? "); args.add(level); csql.append("AND t.level = ? "); cargs.add(level); }
        if (complaintType != null && !complaintType.isEmpty()) {
            sql.append("AND t.complaint_type = ? "); args.add(complaintType);
            csql.append("AND t.complaint_type = ? "); cargs.add(complaintType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (t.ticket_no LIKE ? OR t.member_name LIKE ? OR t.member_phone LIKE ? OR t.subject LIKE ?) ");
            String kw = "%" + keyword + "%";
            args.add(kw); args.add(kw); args.add(kw); args.add(kw);
            csql.append("AND (t.ticket_no LIKE ? OR t.member_name LIKE ? OR t.member_phone LIKE ? OR t.subject LIKE ?) ");
            cargs.add(kw); cargs.add(kw); cargs.add(kw); cargs.add(kw);
        }
        if ("overdue".equals(sla)) {
            sql.append("AND t.status NOT IN (5,6) AND t.sla_expire_at IS NOT NULL AND t.sla_expire_at < NOW() ");
            csql.append("AND t.status NOT IN (5,6) AND t.sla_expire_at IS NOT NULL AND t.sla_expire_at < NOW() ");
        } else if ("soon".equals(sla)) {
            sql.append("AND t.status NOT IN (5,6) AND t.sla_expire_at IS NOT NULL "
                       + "AND t.sla_expire_at >= NOW() AND t.sla_expire_at < DATE_ADD(NOW(), INTERVAL 4 HOUR) ");
            csql.append("AND t.status NOT IN (5,6) AND t.sla_expire_at IS NOT NULL "
                        + "AND t.sla_expire_at >= NOW() AND t.sla_expire_at < DATE_ADD(NOW(), INTERVAL 4 HOUR) ");
        }
        if (dateRange != null && dateRange.contains(",")) {
            String[] dr = dateRange.split(",", 2);
            String start = dr[0].trim();
            String end = dr[1].trim();
            sql.append("AND t.create_time >= ? AND t.create_time < DATE_ADD(?, INTERVAL 1 DAY) ");
            args.add(start); args.add(end);
            csql.append("AND t.create_time >= ? AND t.create_time < DATE_ADD(?, INTERVAL 1 DAY) ");
            cargs.add(start); cargs.add(end);
        }

        sql.append("ORDER BY t.create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total == null ? 0 : total);
        return Result.success(page);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("crm:care:complaint:list")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Long tenantId = effTenant();
        Map<String, Object> t = jdbc.queryForList(
            "SELECT t.*, s.store_name "
            + "FROM crm_complaint_ticket t "
            + "LEFT JOIN pos_store s ON s.id = t.store_id "
            + "WHERE t.id = ? AND t.tenant_id = ? AND t.deleted = 0", id, tenantId)
            .stream().findFirst().orElse(null);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);

        List<Map<String, Object>> logs = jdbc.queryForList(
            "SELECT id, ticket_id, action_type, from_status, to_status, content, "
            + "operator_id, operator_name, create_time "
            + "FROM crm_complaint_log WHERE ticket_id = ? ORDER BY id ASC", id);

        Map<String, Object> data = new HashMap<>();
        data.put("ticket", Keys.camel(t));
        data.put("logs", Keys.camel(logs));
        return Result.success(data);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:edit")
    public Result<CrmComplaintTicket> create(@RequestBody Map<String, Object> body) {
        Long tenantId = effTenant();
        String complaintType = (String) body.get("complaintType");
        String subject = (String) body.get("subject");
        if (complaintType == null || subject == null)
            return Result.error(BizCode.BAD_REQUEST, "complaintType / subject 必填");

        Long storeId = body.get("storeId") == null
                ? Optional.ofNullable(TenantContextHolder.get().getStoreId()).orElse(1L)
                : ((Number) body.get("storeId")).longValue();
        Long brandId = null;
        try {
            brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id=?", Long.class, storeId);
        } catch (Exception ignored) {}

        CrmComplaintTicket t = new CrmComplaintTicket();
        t.setTenantId(tenantId);
        t.setBrandId(brandId);
        t.setStoreId(storeId);
        t.setTicketNo("TC" + System.currentTimeMillis());
        t.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
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
        Integer slaMinutes = body.get("slaMinutes") == null ? 1440 : ((Number) body.get("slaMinutes")).intValue();
        t.setSlaMinutes(slaMinutes);
        t.setSlaExpireAt(LocalDateTime.now().plusMinutes(slaMinutes));
        t.setStatus(1);
        t.setOperatorName(currentUser());
        ticketMapper.insert(t);

        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(tenantId);
        log.setTicketId(t.getId());
        log.setActionType("CREATE");
        log.setToStatus(1);
        log.setContent("工单创建: " + subject);
        log.setOperatorName(t.getOperatorName());
        logMapper.insert(log);

        return Result.success("已创建", t, null);
    }

    @PostMapping("/{id}/assign")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:handle")
    public Result<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmComplaintTicket t = ticketMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);
        if (t.getStatus() != 1 && t.getStatus() != 7)
            return Result.error(BizCode.CONFLICT, "当前状态不可分配", null);

        Long handlerId = body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue();
        String handlerName = (String) body.get("handlerName");
        if (handlerName == null || handlerName.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "handlerName 必填");

        jdbc.update("UPDATE crm_complaint_ticket SET handler_id=?, handler_name=?, status=2 WHERE id=?",
                handlerId, handlerName, id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("ASSIGN");
        log.setFromStatus(t.getStatus());
        log.setToStatus(2);
        log.setContent("分配至: " + handlerName);
        log.setOperatorName(currentUser());
        logMapper.insert(log);
        return Result.success("已分配", null, null);
    }

    @PostMapping("/{id}/process")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:handle")
    public Result<Void> process(@PathVariable Long id) {
        CrmComplaintTicket t = ticketMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);
        if (t.getStatus() != 2) return Result.error(BizCode.CONFLICT, "已分配状态才能开始处理", null);

        jdbc.update("UPDATE crm_complaint_ticket SET status=3 WHERE id=?", id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("REPLY");
        log.setFromStatus(2);
        log.setToStatus(3);
        log.setContent("开始处理");
        log.setOperatorName(currentUser());
        logMapper.insert(log);
        return Result.success("已开始处理", null, null);
    }

    @PostMapping("/{id}/resolve")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:handle")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmComplaintTicket t = ticketMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);
        String solutionType = (String) body.get("solutionType");
        if (solutionType == null) return Result.error(BizCode.BAD_REQUEST, "solutionType 必填");
        String solution = (String) body.get("solution");
        Object refundObj = body.get("refundAmount");
        BigDecimal refundAmount = refundObj == null ? BigDecimal.ZERO
                : new BigDecimal(refundObj.toString());

        jdbc.update("UPDATE crm_complaint_ticket SET status=5, solution=?, solution_type=?, "
                + "refund_amount=?, resolved_at=NOW() WHERE id=?",
                solution, solutionType, refundAmount, id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("RESOLVE");
        log.setFromStatus(t.getStatus());
        log.setToStatus(5);
        log.setContent("解决方案: " + solutionType + " - " + (solution == null ? "" : solution));
        log.setOperatorName(currentUser());
        logMapper.insert(log);
        return Result.success("已解决", null, null);
    }

    @PostMapping("/{id}/close")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:handle")
    public Result<Void> close(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrmComplaintTicket t = ticketMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);
        if (t.getStatus() != 5) return Result.error(BizCode.CONFLICT, "仅已解决工单可关闭", null);

        Integer rating = body.get("satisfactionRating") == null
                ? null : ((Number) body.get("satisfactionRating")).intValue();
        String note = (String) body.get("satisfactionNote");

        jdbc.update("UPDATE crm_complaint_ticket SET status=6, satisfaction_rating=?, satisfaction_note=? WHERE id=?",
                rating, note, id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("CLOSE");
        log.setFromStatus(5);
        log.setToStatus(6);
        log.setContent("客户满意度: " + rating + " - " + (note == null ? "" : note));
        log.setOperatorName(currentUser());
        logMapper.insert(log);
        return Result.success("已关闭", null, null);
    }

    @PostMapping("/{id}/escalate")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:handle")
    public Result<Void> escalate(@PathVariable Long id) {
        CrmComplaintTicket t = ticketMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "工单不存在", null);
        if (t.getStatus() == 5 || t.getStatus() == 6)
            return Result.error(BizCode.CONFLICT, "已关闭/已解决工单不可升级", null);

        jdbc.update("UPDATE crm_complaint_ticket SET status=7, escalate_count=escalate_count+1 WHERE id=?", id);
        CrmComplaintLog log = new CrmComplaintLog();
        log.setTenantId(t.getTenantId());
        log.setTicketId(id);
        log.setActionType("ESCALATE");
        log.setFromStatus(t.getStatus());
        log.setToStatus(7);
        log.setContent("升级处理（升级次数+1）");
        log.setOperatorName(currentUser());
        logMapper.insert(log);
        return Result.success("已升级", null, null);
    }

    @PostMapping("/satisfaction")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:edit")
    public Result<ComplaintSatisfaction> satisfaction(@RequestBody ComplaintSatisfaction s) {
        if (s.getTicketId() == null) return Result.error(BizCode.BAD_REQUEST, "ticketId 必填");
        if (s.getRating() == null) return Result.error(BizCode.BAD_REQUEST, "rating 必填");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getNpsScore() == null) {
            int r = s.getRating();
            int promoters = r >= 9 ? 1 : 0;
            int detractors = r <= 6 ? 1 : 0;
            s.setNpsScore((promoters - detractors) * 100);
        }
        if (s.getRatedAt() == null) s.setRatedAt(LocalDateTime.now());

        ComplaintSatisfaction exist = satisfactionMapper.selectOne(
            new LambdaQueryWrapper<ComplaintSatisfaction>()
                .eq(ComplaintSatisfaction::getTicketId, s.getTicketId()));
        if (exist != null) {
            if (s.getFeedback() != null) exist.setFeedback(s.getFeedback());
            exist.setRating(s.getRating());
            exist.setNpsScore(s.getNpsScore());
            exist.setRatedAt(s.getRatedAt());
            satisfactionMapper.updateById(exist);
            return Result.success("已更新", exist, null);
        }
        satisfactionMapper.insert(s);
        return Result.success("已评价", s, null);
    }

    @PostMapping("/demo-seed")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:complaint:edit")
    public Result<Map<String, Object>> demoSeed() {
        Long tenantId = effTenant();
        String[][] samples = {
            {"SERVICE", "1", "服务态度差", "技师接待时未主动打招呼，体验差。", "张丽", "13800138001"},
            {"STAFF",   "2", "技师操作粗暴", "项目过程中多次疼痛，技师未调整。", "王琳", "13800138002"},
            {"PRODUCT", "2", "产品疑似过敏", "使用产品后皮肤红肿，需要赔付检查费用。", "刘洋", "13800138003"},
            {"PRICE",   "3", "项目收费与公示不符", "套餐包含的项目现场被要求额外付费。", "陈芳", "13800138004"},
            {"ENVIRONMENT","4","店内卫生不达标", "房间有异味，地毯未清理。", "赵磊", "13800138005"},
            {"OTHER",   "1", "希望延长营业时间", "周末晚上 8 点就关门，不便。", "孙明", "13800138006"},
            {"SERVICE", "3", "上错产品", "实际产品与订单不一致。", "周敏", "13800138007"},
            {"STAFF",   "4", "员工与会员发生争执", "前台与会员语言冲突。", "吴强", "13800138008"}
        };
        int[] slaList = {240, 480, 1440, 2880};
        int n = 0;
        for (String[] s : samples) {
            CrmComplaintTicket t = new CrmComplaintTicket();
            t.setTenantId(tenantId);
            t.setBrandId(1L);
            t.setStoreId((long) (n % 4 + 1));
            t.setTicketNo("TC" + System.currentTimeMillis() + "_" + n);
            t.setMemberName(s[4]);
            t.setMemberPhone(s[5]);
            t.setComplaintType(s[0]);
            t.setLevel(Integer.parseInt(s[1]));
            t.setSubject(s[2]);
            t.setContent(s[3]);
            t.setSourceType("MEMBER");
            t.setSlaMinutes(slaList[n % slaList.length]);
            t.setSlaExpireAt(LocalDateTime.now().plusMinutes(slaList[n % slaList.length]));
            if (n == 0 || n == 1) {
                t.setStatus(1);
            } else if (n == 2 || n == 3) {
                t.setStatus(3);
                t.setHandlerName("客服-小张");
                t.setHandlerId(1001L);
            } else if (n == 4) {
                t.setStatus(5);
                t.setHandlerName("客服-小李");
                t.setHandlerId(1002L);
                t.setSolutionType("APOLOGY");
                t.setSolution("已向会员致歉并赠送项目券。");
            } else {
                t.setStatus(7);
                t.setHandlerName("客服-小王");
                t.setHandlerId(1003L);
                t.setEscalateCount(1);
            }
            t.setOperatorName("admin");
            ticketMapper.insert(t);

            CrmComplaintLog log = new CrmComplaintLog();
            log.setTenantId(tenantId);
            log.setTicketId(t.getId());
            log.setActionType("CREATE");
            log.setToStatus(t.getStatus());
            log.setContent("工单创建: " + t.getSubject());
            log.setOperatorName("admin");
            logMapper.insert(log);

            if (t.getStatus() != 1) {
                CrmComplaintLog l2 = new CrmComplaintLog();
                l2.setTenantId(tenantId);
                l2.setTicketId(t.getId());
                l2.setActionType("ASSIGN");
                l2.setFromStatus(1);
                l2.setToStatus(2);
                l2.setContent("分配至: " + (t.getHandlerName() == null ? "" : t.getHandlerName()));
                l2.setOperatorName("admin");
                logMapper.insert(l2);
            }
            if (t.getStatus() == 5 || t.getStatus() == 7) {
                CrmComplaintLog l3 = new CrmComplaintLog();
                l3.setTenantId(tenantId);
                l3.setTicketId(t.getId());
                l3.setActionType("RESOLVE");
                l3.setFromStatus(3);
                l3.setToStatus(5);
                l3.setContent("解决方案: " + (t.getSolutionType() == null ? "NONE" : t.getSolutionType()));
                l3.setOperatorName("admin");
                logMapper.insert(l3);
            }
            n++;
        }
        Map<String, Object> data = new HashMap<>();
            data.put("inserted", n);
        return Result.success("已插入演示数据", data, null);
    }

    @GetMapping("/category/list")
    @RequirePermission("crm:care:complaint:list")
    public Result<List<ComplaintCategory>> listCategory() {
        Long tenantId = effTenant();
        return Result.success(categoryMapper.selectList(
            new LambdaQueryWrapper<ComplaintCategory>()
                .eq(ComplaintCategory::getTenantId, tenantId)
                .eq(ComplaintCategory::getEnabled, 1)
                .orderByAsc(ComplaintCategory::getSortOrder)));
    }

    @GetMapping("/sla/list")
    @RequirePermission("crm:care:complaint:list")
    public Result<List<ComplaintSlaPolicy>> listSla() {
        Long tenantId = effTenant();
        return Result.success(slaPolicyMapper.selectList(
            new LambdaQueryWrapper<ComplaintSlaPolicy>()
                .eq(ComplaintSlaPolicy::getTenantId, tenantId)));
    }
}