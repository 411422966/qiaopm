package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.HuabeiOrder;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.HuabeiService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/huabei")
@RequiredArgsConstructor
@SysOpLog(module = "Huabei Installment")
public class HuabeiController {

    private final HuabeiService huabeiService;

    @GetMapping("/credit")
    @RequirePermission("huabei:credit")
    public Result<Map<String, Object>> queryCredit(@RequestParam Long memberId,
                                                   @RequestParam String phone) {
        return huabeiService.queryCreditScore(memberId, phone, TenantContextHolder.get().getTenantId());
    }

    @PostMapping("/order")
    @RequirePermission("huabei:create")
    public Result<HuabeiOrder> createOrder(@RequestParam Long memberId,
                                           @RequestParam String bizOrderNo,
                                           @RequestParam BigDecimal amount,
                                           @RequestParam(defaultValue = "3") Integer period) {
        return huabeiService.createInstallmentOrder(memberId, bizOrderNo, amount, period,
                TenantContextHolder.get().getTenantId());
    }

    @GetMapping("/orders")
    @RequirePermission("huabei:list")
    public Result<?> listOrders(@RequestParam(required = false) Long memberId,
                                @RequestParam(required = false) Integer status,
                                @RequestParam(defaultValue = "1") long current,
                                @RequestParam(defaultValue = "20") long size) {
        return huabeiService.listOrders(memberId, status, current, size, TenantContextHolder.get().getTenantId());
    }

    @PostMapping("/order/{id}/cancel")
    @RequirePermission("huabei:cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        return huabeiService.cancel(id, TenantContextHolder.get().getTenantId());
    }

    @PostMapping("/notify/simulate")
    public Result<Void> simulateNotify(@RequestParam Long orderId,
                                       @RequestParam(defaultValue = "paid") String status) {
        return huabeiService.simulateNotify(orderId, status, TenantContextHolder.get().getTenantId());
    }
}