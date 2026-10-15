package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import com.meiyetec.util.XlsxExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R2.1-A T-A13：疗程方案 + 阶段 CRUD + 会员进度跟踪
 * 注意：直接用 @RestController，不混 @Service，避免被重复扫描
 */
@RestController
@RequestMapping("/biz/treatment")
@RequiredArgsConstructor
public class BizTreatmentService {

    private final BizTreatmentPlanMapper planMapper;
    private final BizTreatmentPhaseMapper phaseMapper;
    private final BizServiceItemMapper itemMapper;
    private final BizServiceCategoryMapper categoryMapper;
    private final MemberTreatmentProgressMapper progressMapper;
    private final MemberTreatmentSessionMapper sessionMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================== 方案 CRUD ====================

    @GetMapping("/plan/stats")
    public Result<Map<String, Object>> planStats() {
        Long tenantId = currentTenantId();
        Map<String, Object> data = new HashMap<>();
        Long totalPlans = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ?",
            Long.class, tenantId);
        Long enabled = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ? AND status = 1",
            Long.class, tenantId);
        Long disabled = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ? AND status = 0",
            Long.class, tenantId);
        Double totalPrice = jdbc.queryForObject(
            "SELECT COALESCE(SUM(plan_price), 0) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ?",
            Double.class, tenantId);
        Double avgPrice = jdbc.queryForObject(
            "SELECT COALESCE(AVG(plan_price), 0) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ?",
            Double.class, tenantId);
        Long serviceCount = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT service_id) FROM biz_treatment_plan WHERE deleted = 0 AND tenant_id = ?",
            Long.class, tenantId);

        data.put("totalPlans", totalPlans == null ? 0 : totalPlans);
        data.put("enabled", enabled == null ? 0 : enabled);
        data.put("disabled", disabled == null ? 0 : disabled);
        data.put("totalPrice", totalPrice == null ? 0.0 : totalPrice);
        data.put("avgPrice", avgPrice == null ? 0.0 : avgPrice);
        data.put("serviceCount", serviceCount == null ? 0 : serviceCount);
        return Result.success(data);
    }

    @GetMapping("/plan/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> planPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long tenantId = currentTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT p.id, p.plan_code, p.plan_name, p.service_id, p.category_id, " +
            "p.total_sessions, p.cycle_days, p.plan_price, p.member_price, " +
            "p.description, p.status, p.create_time, p.update_time, " +
            "i.service_code, i.service_name, c.category_name " +
            "FROM biz_treatment_plan p " +
            "LEFT JOIN biz_service_item i ON p.service_id = i.id " +
            "LEFT JOIN biz_service_category c ON p.category_id = c.id " +
            "WHERE p.deleted = 0 AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql.append("AND p.store_id = ? "); args.add(storeId); }
        if (categoryId != null) { sql.append("AND p.category_id = ? "); args.add(categoryId); }
        if (status != null) { sql.append("AND p.status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.plan_code LIKE ? OR p.plan_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY p.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM biz_treatment_plan p WHERE p.deleted = 0 AND p.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (storeId != null) { csql.append("AND p.store_id = ? "); cargs.add(storeId); }
        if (categoryId != null) { csql.append("AND p.category_id = ? "); cargs.add(categoryId); }
        if (status != null) { csql.append("AND p.status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (p.plan_code LIKE ? OR p.plan_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/plan/{id}")
    public Result<Map<String, Object>> planDetail(@PathVariable Long id) {
        BizTreatmentPlan plan = planMapper.selectById(id);
        if (plan == null) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        if (plan.getTenantId() != null && !plan.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权访问该方案");
        }
        List<BizTreatmentPhase> phases = phaseMapper.selectList(
                new LambdaQueryWrapper<BizTreatmentPhase>()
                        .eq(BizTreatmentPhase::getPlanId, id)
                        .orderByAsc(BizTreatmentPhase::getPhaseNo));
        Map<String, Object> result = new HashMap<>();
        result.put("plan", plan);
        result.put("phases", phases);
        return Result.success(result);
    }

    @PostMapping("/plan")
    @Transactional(rollbackFor = Exception.class)
    public Result<BizTreatmentPlan> createPlan(@RequestBody Map<String, Object> body) {
        BizTreatmentPlan p = new BizTreatmentPlan();
        p.setPlanCode((String) body.get("planCode"));
        p.setPlanName((String) body.get("planName"));
        if (body.get("serviceId") != null) p.setServiceId(((Number) body.get("serviceId")).longValue());
        if (body.get("categoryId") != null) p.setCategoryId(((Number) body.get("categoryId")).longValue());
        p.setTotalSessions(body.get("totalSessions") == null ? 1 : ((Number) body.get("totalSessions")).intValue());
        if (body.get("cycleDays") != null) p.setCycleDays(((Number) body.get("cycleDays")).intValue());
        p.setPlanPrice(new BigDecimal(body.get("planPrice") == null ? "0" : body.get("planPrice").toString()));
        if (body.get("memberPrice") != null)
            p.setMemberPrice(new BigDecimal(body.get("memberPrice").toString()));
        p.setDescription((String) body.get("description"));
        p.setSop((String) body.get("sop"));
        p.setStatus(body.get("status") == null ? 1 : ((Number) body.get("status")).intValue());

        if (p.getPlanCode() == null || p.getPlanCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "方案编码必填");
        if (p.getPlanName() == null || p.getPlanName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "方案名称必填");
        if (p.getServiceId() == null)
            return Result.error(BizCode.BAD_REQUEST, "方案必须关联服务项目");

        Long exist = planMapper.selectCount(new LambdaQueryWrapper<BizTreatmentPlan>()
                .eq(BizTreatmentPlan::getPlanCode, p.getPlanCode()));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "方案编码已存在");

        if (p.getTenantId() == null) p.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (p.getStoreId() == null) p.setStoreId(1L);
        planMapper.insert(p);

        // 同步写阶段
        List<Map<String, Object>> phases = (List<Map<String, Object>>) body.get("phases");
        if (phases != null) {
            int totalInPhases = 0;
            for (Map<String, Object> ph : phases) {
                BizTreatmentPhase phase = new BizTreatmentPhase();
                phase.setPlanId(p.getId());
                phase.setPhaseNo(((Number) ph.get("phaseNo")).intValue());
                phase.setPhaseName((String) ph.get("phaseName"));
                int cnt = ph.get("sessionCount") == null ? 1 : ((Number) ph.get("sessionCount")).intValue();
                phase.setSessionCount(cnt);
                phase.setSop((String) ph.get("sop"));
                phase.setExpectedDuration(ph.get("expectedDuration") == null ? 0
                        : ((Number) ph.get("expectedDuration")).intValue());
                phase.setSortOrder(phase.getPhaseNo());
                phaseMapper.insert(phase);
                totalInPhases += cnt;
            }
            // 校验：所有阶段总次数 = plan.totalSessions
            if (totalInPhases != p.getTotalSessions()) {
                throw new BusinessException(BizCode.BAD_REQUEST,
                        "所有阶段 sessionCount 之和(" + totalInPhases + ") 必须等于方案总次数("
                                + p.getTotalSessions() + ")");
            }
        }
        return Result.success("创建成功", p);
    }

    @PutMapping("/plan/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<BizTreatmentPlan> updatePlan(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizTreatmentPlan old = planMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        if (old.getTenantId() != null && !old.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权修改该方案");
        }
        if (body.get("planName") != null) old.setPlanName((String) body.get("planName"));
        if (body.get("categoryId") != null) old.setCategoryId(((Number) body.get("categoryId")).longValue());
        if (body.get("totalSessions") != null) old.setTotalSessions(((Number) body.get("totalSessions")).intValue());
        if (body.get("cycleDays") != null) old.setCycleDays(((Number) body.get("cycleDays")).intValue());
        if (body.get("planPrice") != null) old.setPlanPrice(new BigDecimal(body.get("planPrice").toString()));
        if (body.get("memberPrice") != null) old.setMemberPrice(new BigDecimal(body.get("memberPrice").toString()));
        if (body.get("description") != null) old.setDescription((String) body.get("description"));
        if (body.get("sop") != null) old.setSop((String) body.get("sop"));
        if (body.get("status") != null) old.setStatus(((Number) body.get("status")).intValue());
        planMapper.updateById(old);

        // 如果传了 phases，全量替换
        List<Map<String, Object>> phases = (List<Map<String, Object>>) body.get("phases");
        if (phases != null) {
            // 软删旧阶段
            phaseMapper.delete(new LambdaQueryWrapper<BizTreatmentPhase>()
                    .eq(BizTreatmentPhase::getPlanId, id));
            int totalInPhases = 0;
            for (Map<String, Object> ph : phases) {
                BizTreatmentPhase phase = new BizTreatmentPhase();
                phase.setPlanId(id);
                phase.setPhaseNo(((Number) ph.get("phaseNo")).intValue());
                phase.setPhaseName((String) ph.get("phaseName"));
                int cnt = ph.get("sessionCount") == null ? 1 : ((Number) ph.get("sessionCount")).intValue();
                phase.setSessionCount(cnt);
                phase.setSop((String) ph.get("sop"));
                phase.setExpectedDuration(ph.get("expectedDuration") == null ? 0
                        : ((Number) ph.get("expectedDuration")).intValue());
                phase.setSortOrder(phase.getPhaseNo());
                phaseMapper.insert(phase);
                totalInPhases += cnt;
            }
            if (totalInPhases != old.getTotalSessions()) {
                throw new BusinessException(BizCode.BAD_REQUEST,
                        "所有阶段 sessionCount 之和(" + totalInPhases + ") 必须等于方案总次数("
                                + old.getTotalSessions() + ")");
            }
        }
        return Result.success("更新成功", planMapper.selectById(id));
    }

    @DeleteMapping("/plan/{id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        BizTreatmentPlan old = planMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        if (old.getTenantId() != null && !old.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权删除该方案");
        }
        Long inUse = progressMapper.selectCount(new LambdaQueryWrapper<MemberTreatmentProgress>()
                .eq(MemberTreatmentProgress::getPlanId, id));
        if (inUse > 0) return Result.error(BizCode.CONFLICT, "该方案已被会员使用，无法删除");
        planMapper.deleteById(id);
        // 软删阶段
        phaseMapper.delete(new LambdaQueryWrapper<BizTreatmentPhase>()
                .eq(BizTreatmentPhase::getPlanId, id));
        return Result.success("删除成功", null);
    }

    // ==================== 阶段 CRUD ====================

    @PostMapping("/phase")
    public Result<BizTreatmentPhase> createPhase(@RequestBody BizTreatmentPhase ph) {
        if (ph.getPlanId() == null) return Result.error(BizCode.BAD_REQUEST, "planId 必填");
        if (ph.getPhaseName() == null || ph.getPhaseName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "阶段名称必填");
        if (ph.getSessionCount() == null || ph.getSessionCount() <= 0)
            return Result.error(BizCode.BAD_REQUEST, "阶段次数必须 > 0");
        if (ph.getPhaseNo() == null) ph.setPhaseNo(1);
        if (ph.getTenantId() == null) ph.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (ph.getSortOrder() == null) ph.setSortOrder(ph.getPhaseNo());
        phaseMapper.insert(ph);
        return Result.success("创建成功", ph);
    }

    @PutMapping("/phase/{id}")
    public Result<BizTreatmentPhase> updatePhase(@PathVariable Long id, @RequestBody BizTreatmentPhase ph) {
        BizTreatmentPhase old = phaseMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "阶段不存在");
        ph.setId(id);
        phaseMapper.updateById(ph);
        return Result.success("更新成功", phaseMapper.selectById(id));
    }

    @DeleteMapping("/phase/{id}")
    public Result<Void> deletePhase(@PathVariable Long id) {
        BizTreatmentPhase old = phaseMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "阶段不存在");
        phaseMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    // ==================== 会员进度 ====================

    /**
     * 给会员开疗程（创建一条 member_treatment_progress 记录）
     */
    @PostMapping("/progress")
    @Transactional(rollbackFor = Exception.class)
    public Result<MemberTreatmentProgress> createProgress(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long planId = body.get("planId") == null ? null : ((Number) body.get("planId")).longValue();
        Long packageOrderId = body.get("packageOrderId") == null ? null
                : ((Number) body.get("packageOrderId")).longValue();
        Long operatorId = body.get("operatorId") == null ? 1L : ((Number) body.get("operatorId")).longValue();
        String operatorName = (String) body.getOrDefault("operatorName", "admin");

        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (planId == null) return Result.error(BizCode.BAD_REQUEST, "planId 必填");

        BizTreatmentPlan plan = planMapper.selectById(planId);
        if (plan == null) return Result.error(BizCode.NOT_FOUND, "疗程方案不存在");

        MemberTreatmentProgress p = new MemberTreatmentProgress();
        p.setMemberId(memberId);
        p.setPlanId(planId);
        p.setServiceId(plan.getServiceId());
        p.setPackageOrderId(packageOrderId);
        p.setTotalSessions(plan.getTotalSessions());
        p.setUsedSessions(0);
        p.setRemainingSessions(plan.getTotalSessions());
        p.setCurrentPhaseNo(1);
        p.setStartedAt(LocalDateTime.now());
        // 预计完成时间 = 开始 + totalSessions * cycleDays
        if (plan.getCycleDays() != null && plan.getCycleDays() > 0) {
            p.setExpectedFinishAt(p.getStartedAt().plusDays((long) plan.getTotalSessions() * plan.getCycleDays()));
        }
        p.setStatus(1);
        p.setOperatorId(operatorId);
        p.setOperatorName(operatorName);
        p.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        progressMapper.insert(p);
        return Result.success("开疗程成功", p);
    }

    /**
     * 完成一次疗程服务（到店打卡）：扣减 remaining，生成 session 记录
     */
    @PostMapping("/progress/{id}/session")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> recordSession(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MemberTreatmentProgress p = progressMapper.selectById(id);
        if (p == null) return Result.error(BizCode.NOT_FOUND, "疗程进度不存在");
        if (p.getStatus() != null && p.getStatus() != 1) {
            return Result.error(BizCode.CONFLICT, "疗程已停用/完成，不能再记录");
        }
        if (p.getRemainingSessions() == null || p.getRemainingSessions() <= 0) {
            return Result.error(BizCode.CONFLICT, "疗程剩余次数为 0，请检查");
        }

        Integer phaseNo = body.get("phaseNo") == null ? p.getCurrentPhaseNo() : ((Number) body.get("phaseNo")).intValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        Long employeeId = body.get("employeeId") == null ? null : ((Number) body.get("employeeId")).longValue();
        String employeeName = (String) body.get("employeeName");
        String remark = (String) body.get("remark");

        // 计算阶段内的第几次
        Integer sessionNo = jdbc.queryForObject(
            "SELECT COALESCE(MAX(session_no), 0) + 1 FROM member_treatment_session " +
            "WHERE deleted = 0 AND progress_id = ? AND phase_no = ?",
            Integer.class, id, phaseNo);

        MemberTreatmentSession session = new MemberTreatmentSession();
        session.setProgressId(id);
        session.setMemberId(p.getMemberId());
        session.setPlanId(p.getPlanId());
        session.setPhaseNo(phaseNo);
        session.setSessionNo(sessionNo);
        session.setOrderId(orderId);
        session.setEmployeeId(employeeId);
        session.setEmployeeName(employeeName);
        session.setSessionAt(LocalDateTime.now());
        session.setRemark(remark);
        session.setTenantId(p.getTenantId());
        sessionMapper.insert(session);

        // 乐观更新进度（避免并发打卡重复扣减）
        int rows = jdbc.update(
            "UPDATE member_treatment_progress " +
            "SET used_sessions = used_sessions + 1, " +
            "    remaining_sessions = remaining_sessions - 1, " +
            "    last_session_at = NOW() " +
            "WHERE id = ? AND remaining_sessions > 0 AND status = 1",
            id);
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "并发打卡失败，请重试");
        }

        // 检查是否需要切换阶段（按阶段总次数累积）
        checkAndAdvancePhase(p, phaseNo);

        // 重新读取进度（已带 used/remaining/status 更新）
        MemberTreatmentProgress refreshed = progressMapper.selectById(id);
        if (refreshed.getRemainingSessions() != null && refreshed.getRemainingSessions() == 0) {
            jdbc.update("UPDATE member_treatment_progress SET status = 2, finished_at = NOW() WHERE id = ?", id);
            refreshed.setStatus(2);
            refreshed.setFinishedAt(LocalDateTime.now());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("session", session);
        data.put("progress", refreshed);
        return Result.success("已记录", data);
    }

    private void checkAndAdvancePhase(MemberTreatmentProgress p, Integer phaseNo) {
        // 当前阶段已服务次数
        Integer usedInPhase = jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_treatment_session " +
            "WHERE deleted = 0 AND progress_id = ? AND phase_no = ?",
            Integer.class, p.getId(), phaseNo);
        Integer phaseTotal = jdbc.queryForObject(
            "SELECT session_count FROM biz_treatment_phase " +
            "WHERE deleted = 0 AND plan_id = ? AND phase_no = ?",
            Integer.class, p.getPlanId(), phaseNo);
        if (usedInPhase != null && phaseTotal != null
                && usedInPhase >= phaseTotal
                && phaseNo != null && phaseNo < 99) {
            // 推进到下一阶段
            Integer next = phaseNo + 1;
            Long nextCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_treatment_phase " +
                "WHERE deleted = 0 AND plan_id = ? AND phase_no = ?",
                Long.class, p.getPlanId(), next);
            if (nextCount != null && nextCount > 0) {
                jdbc.update("UPDATE member_treatment_progress SET current_phase_no = ? WHERE id = ?",
                        next, p.getId());
            }
        }
    }

    /**
     * 会员的疗程进度列表
     */
    @GetMapping("/progress/member/{memberId}")
    public Result<List<Map<String, Object>>> memberProgress(@PathVariable Long memberId) {
        Long tenantId = currentTenantId();
        String sql =
            "SELECT p.id, p.plan_id, p.service_id, p.total_sessions, p.used_sessions, " +
            "       p.remaining_sessions, p.current_phase_no, p.status, p.started_at, " +
            "       p.last_session_at, p.expected_finish_at, p.finished_at, " +
            "       pl.plan_name, pl.plan_code, pl.total_sessions AS plan_total, " +
            "       pl.cycle_days, " +
            "       i.service_name, " +
            "       (SELECT phase_name FROM biz_treatment_phase " +
            "        WHERE deleted = 0 AND plan_id = p.plan_id AND phase_no = p.current_phase_no LIMIT 1) AS current_phase_name, " +
            "       ROUND(p.used_sessions * 100.0 / GREATEST(p.total_sessions, 1), 1) AS progress_percent " +
            "FROM member_treatment_progress p " +
            "JOIN biz_treatment_plan pl ON p.plan_id = pl.id " +
            "LEFT JOIN biz_service_item i ON p.service_id = i.id " +
            "WHERE p.deleted = 0 AND p.tenant_id = ? AND p.member_id = ? " +
            "ORDER BY p.started_at DESC";
        return Result.success(Keys.camel(jdbc.queryForList(sql, tenantId, memberId)));
    }

    /**
     * 进度详情（含阶段信息）
     */
    @GetMapping("/progress/{id}")
    public Result<Map<String, Object>> progressDetail(@PathVariable Long id) {
        MemberTreatmentProgress p = progressMapper.selectById(id);
        if (p == null) return Result.error(BizCode.NOT_FOUND, "进度不存在");
        if (p.getTenantId() != null && !p.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权访问该进度");
        }
        List<BizTreatmentPhase> phases = phaseMapper.selectList(
                new LambdaQueryWrapper<BizTreatmentPhase>()
                        .eq(BizTreatmentPhase::getPlanId, p.getPlanId())
                        .orderByAsc(BizTreatmentPhase::getPhaseNo));
        List<MemberTreatmentSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<MemberTreatmentSession>()
                        .eq(MemberTreatmentSession::getProgressId, id)
                        .orderByDesc(MemberTreatmentSession::getSessionAt));
        Map<String, Object> data = new HashMap<>();
        data.put("progress", p);
        data.put("phases", phases);
        data.put("sessions", sessions);
        return Result.success(data);
    }

    @PostMapping("/progress/{id}/stop")
    public Result<Void> stopProgress(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        MemberTreatmentProgress p = progressMapper.selectById(id);
        if (p == null) return Result.error(BizCode.NOT_FOUND, "进度不存在");
        if (p.getTenantId() != null && !p.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权停用该进度");
        }
        if (p.getStatus() != null && p.getStatus() == 3) return Result.success("已停用", null);
        jdbc.update("UPDATE member_treatment_progress SET status = 3 WHERE id = ?", id);
        return Result.success("已停用", null);
    }

    // ==================== 导出 XLSX（R2.1-A T-A14 + T-A16 升级） ====================

    @GetMapping("/plan/export")
    public ResponseEntity<byte[]> exportPlan(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) throws Exception {
        Long tenantId = currentTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT p.plan_code, p.plan_name, i.service_name, c.category_name, " +
            "p.total_sessions, p.cycle_days, p.plan_price, p.member_price, " +
            "p.description, p.status, p.create_time " +
            "FROM biz_treatment_plan p " +
            "LEFT JOIN biz_service_item i ON p.service_id = i.id " +
            "LEFT JOIN biz_service_category c ON p.category_id = c.id " +
            "WHERE p.deleted = 0 AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql.append("AND p.store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND p.status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.plan_code LIKE ? OR p.plan_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY p.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        String tenantName = resolveTenantName();
        String watermark = "疗程方案 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用";
        String[] headers = {"方案编码", "方案名称", "服务项目", "分类", "总次数", "周期(天)",
                            "方案价", "会员价", "描述", "状态", "创建时间"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer st = (Integer) r.get("status");
            String statusDesc = st == null ? "停用" : st == 1 ? "启用" : "停用";
            List<Object> line = new ArrayList<>();
            line.add(XlsxExportUtil.str(r.get("plan_code")));
            line.add(XlsxExportUtil.str(r.get("plan_name")));
            line.add(XlsxExportUtil.str(r.get("service_name")));
            line.add(XlsxExportUtil.str(r.get("category_name")));
            line.add(r.get("total_sessions"));
            line.add(r.get("cycle_days"));
            line.add(r.get("plan_price"));
            line.add(r.get("member_price"));
            line.add(XlsxExportUtil.str(r.get("description")));
            line.add(statusDesc);
            line.add(XlsxExportUtil.str(r.get("create_time")));
            body.add(line);
        }

        byte[] bytes = XlsxExportUtil.export("疗程方案", watermark, headers, body);
        String filename = "疗程方案_" + LocalDate.now() + ".xlsx";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        h.add("X-Tenant-Name", new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(h).body(bytes);
    }

    @GetMapping("/plan/template")
    public ResponseEntity<byte[]> planImportTemplate() throws Exception {
        String[] headers = {"方案编码*", "方案名称*", "服务项目编码", "分类编码", "总次数*", "周期(天)*",
                            "方案价", "会员价", "状态(启用/停用)", "描述", "SOP"};
        List<List<Object>> body = new ArrayList<>();
        body.add(List.of("PL001", "面部护理 6 次套餐", "SV001", "FACE", "6", "60",
                "1080.00", "980.00", "启用", "六次完整面部护理", "见 SOP 详情"));
        body.add(List.of("PL002", "身体按摩 10 次卡", "SV002", "BODY", "10", "90",
                "2680.00", "2480.00", "启用", "全身按摩十次卡", "见 SOP 详情"));
        byte[] bytes = XlsxExportUtil.export("疗程方案导入模板", "请按示例格式填写，必填项带 *", headers, body);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String fn = "疗程方案导入模板.xlsx";
        h.setContentDispositionFormData("attachment",
                new String(fn.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(h).body(bytes);
    }

    @PostMapping("/plan/import")
    public Result<Map<String, Object>> importPlan(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        Long tenantId = currentTenantId();
        if (file == null || file.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "请上传文件");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) return Result.error(BizCode.BAD_REQUEST, "仅支持 .xlsx 格式");

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("planCode", "方案编码*");
        headerMap.put("planName", "方案名称*");
        headerMap.put("serviceCode", "服务项目编码");
        headerMap.put("categoryCode", "分类编码");
        headerMap.put("totalSessions", "总次数*");
        headerMap.put("cycleDays", "周期(天)*");
        headerMap.put("planPrice", "方案价");
        headerMap.put("memberPrice", "会员价");
        headerMap.put("status", "状态(启用/停用)");
        headerMap.put("description", "描述");
        headerMap.put("sop", "SOP");

        List<Map<String, Object>> rows = XlsxExportUtil.importRows(file.getInputStream(), headerMap);
        if (rows.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "模板无数据");

        // 查 serviceCode → id
        Map<String, Long> svcMap = new HashMap<>();
        List<Map<String, Object>> svcRows = jdbc.queryForList(
                "SELECT id, service_code FROM biz_service_item WHERE deleted = 0 AND tenant_id = ?", tenantId);
        for (Map<String, Object> m : svcRows) {
            svcMap.put(String.valueOf(m.get("service_code")), ((Number) m.get("id")).longValue());
        }
        // 查分类
        Map<String, Long> catMap = new HashMap<>();
        List<Map<String, Object>> catRows = jdbc.queryForList(
                "SELECT id, category_code FROM biz_service_category WHERE deleted = 0 AND tenant_id = ?", tenantId);
        for (Map<String, Object> m : catRows) {
            catMap.put(String.valueOf(m.get("category_code")), ((Number) m.get("id")).longValue());
        }

        int ok = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int rowNo = i + 3;
            try {
                String code = XlsxExportUtil.str(r.get("planCode"));
                String name = XlsxExportUtil.str(r.get("planName"));
                String sessions = XlsxExportUtil.str(r.get("totalSessions"));
                String days = XlsxExportUtil.str(r.get("cycleDays"));
                if (code.isEmpty()) throw new RuntimeException("方案编码不能为空");
                if (name.isEmpty()) throw new RuntimeException("方案名称不能为空");
                if (sessions.isEmpty()) throw new RuntimeException("总次数不能为空");
                if (days.isEmpty()) throw new RuntimeException("周期不能为空");
                Long exist = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM biz_treatment_plan WHERE deleted = 0 AND plan_code = ?",
                        Long.class, code);
                if (exist != null && exist > 0) throw new RuntimeException("方案编码已存在");
                Long svcId = null;
                String svcCode = XlsxExportUtil.str(r.get("serviceCode"));
                if (!svcCode.isEmpty()) svcId = svcMap.get(svcCode);
                Long catId = null;
                String catCode = XlsxExportUtil.str(r.get("categoryCode"));
                if (!catCode.isEmpty()) catId = catMap.get(catCode);
                com.meiyetec.entity.BizTreatmentPlan p = new com.meiyetec.entity.BizTreatmentPlan();
                p.setPlanCode(code);
                p.setPlanName(name);
                p.setServiceId(svcId);
                p.setCategoryId(catId);
                p.setTotalSessions(Integer.parseInt(sessions));
                p.setCycleDays(Integer.parseInt(days));
                p.setPlanPrice(XlsxExportUtil.bigDec(r.get("planPrice")));
                p.setMemberPrice(XlsxExportUtil.bigDec(r.get("memberPrice")));
                String st = XlsxExportUtil.str(r.get("status"));
                p.setStatus(st.contains("启用") || st.equals("1") ? 1 : 0);
                p.setDescription(XlsxExportUtil.str(r.get("description")));
                p.setSop(XlsxExportUtil.str(r.get("sop")));
                p.setTenantId(tenantId);
                p.setStoreId(1L);
                jdbc.update(
                        "INSERT INTO biz_treatment_plan(tenant_id, store_id, plan_code, plan_name, service_id, category_id, " +
                        "total_sessions, cycle_days, plan_price, member_price, description, sop, status) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        tenantId, 1L, p.getPlanCode(), p.getPlanName(), p.getServiceId(), p.getCategoryId(),
                        p.getTotalSessions(), p.getCycleDays(), p.getPlanPrice(), p.getMemberPrice(),
                        p.getDescription(), p.getSop(), p.getStatus());
                ok++;
            } catch (Exception e) {
                fail++;
                if (errors.size() < 20) errors.add("第 " + rowNo + " 行：" + e.getMessage());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", rows.size());
        data.put("success", ok);
        data.put("failed", fail);
        data.put("errors", errors);
        return Result.success("导入完成，成功 " + ok + " 条，失败 " + fail + " 条", data);
    }

    @GetMapping("/progress/export")
    public ResponseEntity<byte[]> exportProgress(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Integer status) throws Exception {
        Long tenantId = currentTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT m.name member_name, pl.plan_code, pl.plan_name, i.service_name, " +
            "p.total_sessions, p.used_sessions, p.remaining_sessions, p.current_phase_no, " +
            "p.started_at, p.last_session_at, p.expected_finish_at, p.finished_at, p.status " +
            "FROM member_treatment_progress p " +
            "JOIN biz_treatment_plan pl ON p.plan_id = pl.id " +
            "LEFT JOIN biz_service_item i ON p.service_id = i.id " +
            "LEFT JOIN crm_member m ON p.member_id = m.id " +
            "WHERE p.deleted = 0 AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND p.member_id = ? "); args.add(memberId); }
        if (status != null) { sql.append("AND p.status = ? "); args.add(status); }
        sql.append("ORDER BY p.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        String tenantName = resolveTenantName();
        String watermark = "会员疗程进度 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用";
        String[] headers = {"会员", "方案编码", "方案名称", "服务项目", "总次数", "已用", "剩余",
                            "当前阶段", "开始", "最近服务", "预计完成", "实际完成", "状态"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer st = (Integer) r.get("status");
            String statusDesc = st == null ? "未知" : st == 1 ? "进行中" : st == 2 ? "已完成" : "已停用";
            List<Object> line = new ArrayList<>();
            line.add(XlsxExportUtil.str(r.get("member_name")));
            line.add(XlsxExportUtil.str(r.get("plan_code")));
            line.add(XlsxExportUtil.str(r.get("plan_name")));
            line.add(XlsxExportUtil.str(r.get("service_name")));
            line.add(r.get("total_sessions"));
            line.add(r.get("used_sessions"));
            line.add(r.get("remaining_sessions"));
            line.add(r.get("current_phase_no"));
            line.add(XlsxExportUtil.str(r.get("started_at")));
            line.add(XlsxExportUtil.str(r.get("last_session_at")));
            line.add(XlsxExportUtil.str(r.get("expected_finish_at")));
            line.add(XlsxExportUtil.str(r.get("finished_at")));
            line.add(statusDesc);
            body.add(line);
        }

        byte[] bytes = XlsxExportUtil.export("会员疗程进度", watermark, headers, body);
        String filename = "会员疗程进度_" + LocalDate.now() + ".xlsx";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        h.add("X-Tenant-Name", new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(h).body(bytes);
    }

    /**
     * 取当前会话的 tenantId；未登录或上下文为空时返回 0L（兜底）。
     */
    private Long currentTenantId() {
        Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        return tid == null ? 0L : tid;
    }

    private String resolveTenantName() {
        try {
            Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) return String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}
        return "默认租户";
    }
}
