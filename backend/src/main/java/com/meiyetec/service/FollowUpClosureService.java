package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpTouch;
import com.meiyetec.entity.CrmReview;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.CrmFollowUpNoteMapper;
import com.meiyetec.mapper.CrmFollowUpTouchMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P0.2：回访闭环（补强）
 *  - 触达（touch）API：电话/短信/微信/...
 *  - 操作日志（note）API
 *  - 闭环统计 API：完成率 / 平均完成时长 / 评级分布 / 触达漏斗 / 重开率 / 重单率
 *
 * 设计：补强 CustomerCareService 已有的 CRUD；本类专注"执行痕迹 + 统计分析"。
 */
@Slf4j
@RestController
@RequestMapping("/crm/care/loop")
@RequiredArgsConstructor
public class FollowUpClosureService {

    private final CrmFollowUpTouchMapper touchMapper;
    private final CrmFollowUpNoteMapper noteMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 触达日志
    // ====================================================================

    /** 1.1 记录一次触达（电话/短信/微信/...） */
    @PostMapping("/touch")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmFollowUpTouch> touch(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long taskId = body.get("taskId") == null ? null
                : ((Number) body.get("taskId")).longValue();
        String touchType = (String) body.get("touchType");
        if (taskId == null || touchType == null)
            return Result.error(BizCode.BAD_REQUEST, "taskId / touchType 必填");

        // 校验任务存在 + 当前状态可触达
        Map<String, Object> task = jdbc.queryForList(
            "SELECT id, status, member_phone, member_name, touch_count " +
            "FROM crm_follow_up_task WHERE id = ? AND deleted = 0", taskId)
            .stream().findFirst().orElse(null);
        if (task == null) return Result.error(BizCode.NOT_FOUND, "回访任务不存在");
        int status = ((Number) task.get("status")).intValue();
        if (status == 3 || status == 4)
            return Result.error(BizCode.CONFLICT, "已完成/已关闭任务不可再触达");

        CrmFollowUpTouch t = new CrmFollowUpTouch();
        t.setTenantId(tenantId);
        t.setTaskId(taskId);
        t.setTouchType(touchType);
        t.setTouchStatus((String) body.getOrDefault("touchStatus", "SENT"));
        t.setContact((String) body.getOrDefault("contact", task.get("member_phone")));
        t.setContent((String) body.get("content"));
        t.setReplyContent((String) body.get("replyContent"));
        t.setDurationSeconds(body.get("durationSeconds") == null ? null
                : ((Number) body.get("durationSeconds")).intValue());
        t.setOperatorId(body.get("operatorId") == null ? null
                : ((Number) body.get("operatorId")).longValue());
        t.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        t.setRemark((String) body.get("remark"));
        t.setTouchedAt(LocalDateTime.now());
        if ("REPLIED".equals(t.getTouchStatus())) {
            t.setReplyAt(LocalDateTime.now());
        }
        touchMapper.insert(t);

        // 自动：写操作日志 + 累加 touch_count
        jdbc.update(
            "UPDATE crm_follow_up_task SET touch_count = IFNULL(touch_count, 0) + 1, " +
            "       last_touched_at = NOW(), " +
            "       status = CASE WHEN status = 1 THEN 2 ELSE status END " +
            "WHERE id = ?", taskId);
        writeNote(taskId, tenantId, "TOUCH", status, status == 1 ? 2 : status,
                touchType + ": " + (t.getContent() == null ? "" : t.getContent()),
                t.getOperatorId(), t.getOperatorName());

        return Result.success("已记录触达", t);
    }

    /** 1.2 任务触达列表 */
    @GetMapping("/touch/list")
    public Result<List<CrmFollowUpTouch>> touchList(@RequestParam Long taskId) {
        return Result.success(touchMapper.selectList(
                new LambdaQueryWrapper<CrmFollowUpTouch>()
                        .eq(CrmFollowUpTouch::getTaskId, taskId)
                        .orderByDesc(CrmFollowUpTouch::getTouchedAt)));
    }

    // ====================================================================
    // 2. 任务操作日志（note）
    // ====================================================================

    @GetMapping("/note/list")
    public Result<List<CrmFollowUpNote>> noteList(@RequestParam Long taskId) {
        return Result.success(noteMapper.selectList(
                new LambdaQueryWrapper<CrmFollowUpNote>()
                        .eq(CrmFollowUpNote::getTaskId, taskId)
                        .orderByDesc(CrmFollowUpNote::getCreateTime)));
    }

    // ====================================================================
    // 3. 闭环统计（核心：闭合率 + 触达漏斗 + 评级分布）
    // ====================================================================

    /**
     * 3.1 闭环看板统计
     * 输入：startDate / endDate / storeId（可选）/ ownerId（可选）
     * 输出：
     *  - 任务总数 / 完成数 / 完成率 / 重开率 / SLA 逾期率
     *  - 平均完成时长（分钟）
     *  - 触达漏斗（touchCount 分布）
     *  - 评级分布
     *  - 任务类型分布
     */
    @GetMapping("/closure/stats")
    public Result<Map<String, Object>> closureStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long ownerId) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        StringBuilder where = new StringBuilder(
            "FROM crm_follow_up_task " +
            "WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (startDate != null && !startDate.isEmpty()) {
            where.append("AND planned_at >= ? "); args.add(LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            where.append("AND planned_at < ? "); args.add(LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        if (storeId != null) { where.append("AND store_id = ? "); args.add(storeId); }
        if (ownerId != null) { where.append("AND owner_id = ? "); args.add(ownerId); }

        // === 基础聚合 ===
        Map<String, Object> overview = jdbc.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS finished, " +
            "  SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END) AS closed, " +
            "  SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END) AS active, " +
            "  SUM(CASE WHEN status IN (1, 2, 7) AND sla_expire_at < NOW() THEN 1 ELSE 0 END) AS overdue, " +
            "  SUM(CASE WHEN status = 3 AND reopens > 0 THEN 1 ELSE 0 END) AS reopened " +
            where, args.toArray());

        long total = ((Number) overview.getOrDefault("total", 0)).longValue();
        long finished = ((Number) overview.getOrDefault("finished", 0)).longValue();
        long closed = ((Number) overview.getOrDefault("closed", 0)).longValue();
        long overdue = ((Number) overview.getOrDefault("overdue", 0)).longValue();
        long reopened = ((Number) overview.getOrDefault("reopened", 0)).longValue();
        long active = ((Number) overview.getOrDefault("active", 0)).longValue();

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("finished", finished);
        data.put("closed", closed);
        data.put("active", active);
        data.put("overdue", overdue);
        data.put("reopened", reopened);
        data.put("finishRate", pct(finished, total));
        data.put("closureRate", pct(finished + closed, total));
        data.put("overdueRate", pct(overdue, total));
        data.put("reopenRate", pct(reopened, finished + closed));

        // === 平均完成时长（分钟）===
        Double avgMin = jdbc.queryForObject(
            "SELECT AVG(TIMESTAMPDIFF(MINUTE, started_at, finished_at)) " +
            "FROM crm_follow_up_task " +
            "WHERE deleted=0 AND tenant_id=? AND status=3 AND started_at IS NOT NULL AND finished_at IS NOT NULL " +
            "  AND planned_at >= ? AND planned_at < ?",
            Double.class, tenantId,
            startDate == null ? LocalDate.now().withDayOfMonth(1).atStartOfDay() : LocalDate.parse(startDate).atStartOfDay(),
            endDate == null ? LocalDateTime.now() : LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        data.put("avgFinishMinutes", avgMin == null ? 0 : Math.round(avgMin));

        // === 触达漏斗（按 touch_count 区间）===
        List<Map<String, Object>> touchFunnel = jdbc.queryForList(
            "SELECT " +
            "  CASE " +
            "    WHEN IFNULL(touch_count, 0) = 0 THEN '0_未触达' " +
            "    WHEN IFNULL(touch_count, 0) = 1 THEN '1_首次' " +
            "    WHEN IFNULL(touch_count, 0) = 2 THEN '2_二次' " +
            "    WHEN IFNULL(touch_count, 0) = 3 THEN '3_三次' " +
            "    ELSE '4_三次以上' END AS bucket, " +
            "  COUNT(*) AS cnt, " +
            "  SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS finished_in_bucket " +
            where + "GROUP BY bucket ORDER BY bucket", args.toArray());
        data.put("touchFunnel", touchFunnel);

        // === 评级分布（已完成任务的 rating）===
        List<Map<String, Object>> ratingDist = jdbc.queryForList(
            "SELECT rating, COUNT(*) cnt " +
            "FROM crm_follow_up_task " +
            "WHERE deleted=0 AND tenant_id=? AND status=3 AND rating IS NOT NULL " +
            "  AND planned_at >= ? AND planned_at < ? " +
            "GROUP BY rating ORDER BY rating DESC",
            tenantId,
            startDate == null ? LocalDate.now().withDayOfMonth(1).atStartOfDay() : LocalDate.parse(startDate).atStartOfDay(),
            endDate == null ? LocalDateTime.now() : LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        data.put("ratingDist", ratingDist);

        // === 任务类型分布 ===
        List<Map<String, Object>> typeDist = jdbc.queryForList(
            "SELECT task_type, COUNT(*) cnt, " +
            "  SUM(CASE WHEN status=3 THEN 1 ELSE 0 END) finished " +
            where + " GROUP BY task_type ORDER BY cnt DESC", args.toArray());
        data.put("typeDist", typeDist);

        return Result.success(data);
    }

    /** 3.2 个人回访员绩效 */
    @GetMapping("/closure/owner-stats")
    public Result<List<Map<String, Object>>> ownerStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder where = new StringBuilder(
            "WHERE deleted=0 AND tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (startDate != null && !startDate.isEmpty()) {
            where.append("AND planned_at >= ? ");
            args.add(LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            where.append("AND planned_at < ? ");
            args.add(LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT owner_id, owner_name, " +
            "       COUNT(*) total, " +
            "       SUM(CASE WHEN status=3 THEN 1 ELSE 0 END) finished, " +
            "       SUM(CASE WHEN status IN (1,2) AND sla_expire_at<NOW() THEN 1 ELSE 0 END) overdue, " +
            "       ROUND(AVG(rating), 2) avg_rating, " +
            "       AVG(TIMESTAMPDIFF(MINUTE, started_at, finished_at)) avg_minutes " +
            "FROM crm_follow_up_task " + where + " AND owner_id IS NOT NULL " +
            "GROUP BY owner_id, owner_name ORDER BY finished DESC",
            args.toArray());
        return Result.success(rows);
    }

    /** 3.3 重开任务（closed → reopen） */
    @PostMapping("/task/{id}/reopen")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reopenTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> task = jdbc.queryForList(
            "SELECT id, status, reopens FROM crm_follow_up_task WHERE id = ? AND deleted=0", id)
            .stream().findFirst().orElse(null);
        if (task == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");
        int status = ((Number) task.get("status")).intValue();
        if (status != 3 && status != 4)
            return Result.error(BizCode.CONFLICT, "仅已完成/已关闭任务可重开");

        Long operatorId = body.get("operatorId") == null ? null
                : ((Number) body.get("operatorId")).longValue();
        String operatorName = (String) body.getOrDefault("operatorName", "admin");
        jdbc.update(
            "UPDATE crm_follow_up_task SET status = 2, started_at = NOW(), " +
            "       finished_at = NULL, result_type = NULL, result_note = NULL, " +
            "       reopens = IFNULL(reopens, 0) + 1 WHERE id = ?", id);
        writeNote(id, tenantId, "REOPEN", status, 2,
                (String) body.getOrDefault("reason", "重开任务"), operatorId, operatorName);
        return Result.success("已重开", null);
    }

    /** 3.4 个人今日待办 */
    @GetMapping("/task/my-today")
    public Result<List<Map<String, Object>>> myToday(@RequestParam Long ownerId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, task_no, task_type, member_id, member_name, member_phone, " +
            "       subject, priority, status, planned_at, sla_expire_at, touch_count, reopens " +
            "FROM crm_follow_up_task " +
            "WHERE deleted=0 AND tenant_id=? AND owner_id=? " +
            "  AND status IN (1,2) " +
            "  AND DATE(planned_at) <= CURDATE() " +
            "ORDER BY priority DESC, sla_expire_at ASC", tenantId, ownerId);
        return Result.success(rows);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private void writeNote(Long taskId, Long tenantId, String action,
                            Integer from, Integer to, String content,
                            Long operatorId, String operatorName) {
        CrmFollowUpNote n = new CrmFollowUpNote();
        n.setTenantId(tenantId);
        n.setTaskId(taskId);
        n.setActionType(action);
        n.setFromStatus(from);
        n.setToStatus(to);
        n.setContent(content == null ? "" : (content.length() > 990 ? content.substring(0, 990) : content));
        n.setOperatorId(operatorId);
        n.setOperatorName(operatorName == null ? "system" : operatorName);
        noteMapper.insert(n);
    }

    private static BigDecimal pct(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(2, RoundingMode.HALF_UP);
    }
}