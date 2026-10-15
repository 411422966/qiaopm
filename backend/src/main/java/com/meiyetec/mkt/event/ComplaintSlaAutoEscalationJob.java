package com.meiyetec.mkt.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.ComplaintEscalation;
import com.meiyetec.entity.ComplaintSlaPolicy;
import com.meiyetec.entity.CrmComplaintTicket;
import com.meiyetec.mapper.ComplaintEscalationMapper;
import com.meiyetec.mapper.ComplaintSlaPolicyMapper;
import com.meiyetec.mapper.CrmComplaintTicketMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * M9.5 投诉 SLA 自动升级调度 Job
 *
 * <p>每 15 分钟扫描所有 OPEN / RESPONDED / IN_PROGRESS 状态的投诉工单，
 * 检查是否超过 SLA 响应/解决时限，若超期则自动创建升级记录并记录日志。
 *
 * <p>升级规则：
 * <ul>
 *   <li>超过 responseMinutes 未响应 → 升级至上一级负责人</li>
 *   <li>超过 resolveMinutes 未解决 → 升级至管理层</li>
 * </ul>
 *
 * <p>幂等：同工单 + 同升级类型（RESPONSE/RESOLVE）只创建一条未接受的升级记录。
 *
 * <p>启用开关：{@code m9.complaint.sla.auto-escalate.enabled}（默认 true）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "m9.complaint.sla.auto-escalate.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintSlaAutoEscalationJob {

    private final CrmComplaintTicketMapper ticketMapper;
    private final ComplaintSlaPolicyMapper slaPolicyMapper;
    private final ComplaintEscalationMapper escalationMapper;

    /**
     * 每 15 分钟检查一次 SLA 超时
     */
    @Scheduled(fixedRateString = "${m9.complaint.sla.check-interval-ms:900000}")
    public void checkAndEscalate() {
        long start = System.currentTimeMillis();
        log.info("[M9.5-ComplaintSLA] SLA 超时检查开始");

        // 扫描所有未关闭的工单
        List<CrmComplaintTicket> openTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<CrmComplaintTicket>()
                        .in(CrmComplaintTicket::getStatus, 0, 1, 2) // OPEN=0, RESPONDED=1, IN_PROGRESS=2
                        .orderByAsc(CrmComplaintTicket::getId)
                        .last("LIMIT 500"));

        if (openTickets.isEmpty()) {
            log.info("[M9.5-ComplaintSLA] 无待处理工单");
            return;
        }

        Set<Long> processedTenants = new HashSet<>();
        int escalatedCount = 0;

        for (CrmComplaintTicket ticket : openTickets) {
            if (ticket.getTenantId() != null) {
                processedTenants.add(ticket.getTenantId());
            }
            try {
                boolean escalated = processTicket(ticket);
                if (escalated) escalatedCount++;
            } catch (Exception e) {
                log.warn("[M9.5-ComplaintSLA] 工单 {} 检查异常: {}", ticket.getId(), e.getMessage());
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[M9.5-ComplaintSLA] 检查完成: 工单数={} 租户数={} 升级数={} cost={}ms",
                openTickets.size(), processedTenants.size(), escalatedCount, cost);
    }

    private boolean processTicket(CrmComplaintTicket ticket) {
        Long tenantId = ticket.getTenantId() != null ? ticket.getTenantId() : 1L;

        // 加载 SLA 策略
        String priority = ticket.getLevel() != null ? "P" + ticket.getLevel() : "P3";
        ComplaintSlaPolicy sla = slaPolicyMapper.selectOne(
                new LambdaQueryWrapper<ComplaintSlaPolicy>()
                        .eq(ComplaintSlaPolicy::getTenantId, tenantId)
                        .eq(ComplaintSlaPolicy::getPriority, priority));

        if (sla == null) {
            log.debug("[M9.5-ComplaintSLA] 工单 {} 无对应 SLA 策略 (priority={})", ticket.getId(), priority);
            return false;
        }

        LocalDateTime created = ticket.getCreateTime();
        if (created == null) return false;

        long elapsedMin = Duration.between(created, LocalDateTime.now()).toMinutes();
        boolean responseBreached = ticket.getStatus() == 0 && elapsedMin > sla.getResponseMinutes();
        boolean resolveBreached = elapsedMin > sla.getResolveMinutes();

        // 检查是否已存在相同类型的升级
        boolean alreadyEscalatedResponse = hasEscalation(ticket.getId(), "RESPONSE");
        boolean alreadyEscalatedResolve = hasEscalation(ticket.getId(), "RESOLVE");

        boolean escalated = false;

        // 响应超时升级
        if (responseBreached && !alreadyEscalatedResponse) {
            createEscalation(ticket, "RESPONSE", priority, "响应超时自动升级");
            escalated = true;
            log.warn("[M9.5-ComplaintSLA] 工单 {} 响应超时 (elapsed={}min > sla={}min)，已升级",
                    ticket.getId(), elapsedMin, sla.getResponseMinutes());
        }

        // 解决超时升级
        if (resolveBreached && !alreadyEscalatedResolve) {
            createEscalation(ticket, "RESOLVE", priority, "解决超时自动升级");
            escalated = true;
            log.warn("[M9.5-ComplaintSLA] 工单 {} 解决超时 (elapsed={}min > sla={}min)，已升级",
                    ticket.getId(), elapsedMin, sla.getResolveMinutes());
        }

        return escalated;
    }

    private boolean hasEscalation(Long ticketId, String type) {
        return escalationMapper.selectCount(
                new LambdaQueryWrapper<ComplaintEscalation>()
                        .eq(ComplaintEscalation::getTicketId, ticketId)
                        .like(ComplaintEscalation::getEscalateReason, type)
                        .isNull(ComplaintEscalation::getAcceptedAt)) > 0;
    }

    private void createEscalation(CrmComplaintTicket ticket, String type, String priority, String reason) {
        ComplaintEscalation e = new ComplaintEscalation();
        e.setTicketId(ticket.getId());
        e.setFromAssignee(ticket.getHandlerName());
        // 升级目标：根据 type 决定
        e.setToAssignee(type.equals("RESPONSE") ? "supervisor" : "manager");
        e.setEscalateReason("[" + type + "] " + reason + " (SLA:P" + ticket.getLevel() + ")");
        e.setEscalatedAt(LocalDateTime.now());
        escalationMapper.insert(e);
    }
}
