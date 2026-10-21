package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.exception.BizCode;
import com.meiyetec.payment.OverseasPaymentAdapter;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.OverseasPaymentGatewayService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/overseas-payment/gateway")
@RequiredArgsConstructor
@SysOpLog(module = "OverseasPayment")
public class OverseasPaymentGatewayController {

    private final OverseasPaymentGatewayService gatewayService;

    @GetMapping("/list")
    @RequirePermission("overseas-payment:gateway:list")
    public Result<List<OverseasPaymentGateway>> list(
            @RequestParam(required = false) String gatewayCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String environment) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(gatewayService.queryList(gatewayCode, status, environment, tenantId));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:gateway:list")
    public Result<OverseasPaymentGateway> getById(@PathVariable Long id) {
        OverseasPaymentGateway g = gatewayService.getById(id);
        if (g == null) return Result.error(BizCode.NOT_FOUND, "网关不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && g.getTenantId() != null && !g.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该网关");
        return Result.success(g);
    }

    @PostMapping
    @RequirePermission("overseas-payment:gateway:add")
    public Result<Void> create(@RequestBody OverseasPaymentGateway gateway) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        gateway.setTenantId(tenantId);
        gatewayService.save(gateway);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:gateway:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody OverseasPaymentGateway gateway) {
        OverseasPaymentGateway existing = gatewayService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "网关不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权修改该网关");
        gateway.setId(id);
        gateway.setTenantId(existing.getTenantId());
        gatewayService.updateById(gateway);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:gateway:delete")
    public Result<Void> delete(@PathVariable Long id) {
        OverseasPaymentGateway existing = gatewayService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "网关不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权删除该网关");
        gatewayService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id:\\d+}/health-check")
    @RequirePermission("overseas-payment:gateway:edit")
    public Result<OverseasPaymentAdapter.HealthStatus> healthCheck(@PathVariable Long id) {
        OverseasPaymentGateway existing = gatewayService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "网关不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该网关");
        return Result.success(gatewayService.healthCheck(id));
    }
}