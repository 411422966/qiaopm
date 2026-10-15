package com.meiyetec.mkt.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.Member;
import com.meiyetec.entity.PackageOrder;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.mapper.PackageOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M09 营销自动化触发器（基于 {@link MarketingEventPublisher}）
 *
 * <p>把"实时业务事件"以外的事件（生日 / 沉睡 / 即将到期 / 到生日）
 * 通过定时任务批量扫描 + 事件发布，让 {@code JourneyTriggerService.onMarketingEvent}
 * 仍走统一事件链路，无需在旅程触发器侧做特殊处理。
 *
 * <p>每个 Job 保持轻量：扫到一批会员 → 逐个调用 {@code publisher.xxx(...)}；
 * 真正的过滤 / 入旅程 / 幂等由 {@link com.meiyetec.service.JourneyTriggerService} 完成。
 *
 * <p>启用开关：{@code m09.automation.enabled}（默认 true）。
 *
 * <h3>任务列表</h3>
 * <ol>
 *   <li>{@link #scanBirthday()} — 每天 08:05 扫"今天生日"会员 → BIRTHDAY</li>
 *   <li>{@link #scanInactive()} — 每天 09:00 扫"沉睡 ≥ 60 天"会员 → MEMBER_INACTIVE</li>
 *   <li>{@link #scanPreExpiry()} — 每天 10:00 扫"次卡即将到期"会员 → CONSUME（提示型）</li>
 * </ol>
 *
 * <p>全部事件统一发到 {@link MarketingEventPublisher#publish}，由现有 {@code @EventListener}
 * 异步消化，保持与"实时业务事件"完全一致的链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "m09.automation.enabled", havingValue = "true", matchIfMissing = true)
public class MarketingAutomationScheduler {

    private final MemberMapper memberMapper;
    private final PackageOrderMapper packageOrderMapper;
    private final MarketingEventPublisher publisher;

    /** 沉睡判定阈值（天）：距 lastVisit 超过此值视为沉睡会员 */
    private static final long INACTIVE_DAYS = 60L;

    /** 次卡到期提醒阈值（天）：距 expireTime ≤ 此值视为即将到期 */
    private static final int PRE_EXPIRY_DAYS = 7;

    /** 每次扫描的最大会员数（防止大批量事件压垮下游） */
    private static final int BATCH_LIMIT = 500;

    // ====================================================================
    // 1. 生日事件 — 每天 08:05
    // ====================================================================

    /**
     * 每天 08:05 扫描"今天生日"的会员，发布 BIRTHDAY 事件。
     * 旅程触发器可在 daysFromBirthday=0 + 生日标签下派发生日券 / 关怀短信。
     */
    @Scheduled(cron = "${m09.automation.birthday-cron:0 5 8 * * ?}")
    public void scanBirthday() {
        long start = System.currentTimeMillis();
        try {
            LocalDate today = LocalDate.now();
            // MM-dd 一致即"今天生日"（忽略年份）
            List<Member> birthdayMembers = memberMapper.selectList(
                    new LambdaQueryWrapper<Member>()
                            .isNotNull(Member::getBirthday)
                            // 仅在 active 状态推送
                            .eq(Member::getStatus, 1)
                            // 防止软删
                            .apply("deleted = 0")
                            .last("LIMIT " + BATCH_LIMIT));
            int fired = 0;
            for (Member m : birthdayMembers) {
                if (m.getBirthday() == null) continue;
                if (m.getBirthday().getMonthValue() != today.getMonthValue()) continue;
                if (m.getBirthday().getDayOfMonth() != today.getDayOfMonth()) continue;
                try {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("daysFromBirthday", 0);
                    attr.put("birthdayTag", "today");
                    // 走便捷方法（保持事件类型/属性与生日流程一致）
                    publisher.birthday(m.getTenantId(), m.getId(), m.getName(), "today");
                    fired++;
                } catch (Exception ex) {
                    log.warn("[M09Auto] 生日事件发布失败: member={} err={}", m.getId(), ex.getMessage());
                }
            }
            long cost = System.currentTimeMillis() - start;
            log.info("[M09Auto] 生日扫描完成: scanned={} fired={} cost={}ms",
                    birthdayMembers.size(), fired, cost);
        } catch (Exception e) {
            log.error("[M09Auto] 生日扫描异常", e);
        }
    }

    // ====================================================================
    // 2. 沉睡会员 — 每天 09:00
    // ====================================================================

    /**
     * 每天 09:00 扫描"沉睡 ≥ INACTIVE_DAYS 天"的会员，发布 MEMBER_INACTIVE 事件。
     * 旅程触发器可据此派发唤醒券 / 关怀话术。
     */
    @Scheduled(cron = "${m09.automation.inactive-cron:0 0 9 * * ?}")
    public void scanInactive() {
        long start = System.currentTimeMillis();
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVE_DAYS);
            // 沉睡：lastVisit < threshold（NULL 也算沉睡，从未到店）
            List<Member> inactives = memberMapper.selectList(
                    new LambdaQueryWrapper<Member>()
                            .eq(Member::getStatus, 1)
                            .apply("deleted = 0")
                            .and(w -> w.isNull(Member::getLastVisit).or().lt(Member::getLastVisit, threshold))
                            .last("LIMIT " + BATCH_LIMIT));
            int fired = 0;
            for (Member m : inactives) {
                try {
                    long daysSince = m.getLastVisit() == null
                            ? -1L
                            : java.time.Duration.between(m.getLastVisit(), LocalDateTime.now()).toDays();
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("daysSinceLastVisit", daysSince);
                    attr.put("inactiveThresholdDays", INACTIVE_DAYS);
                    publisher.publish(MarketingEvent.builder()
                            .type(MarketingEventTypes.MEMBER_INACTIVE)
                            .tenantId(m.getTenantId())
                            .memberId(m.getId())
                            .memberName(m.getName())
                            .attributes(attr)
                            .build());
                    fired++;
                } catch (Exception ex) {
                    log.warn("[M09Auto] 沉睡事件发布失败: member={} err={}", m.getId(), ex.getMessage());
                }
            }
            long cost = System.currentTimeMillis() - start;
            log.info("[M09Auto] 沉睡扫描完成: scanned={} fired={} cost={}ms",
                    inactives.size(), fired, cost);
        } catch (Exception e) {
            log.error("[M09Auto] 沉睡扫描异常", e);
        }
    }

    // ====================================================================
    // 3. 次卡即将到期 — 每天 10:00
    // ====================================================================

    /**
     * 每天 10:00 扫描 {@code crm_package_order} 中「即将到期（≤ PRE_EXPIRY_DAYS 天）」
     * 且「正常状态 + 还有剩余次数」的次卡，逐张发布 {@link MarketingEventTypes#PACKAGE_PRE_EXPIRY} 事件。
     *
     * <p>下游旅程触发器可据此派发续卡券 / 到期提醒短信。事件属性携带：
     * <ul>
     *   <li>{@code daysToExpire}：距到期天数（0 = 当天到期）</li>
     *   <li>{@code orderId / orderNo}：次卡订单 ID + 单号（旅程文案可引用）</li>
     *   <li>{@code packageId / packageName}：套餐识别信息</li>
     *   <li>{@code remainingTimes}：剩余次数</li>
     * </ul>
     *
     * <p>幂等：依赖 {@code JourneyTriggerService} 侧的去重逻辑（memberId + type + refId）。
     */
    @Scheduled(cron = "${m09.automation.pre-expiry-cron:0 0 10 * * ?}")
    public void scanPreExpiry() {
        long start = System.currentTimeMillis();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime threshold = now.plusDays(PRE_EXPIRY_DAYS);
            // 扫：状态=1（正常）+ 剩余次数>0 + 到期时间在 (now, now+7d] 之间 + 未删除
            List<PackageOrder> expiring = packageOrderMapper.selectList(
                    new LambdaQueryWrapper<PackageOrder>()
                            .eq(PackageOrder::getStatus, 1)
                            .gt(PackageOrder::getRemainingTimes, 0)
                            .isNotNull(PackageOrder::getExpireTime)
                            .gt(PackageOrder::getExpireTime, now)
                            .le(PackageOrder::getExpireTime, threshold)
                            .orderByAsc(PackageOrder::getExpireTime)
                            .last("LIMIT " + BATCH_LIMIT));
            int fired = 0;
            for (PackageOrder po : expiring) {
                try {
                    // 取会员姓名（用于事件 attribute / 日志可读性）
                    String memberName = null;
                    try {
                        Member m = memberMapper.selectById(po.getMemberId());
                        if (m != null) memberName = m.getName();
                    } catch (Exception ignored) { /* 会员已删静默 */ }

                    long daysToExpire = po.getExpireTime() == null ? 0L
                            : java.time.Duration.between(now, po.getExpireTime()).toDays();

                    Map<String, Object> attr = new HashMap<>();
                    attr.put("daysToExpire", daysToExpire);
                    attr.put("orderId", po.getId());
                    attr.put("orderNo", po.getOrderNo());
                    attr.put("packageId", po.getPackageId());
                    attr.put("packageName", po.getPackageName());
                    attr.put("remainingTimes", po.getRemainingTimes());

                    publisher.publish(MarketingEvent.builder()
                            .type(MarketingEventTypes.PACKAGE_PRE_EXPIRY)
                            .tenantId(po.getTenantId())
                            .memberId(po.getMemberId())
                            .memberName(memberName)
                            .refNo(po.getOrderNo())
                            .attributes(attr)
                            .build());
                    fired++;
                } catch (Exception ex) {
                    log.warn("[M09Auto] 次卡到期事件发布失败: orderId={} err={}", po.getId(), ex.getMessage());
                }
            }
            long cost = System.currentTimeMillis() - start;
            log.info("[M09Auto] 次卡到期扫描完成: scanned={} fired={} window={}d cost={}ms",
                    expiring.size(), fired, PRE_EXPIRY_DAYS, cost);
        } catch (Exception e) {
            log.error("[M09Auto] 次卡到期扫描异常", e);
        }
    }
}
