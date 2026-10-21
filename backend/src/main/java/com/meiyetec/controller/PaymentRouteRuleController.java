package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.PaymentRouteRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PaymentRouteRuleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/overseas-payment/route")
@RequiredArgsConstructor
@SysOpLog(module = "PaymentRoute")
public class PaymentRouteRuleController {

    private final PaymentRouteRuleService routeRuleService;

    @GetMapping("/list")
    @RequirePermission("overseas-payment:route:list")
    public Result<List<PaymentRouteRule>> list() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(routeRuleService.lambdaQuery()
                .eq(tenantId != null, PaymentRouteRule::getTenantId, tenantId)
                .list());
    }

    @GetMapping("/match")
    @RequirePermission("overseas-payment:route:list")
    public Result<List<PaymentRouteRule>> match(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String paymentMethod) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(routeRuleService.findMatchingRules(currency, countryCode, paymentMethod, tenantId));
    }

    @PostMapping
    @RequirePermission("overseas-payment:route:add")
    public Result<Void> create(@RequestBody PaymentRouteRule rule) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        rule.setTenantId(tenantId);
        routeRuleService.save(rule);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:route:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody PaymentRouteRule rule) {
        PaymentRouteRule existing = routeRuleService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "路由规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权修改该路由");
        rule.setId(id);
        rule.setTenantId(existing.getTenantId());
        routeRuleService.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:route:delete")
    public Result<Void> delete(@PathVariable Long id) {
        PaymentRouteRule existing = routeRuleService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "路由规则不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权删除该路由");
        routeRuleService.removeById(id);
        return Result.success("删除成功", null);
    }
}