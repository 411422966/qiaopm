package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.OverseasPayment;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.OverseasPaymentService;
import com.meiyetec.util.Result;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/overseas-payment/payment")
@SysOpLog(module = "OverseasPayment")
public class OverseasPaymentController {

    private final OverseasPaymentService overseasPaymentService;

    public OverseasPaymentController(@Qualifier("overseasPaymentServiceImpl") OverseasPaymentService overseasPaymentService) {
        this.overseasPaymentService = overseasPaymentService;
    }

    @GetMapping("/list")
    @RequirePermission("overseas-payment:list")
    public Result<List<OverseasPayment>> list(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String gatewayCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(overseasPaymentService.queryList(orderId, memberId, gatewayCode, status, startTime, endTime, tenantId));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("overseas-payment:list")
    public Result<OverseasPayment> getById(@PathVariable Long id) {
        OverseasPayment p = overseasPaymentService.getById(id);
        if (p == null) return Result.error(BizCode.NOT_FOUND, "支付不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && p.getTenantId() != null && !p.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该支付");
        return Result.success(p);
    }

    @PostMapping("/create")
    @RequirePermission("overseas-payment:create")
    public Result<OverseasPayment> create(@RequestBody OverseasPayment payment) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        OverseasPayment result = overseasPaymentService.createPayment(
                payment.getOrderType(),
                payment.getOrderId(),
                payment.getOrderNo(),
                payment.getMemberId(),
                payment.getMemberName(),
                payment.getMemberEmail(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                tenantId);
        return Result.success("支付创建成功", result);
    }

    @PostMapping("/{id:\\d+}/refund")
    @RequirePermission("overseas-payment:refund")
    public Result<OverseasPayment> refund(@PathVariable Long id,
                                          @RequestParam BigDecimal amount,
                                          @RequestParam(required = false) String reason) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        OverseasPayment payment = overseasPaymentService.getById(id);
        if (payment == null) return Result.error(BizCode.NOT_FOUND, "支付不存在");
        if (tenantId != null && payment.getTenantId() != null && !payment.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该支付");
        return Result.success("退款成功",
                overseasPaymentService.refund(payment.getGatewayPaymentId(), amount, reason, tenantId));
    }

    @GetMapping("/statistics")
    @RequirePermission("overseas-payment:stat")
    public Result<Map<String, Object>> statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (startTime == null) startTime = LocalDateTime.now().minusDays(30);
        if (endTime == null) endTime = LocalDateTime.now();
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(overseasPaymentService.statistics(startTime, endTime, tenantId));
    }

    @GetMapping("/select-gateway")
    @RequirePermission("overseas-payment:list")
    public Result<String> selectGateway(
            @RequestParam String currency,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam BigDecimal amount) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(overseasPaymentService.selectBestGateway(currency, countryCode, paymentMethod, amount, tenantId));
    }
}