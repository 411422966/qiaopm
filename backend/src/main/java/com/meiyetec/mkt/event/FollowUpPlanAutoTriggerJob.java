package com.meiyetec.mkt.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmFollowUpPlan;
import com.meiyetec.mapper.CrmFollowUpPlanMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CrmFollowUpPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M10 回访计划自动调度 Job
 *
 * <p>每天 08:30 扫描所有已启用的回访计划（enabled=1），逐个调用
 * {@link CrmFollowUpPlanService#triggerPlan(Long)} 生成回访任务。
 *
 * <p>幂等：依赖 {@code triggerPlan} 内部对已生成 source 的去重（countByPlanAndSourceInTenant）。
 *
 * <p>启用开关：{@code m10.followup.auto-trigger.enabled}（默认 true）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "m10.followup.auto-trigger.enabled", havingValue = "true", matchIfMissing = true)
public class FollowUpPlanAutoTriggerJob {

    private final CrmFollowUpPlanMapper planMapper;
    private final CrmFollowUpPlanService followUpPlanService;

    /**
     * 每天 08:30 自动触发所有已启用回访计划
     *
     * <p>为每个 plan 正确设置 TenantContext，避免 triggerPlan 内部 tid() 飘移。
     * 先扫描所有 plan，按 tenantId 分组去重后逐租户触发。
     */
    @Scheduled(cron = "${m10.followup.auto-trigger.cron:0 30 8 * * ?}")
    public void triggerAllEnabledPlans() {
        long start = System.currentTimeMillis();
        log.info("[M10-FollowUp] 自动触发开始");

        // 扫所有启用的计划
        List<CrmFollowUpPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<CrmFollowUpPlan>()
                        .eq(CrmFollowUpPlan::getEnabled, 1)
                        .orderByAsc(CrmFollowUpPlan::getId)
                        .last("LIMIT 200"));

        if (plans.isEmpty()) {
            log.info("[M10-FollowUp] 无已启用计划，跳过");
            return;
        }

        // 按 tenantId 分组
        Set<Long> tenantIds = new HashSet<>();
        for (CrmFollowUpPlan p : plans) {
            if (p.getTenantId() != null) tenantIds.add(p.getTenantId());
        }

        int totalGenerated = 0;
        int totalErrors = 0;
        for (Long tenantId : tenantIds) {
            try {
                // 为每个租户设置上下文
                TenantContext ctx = TenantContextHolder.get();
                ctx.setTenantId(tenantId);
                ctx.setDataScope("OWN");
                TenantContextHolder.set(ctx);

                // 触发该租户下所有已启用计划
                for (CrmFollowUpPlan plan : plans) {
                    if (!tenantId.equals(plan.getTenantId())) continue;
                    int generated = triggerOne(plan);
                    totalGenerated += generated;
                }
            } catch (Exception e) {
                totalErrors++;
                log.warn("[M10-FollowUp] 租户 {} 触发异常: {}", tenantId, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[M10-FollowUp] 自动触发完成: 计划数={} 租户数={} 生成任务={} 错误={} cost={}ms",
                plans.size(), tenantIds.size(), totalGenerated, totalErrors, cost);
    }

    private int triggerOne(CrmFollowUpPlan plan) {
        try {
            var result = followUpPlanService.triggerPlan(plan.getId());
            int generated = 0;
            if (result != null && result.containsKey("generatedCount")) {
                generated = (Integer) result.get("generatedCount");
            }
            log.info("[M10-FollowUp] 计划 {} (id={}) 触发: 生成 {} 个任务",
                    plan.getPlanName(), plan.getId(), generated);
            return generated;
        } catch (Exception e) {
            log.warn("[M10-FollowUp] 计划 {} (id={}) 触发异常: {}",
                    plan.getPlanName(), plan.getId(), e.getMessage());
            return 0;
        }
    }
}
