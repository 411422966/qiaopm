package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.SubscriptionBilling;
import com.meiyetec.entity.SubscriptionMember;
import com.meiyetec.entity.SubscriptionPlan;
import com.meiyetec.mapper.SubscriptionBillingMapper;
import com.meiyetec.mapper.SubscriptionMemberMapper;
import com.meiyetec.mapper.SubscriptionPlanMapper;
import com.meiyetec.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M09 订阅会员 Service 实现（含自动续费 Job）
 *
 * <p>本类由 {@link com.meiyetec.controller.SubscriptionController} 调用，保持纯粹的 POJO+事务边界。
 * HTTP 层注解已迁移至 Controller，本类保留 {@code @Service} + {@code @Transactional}。
 *
 * <p>自动续费 Job：每天 02:00 扫描 {@code next_bill_date <= 今天} 且 {@code autoRenew=1} 的订阅，
 * 自动写入账单、重置次数、推进下次账期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanMapper planMapper;
    private final SubscriptionMemberMapper memberMapper;
    private final SubscriptionBillingMapper billingMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 计划管理
    // ====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionPlan createPlan(SubscriptionPlan p, Long tenantId) {
        if (p.getPlanType() == null) p.setPlanType("MONTHLY");
        if (p.getBillingCycleDays() == null) p.setBillingCycleDays(30);
        if (p.getStatus() == null) p.setStatus("ACTIVE");
        p.setTenantId(tenantId);
        p.setId(null);
        p.setTotalSubscribers(0);
        p.setActiveSubscribers(0);
        planMapper.insert(p);
        return p;
    }

    @Override
    public List<SubscriptionPlan> listPlans(Long tenantId) {
        return planMapper.selectList(new LambdaQueryWrapper<SubscriptionPlan>()
                .eq(SubscriptionPlan::getTenantId, tenantId)
                .eq(SubscriptionPlan::getStatus, "ACTIVE")
                .orderByAsc(SubscriptionPlan::getPrice));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int initDefaults(Long tenantId) {
        List<Map<String, Object>> defaults = List.of(
                Map.of("code", "MONTHLY_BASIC", "name", "月度基础", "type", "MONTHLY", "price", 388, "cycle", 30, "count", 4, "discount", 8.5, "points", 200),
                Map.of("code", "MONTHLY_PRO", "name", "月度尊享", "type", "MONTHLY", "price", 988, "cycle", 30, "count", 8, "discount", 7.0, "points", 800),
                Map.of("code", "YEARLY_BASIC", "name", "年度基础", "type", "YEARLY", "price", 3880, "cycle", 365, "count", 50, "discount", 8.0, "points", 3000),
                Map.of("code", "YEARLY_DIAMOND", "name", "年度钻石", "type", "YEARLY", "price", 9880, "cycle", 365, "count", 120, "discount", 6.5, "points", 8000),
                Map.of("code", "TRIAL_7D", "name", "7天体验", "type", "WEEKLY", "price", 0, "cycle", 7, "count", 1, "trial", 7, "discount", 8.0)
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            SubscriptionPlan exist = planMapper.selectOne(new LambdaQueryWrapper<SubscriptionPlan>()
                    .eq(SubscriptionPlan::getTenantId, tenantId)
                    .eq(SubscriptionPlan::getPlanCode, d.get("code")));
            if (exist != null) continue;
            SubscriptionPlan p = new SubscriptionPlan();
            p.setTenantId(tenantId);
            p.setPlanCode((String) d.get("code"));
            p.setPlanName((String) d.get("name"));
            p.setPlanType((String) d.get("type"));
            p.setPrice(new BigDecimal(d.get("price").toString()));
            p.setBillingCycleDays((Integer) d.get("cycle"));
            p.setServiceCount((Integer) d.get("count"));
            p.setDiscountRate(new BigDecimal(d.get("discount").toString()));
            p.setGiftPoints((Integer) d.getOrDefault("points", 0));
            p.setTrialDays((Integer) d.getOrDefault("trial", 0));
            p.setStatus("ACTIVE");
            p.setTotalSubscribers(0);
            p.setActiveSubscribers(0);
            planMapper.insert(p);
            created++;
        }
        return created;
    }

    // ====================================================================
    // 订阅管理
    // ====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionMember subscribe(Long tenantId, Long planId, Long memberId,
                                        String paymentMethod) {
        SubscriptionPlan plan = planMapper.selectById(planId);
        SubscriptionMember exist = memberMapper.selectOne(new LambdaQueryWrapper<SubscriptionMember>()
                .eq(SubscriptionMember::getTenantId, tenantId)
                .eq(SubscriptionMember::getMemberId, memberId)
                .eq(SubscriptionMember::getPlanId, planId)
                .eq(SubscriptionMember::getStatus, "ACTIVE"));
        if (exist != null) return exist;

        SubscriptionMember sm = new SubscriptionMember();
        sm.setTenantId(tenantId);
        sm.setSubNo("SUB" + System.currentTimeMillis());
        sm.setPlanId(planId);
        sm.setPlanCode(plan == null ? null : plan.getPlanCode());
        sm.setMemberId(memberId);
        sm.setStartDate(LocalDate.now());
        sm.setNextBillDate(LocalDate.now().plusDays(plan == null ? 30 : plan.getBillingCycleDays()));
        sm.setEndDate(sm.getNextBillDate());
        sm.setUsedCount(0);
        sm.setRemainingCount(plan == null ? 0 : plan.getServiceCount());
        sm.setAutoRenew(1);
        sm.setStatus("ACTIVE");
        sm.setPaymentMethod(paymentMethod == null ? "WECHAT" : paymentMethod);
        sm.setTotalPaid(plan == null ? BigDecimal.ZERO : plan.getPrice());
        sm.setRenewCount(0);
        memberMapper.insert(sm);

        SubscriptionBilling bill = new SubscriptionBilling();
        bill.setTenantId(tenantId);
        bill.setSubMemberId(sm.getId());
        bill.setMemberId(memberId);
        bill.setPlanId(planId);
        bill.setBillingDate(LocalDate.now());
        bill.setAmount(plan == null ? BigDecimal.ZERO : plan.getPrice());
        bill.setPaymentMethod(sm.getPaymentMethod());
        bill.setPaymentStatus("PAID");
        bill.setBillNo("B" + System.currentTimeMillis());
        bill.setPaidAt(java.time.LocalDateTime.now());
        billingMapper.insert(bill);

        if (plan != null) {
            jdbc.update("UPDATE subscription_plan SET total_subscribers = total_subscribers + 1, " +
                    "active_subscribers = active_subscribers + 1 WHERE id = ?", planId);
        }
        return sm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long tenantId, String reason) {
        SubscriptionMember sm = memberMapper.selectById(id);
        if (sm == null || !sm.getTenantId().equals(tenantId)) return;
        sm.setStatus("CANCELLED");
        sm.setCancelledAt(java.time.LocalDateTime.now());
        sm.setCancelReason(reason == null ? "用户取消" : reason);
        memberMapper.updateById(sm);
        jdbc.update("UPDATE subscription_plan SET active_subscribers = GREATEST(0, active_subscribers - 1) WHERE id = ?", sm.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id, Long tenantId) {
        SubscriptionMember sm = memberMapper.selectById(id);
        if (sm == null || !sm.getTenantId().equals(tenantId)) return;
        sm.setStatus("PAUSED");
        memberMapper.updateById(sm);
        jdbc.update("UPDATE subscription_plan SET active_subscribers = GREATEST(0, active_subscribers - 1) WHERE id = ?", sm.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id, Long tenantId) {
        SubscriptionMember sm = memberMapper.selectById(id);
        if (sm == null || !sm.getTenantId().equals(tenantId)) return;
        sm.setStatus("ACTIVE");
        memberMapper.updateById(sm);
        jdbc.update("UPDATE subscription_plan SET active_subscribers = active_subscribers + 1 WHERE id = ?", sm.getPlanId());
    }

    @Override
    public List<SubscriptionMember> byMember(Long memberId, Long tenantId) {
        return memberMapper.selectList(new LambdaQueryWrapper<SubscriptionMember>()
                .eq(SubscriptionMember::getTenantId, tenantId)
                .eq(SubscriptionMember::getMemberId, memberId)
                .orderByDesc(SubscriptionMember::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void use(Long memberId, Long tenantId) {
        List<SubscriptionMember> subs = memberMapper.selectList(new LambdaQueryWrapper<SubscriptionMember>()
                .eq(SubscriptionMember::getTenantId, tenantId)
                .eq(SubscriptionMember::getMemberId, memberId)
                .eq(SubscriptionMember::getStatus, "ACTIVE"));
        if (subs.isEmpty()) return;
        SubscriptionMember sm = subs.get(0);
        if (sm.getRemainingCount() == null || sm.getRemainingCount() <= 0) return;
        sm.setUsedCount((sm.getUsedCount() == null ? 0 : sm.getUsedCount()) + 1);
        sm.setRemainingCount(sm.getRemainingCount() - 1);
        memberMapper.updateById(sm);
    }

    @Override
    public Map<String, Object> dashboard(Long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            Long active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_member WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Long.class, tenantId);
            Long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_member WHERE tenant_id = ?",
                    Long.class, tenantId);
            Double mrr = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(CASE WHEN sm.status = 'ACTIVE' AND p.plan_type = 'MONTHLY' THEN p.price ELSE 0 END), 0) " +
                            "FROM subscription_member sm JOIN subscription_plan p ON p.id = sm.plan_id WHERE sm.tenant_id = ?",
                    Double.class, tenantId);
            Double revenue = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM subscription_billing WHERE tenant_id = ? AND payment_status = 'PAID'",
                    Double.class, tenantId);
            data.put("activeSubscribers", active);
            data.put("totalSubscribers", total);
            data.put("mrr", mrr);
            data.put("arr", mrr == null ? 0 : mrr * 12);
            data.put("totalRevenue", revenue);
        } catch (Exception e) {
            // 表不存在时静默返回空
        }
        return data;
    }

    // ====================================================================
    // 自动续费 Job（每天 02:00）
    // ====================================================================

    /**
     * 每天 02:00 自动扫描到期订阅并续费
     * - 自动续费开关关闭 → 标 EXPIRED
     * - 自动续费开启 → 写账单 + 推进账期 + 重置次数
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoRenew() {
        log.info("[Subscription] 自动续费扫描开始");
        LocalDate today = LocalDate.now();
        List<SubscriptionMember> toBill = memberMapper.selectList(
                new LambdaQueryWrapper<SubscriptionMember>()
                        .eq(SubscriptionMember::getStatus, "ACTIVE")
                        .le(SubscriptionMember::getNextBillDate, today));
        int success = 0, fail = 0;
        for (SubscriptionMember sm : toBill) {
            if (sm.getAutoRenew() == null || sm.getAutoRenew() == 0) {
                sm.setStatus("EXPIRED");
                memberMapper.updateById(sm);
                continue;
            }
            SubscriptionPlan plan = planMapper.selectById(sm.getPlanId());
            if (plan == null) continue;
            try {
                SubscriptionBilling bill = new SubscriptionBilling();
                bill.setTenantId(sm.getTenantId());
                bill.setSubMemberId(sm.getId());
                bill.setMemberId(sm.getMemberId());
                bill.setPlanId(sm.getPlanId());
                bill.setBillingDate(today);
                bill.setAmount(plan.getPrice());
                bill.setPaymentMethod(sm.getPaymentMethod());
                bill.setPaymentStatus("PAID");
                bill.setBillNo("B" + System.currentTimeMillis() + (int) (Math.random() * 1000));
                bill.setPaidAt(java.time.LocalDateTime.now());
                billingMapper.insert(bill);

                sm.setNextBillDate(today.plusDays(plan.getBillingCycleDays()));
                sm.setEndDate(sm.getNextBillDate());
                sm.setRemainingCount(plan.getServiceCount());
                sm.setRenewCount((sm.getRenewCount() == null ? 0 : sm.getRenewCount()) + 1);
                sm.setTotalPaid((sm.getTotalPaid() == null ? BigDecimal.ZERO : sm.getTotalPaid())
                        .add(plan.getPrice()));
                memberMapper.updateById(sm);
                success++;
            } catch (Exception e) {
                log.error("[Subscription] 自动续费失败: subId={} err={}", sm.getId(), e.getMessage());
                fail++;
            }
        }
        log.info("[Subscription] 自动续费完成: 成功={}, 失败={}", success, fail);
    }
}
