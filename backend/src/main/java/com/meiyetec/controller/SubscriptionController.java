package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.SubscriptionMember;
import com.meiyetec.entity.SubscriptionPlan;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.SubscriptionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M09 订阅会员 HTTP 层
 *
 * <p>职责：
 * <ul>
 *   <li>提取 {@code TenantContextHolder} 中的 tenantId</li>
 *   <li>HTTP 入参解析（{@code @PathVariable / @RequestBody）</li>
 *   <li>调用 {@link SubscriptionService} 处理业务</li>
 *   <li>封装 {@link Result} 返回</li>
 * </ul>
 *
 * <p>自动续费 Job 保留在 {@link SubscriptionService} 实现中（定时任务需 Spring 上下文）。
 */
@Slf4j
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    // ==================== 计划管理 ====================

    @PostMapping("/plan")
    @RequirePermission("subscription:plan:edit")
    public Result<SubscriptionPlan> createPlan(@RequestBody SubscriptionPlan p) {
        Long tenantId = tid();
        if (p.getPlanCode() == null || p.getPlanName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        subscriptionService.createPlan(p, tenantId);
        return Result.success("已创建", p);
    }

    @GetMapping("/plan/list")
    @RequirePermission("subscription:plan:list")
    public Result<List<SubscriptionPlan>> listPlans() {
        return Result.success(subscriptionService.listPlans(tid()));
    }

    @PostMapping("/plan/init-default")
    @RequirePermission("subscription:plan:edit")
    public Result<Map<String, Object>> initDefaults() {
        int created = subscriptionService.initDefaults(tid());
        return Result.success("已初始化", Map.of("created", created));
    }

    // ==================== 订阅管理 ====================

    @PostMapping("/subscribe")
    @RequirePermission("subscription:member:edit")
    public Result<SubscriptionMember> subscribe(@RequestBody Map<String, Object> body) {
        Long tenantId = tid();
        if (body.get("planId") == null || body.get("memberId") == null)
            return Result.error(BizCode.BAD_REQUEST, "planId / memberId 必填");
        Long planId = ((Number) body.get("planId")).longValue();
        Long memberId = ((Number) body.get("memberId")).longValue();
        String paymentMethod = body.get("paymentMethod") == null ? "WECHAT" : body.get("paymentMethod").toString();

        SubscriptionMember sm = subscriptionService.subscribe(tenantId, planId, memberId, paymentMethod);
        if (sm == null)
            return Result.error(BizCode.BAD_REQUEST, "已订阅该计划");
        return Result.success("已订阅", sm);
    }

    @PostMapping("/{id:\\d+}/cancel")
    @RequirePermission("subscription:member:edit")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        subscriptionService.cancel(id, tid(), reason);
        return Result.success("已取消", null);
    }

    @PostMapping("/{id:\\d+}/pause")
    @RequirePermission("subscription:member:edit")
    public Result<Void> pause(@PathVariable Long id) {
        subscriptionService.pause(id, tid());
        return Result.success("已暂停", null);
    }

    @PostMapping("/{id:\\d+}/resume")
    @RequirePermission("subscription:member:edit")
    public Result<Void> resume(@PathVariable Long id) {
        subscriptionService.resume(id, tid());
        return Result.success("已恢复", null);
    }

    @GetMapping("/by-member/{memberId:\\d+}")
    @RequirePermission("subscription:member:list")
    public Result<List<SubscriptionMember>> byMember(@PathVariable Long memberId) {
        return Result.success(subscriptionService.byMember(memberId, tid()));
    }

    @PostMapping("/use")
    @RequirePermission("subscription:member:edit")
    public Result<Void> use(@RequestBody Map<String, Object> body) {
        if (body.get("memberId") == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        Long memberId = ((Number) body.get("memberId")).longValue();
        subscriptionService.use(memberId, tid());
        return Result.success("已使用", null);
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    @RequirePermission("subscription:member:list")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(subscriptionService.dashboard(tid()));
    }
}