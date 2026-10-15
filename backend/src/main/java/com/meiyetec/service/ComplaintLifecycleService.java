package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.10：投诉工单完整生命周期
 *
 * <p>提供：
 * <ul>
 *   <li>投诉分类（8 预置）</li>
 *   <li>SLA 策略（按优先级）</li>
 *   <li>工单完整生命周期（OPEN → RESPONDED → IN_PROGRESS → RESOLVED → CLOSED）</li>
 *   <li>升级路径</li>
 *   <li>满意度评价（NPS）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/complaint")
@RequiredArgsConstructor
public class ComplaintLifecycleService {

    private final ComplaintCategoryMapper categoryMapper;
    private final ComplaintSlaPolicyMapper slaPolicyMapper;
    private final ComplaintEscalationMapper escalationMapper;
    private final ComplaintSatisfactionMapper satisfactionMapper;
    // 复用已有的 CrmComplaintTicket（已有实体）
    private final com.meiyetec.mapper.CrmComplaintTicketMapper ticketMapper;

    // ====================================================================
    // 1. 投诉分类
    // ====================================================================

    @PostMapping("/category")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintCategory> upsertCategory(@RequestBody ComplaintCategory c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        c.setTenantId(tenantId);
        if (c.getEnabled() == null) c.setEnabled(1);
        if (c.getSortOrder() == null) c.setSortOrder(100);

        ComplaintCategory exist = categoryMapper.selectOne(
                new LambdaQueryWrapper<ComplaintCategory>()
                        .eq(ComplaintCategory::getTenantId, tenantId)
                        .eq(ComplaintCategory::getCategoryCode, c.getCategoryCode()));
        if (exist != null) {
            categoryMapper.updateById(c);
            return Result.success("已更新", c);
        }
        categoryMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/category/list")
    public Result<List<ComplaintCategory>> listCategories() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(categoryMapper.selectList(
                new LambdaQueryWrapper<ComplaintCategory>()
                        .eq(ComplaintCategory::getTenantId, tenantId)
                        .eq(ComplaintCategory::getEnabled, 1)
                        .orderByAsc(ComplaintCategory::getSortOrder)));
    }

    // ====================================================================
    // 2. SLA 策略
    // ====================================================================

    @PostMapping("/sla")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintSlaPolicy> upsertSla(@RequestBody ComplaintSlaPolicy p) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        p.setTenantId(tenantId);
        ComplaintSlaPolicy exist = slaPolicyMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSlaPolicy>()
                        .eq(ComplaintSlaPolicy::getTenantId, tenantId)
                        .eq(ComplaintSlaPolicy::getPriority, p.getPriority()));
        if (exist != null) {
            slaPolicyMapper.updateById(p);
            return Result.success("已更新", p);
        }
        slaPolicyMapper.insert(p);
        return Result.success("已创建", p);
    }

    @GetMapping("/sla/list")
    public Result<List<ComplaintSlaPolicy>> listSla() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(slaPolicyMapper.selectList(
                new LambdaQueryWrapper<ComplaintSlaPolicy>()
                        .eq(ComplaintSlaPolicy::getTenantId, tenantId)
                        .orderByAsc(ComplaintSlaPolicy::getPriority)));
    }

    @GetMapping("/sla/{priority}")
    public Result<ComplaintSlaPolicy> getSla(@PathVariable String priority) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(slaPolicyMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSlaPolicy>()
                        .eq(ComplaintSlaPolicy::getTenantId, tenantId)
                        .eq(ComplaintSlaPolicy::getPriority, priority)));
    }

    // ====================================================================
    // 3. 升级
    // ====================================================================

    @PostMapping("/escalate")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintEscalation> escalate(@RequestBody ComplaintEscalation e) {
        if (e.getTicketId() == null || e.getToAssignee() == null)
            return Result.error(BizCode.BAD_REQUEST, "ticketId / toAssignee 必填");
        if (e.getEscalatedAt() == null) e.setEscalatedAt(LocalDateTime.now());
        escalationMapper.insert(e);
        return Result.success("已升级", e);
    }

    @PostMapping("/escalate/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintEscalation> acceptEscalation(@PathVariable Long id) {
        ComplaintEscalation e = escalationMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "升级不存在");
        e.setAcceptedAt(LocalDateTime.now());
        escalationMapper.updateById(e);
        return Result.success("已接受", e);
    }

    @GetMapping("/escalate/list")
    public Result<List<ComplaintEscalation>> listEscalations(@RequestParam Long ticketId) {
        return Result.success(escalationMapper.selectList(
                new LambdaQueryWrapper<ComplaintEscalation>()
                        .eq(ComplaintEscalation::getTicketId, ticketId)
                        .orderByAsc(ComplaintEscalation::getEscalatedAt)));
    }

    // ====================================================================
    // 4. 满意度
    // ====================================================================

    @PostMapping("/satisfaction")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintSatisfaction> rate(@RequestBody ComplaintSatisfaction s) {
        if (s.getTicketId() == null) return Result.error(BizCode.BAD_REQUEST, "ticketId 必填");
        if (s.getRating() == null) return Result.error(BizCode.BAD_REQUEST, "rating 必填");
        if (s.getNpsScore() == null) s.setNpsScore(calcNps(s.getRating()));
        if (s.getRatedAt() == null) s.setRatedAt(LocalDateTime.now());

        ComplaintSatisfaction exist = satisfactionMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSatisfaction>()
                        .eq(ComplaintSatisfaction::getTicketId, s.getTicketId()));
        if (exist != null) {
            satisfactionMapper.updateById(s);
            return Result.success("已更新", s);
        }
        satisfactionMapper.insert(s);
        return Result.success("已评价", s);
    }

    /**
     * NPS 计算：promoter(9-10) - detractor(0-6)
     */
    public static int calcNps(int rating) {
        int promoters = rating >= 9 ? 1 : 0;
        int detractors = rating <= 6 ? 1 : 0;
        return (promoters - detractors) * 100;
    }

    @GetMapping("/satisfaction/{ticketId}")
    public Result<ComplaintSatisfaction> getSatisfaction(@PathVariable Long ticketId) {
        return Result.success(satisfactionMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSatisfaction>()
                        .eq(ComplaintSatisfaction::getTicketId, ticketId)));
    }

    /**
     * M10: 满意度评价（REST 风格 path param）
     * POST /complaint/{id}/satisfaction
     */
    @PostMapping("/{id}/satisfaction")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplaintSatisfaction> rateById(
            @PathVariable Long id,
            @RequestBody ComplaintSatisfaction body) {
        if (body.getRating() == null) return Result.error(BizCode.BAD_REQUEST, "rating 必填");
        if (body.getRatedAt() == null) body.setRatedAt(LocalDateTime.now());
        if (body.getNpsScore() == null) body.setNpsScore(calcNps(body.getRating()));
        body.setTicketId(id);

        ComplaintSatisfaction exist = satisfactionMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSatisfaction>()
                        .eq(ComplaintSatisfaction::getTicketId, id));
        if (exist != null) {
            body.setId(exist.getId());
            satisfactionMapper.updateById(body);
            return Result.success("已更新", body);
        }
        satisfactionMapper.insert(body);
        return Result.success("已评价", body);
    }

    // ====================================================================
    // 5. 工单 SLA 合规检查
    // ====================================================================

    @GetMapping("/sla/check/{ticketId}")
    public Result<Map<String, Object>> checkSla(@PathVariable Long ticketId) {
        // 复用已有 CrmComplaintTicket（level = priority, status = Integer）
        var ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) return Result.error(BizCode.NOT_FOUND, "工单不存在");

        // level 1=P1, 2=P2, 3=P3, 4=P4 → String
        String priority = ticket.getLevel() != null ? "P" + ticket.getLevel() : "P3";
        Long tenantId = TenantContextHolder.get().getTenantId();
        ComplaintSlaPolicy sla = slaPolicyMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSlaPolicy>()
                        .eq(ComplaintSlaPolicy::getTenantId, tenantId)
                        .eq(ComplaintSlaPolicy::getPriority, priority));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticketId", ticketId);
        data.put("priority", priority);
        data.put("status", ticket.getStatus());
        data.put("createdAt", ticket.getCreateTime());

        if (sla != null) {
            LocalDateTime created = ticket.getCreateTime();
            long elapsedMin = created != null
                    ? Duration.between(created, LocalDateTime.now()).toMinutes() : 0;
            data.put("responseDeadlineMin", sla.getResponseMinutes());
            data.put("resolveDeadlineMin", sla.getResolveMinutes());
            data.put("elapsedMin", elapsedMin);
            data.put("responseBreached", elapsedMin > sla.getResponseMinutes());
            data.put("resolveBreached", elapsedMin > sla.getResolveMinutes());
            data.put("slaPolicy", sla);
        }
        return Result.success(data);
    }

    // ====================================================================
    // 6. 投诉统计
    // ====================================================================

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<ComplaintSatisfaction> satList = satisfactionMapper.selectList(null);

        double avgRating = satList.stream()
                .filter(s -> s.getRating() != null)
                .mapToInt(ComplaintSatisfaction::getRating)
                .average().orElse(0);
        double avgNps = satList.stream()
                .filter(s -> s.getNpsScore() != null)
                .mapToInt(ComplaintSatisfaction::getNpsScore)
                .average().orElse(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalComplaints", satList.size());
        data.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
        data.put("avgNps", Math.round(avgNps * 10.0) / 10.0);
        data.put("totalSlaPolicies", slaPolicyMapper.selectCount(null));
        data.put("totalCategories", categoryMapper.selectCount(null));
        return Result.success(data);
    }
}