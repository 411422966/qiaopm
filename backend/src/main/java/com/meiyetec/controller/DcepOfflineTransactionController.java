package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DcepOfflineTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DcepOfflineTransactionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dcep/offline")
@RequiredArgsConstructor
@SysOpLog(module = "DCEP Offline")
public class DcepOfflineTransactionController {

    private final DcepOfflineTransactionService offlineService;

    @GetMapping("/list")
    @RequirePermission("dcep:offline:list")
    public Result<List<DcepOfflineTransaction>> list(
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) String merchantId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(offlineService.queryList(walletId, syncStatus, merchantId, tenantId));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("dcep:offline:list")
    public Result<DcepOfflineTransaction> getById(@PathVariable Long id) {
        DcepOfflineTransaction tx = offlineService.getById(id);
        if (tx == null) return Result.error(BizCode.NOT_FOUND, "离线交易不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && tx.getTenantId() != null && !tx.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该离线交易");
        return Result.success(tx);
    }

    @PostMapping("/create")
    @RequirePermission("dcep:offline:edit")
    public Result<DcepOfflineTransaction> create(@RequestBody Map<String, Object> params) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Long walletId = ((Number) params.get("walletId")).longValue();
        String merchantId = (String) params.get("merchantId");
        String merchantName = (String) params.get("merchantName");
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        String txType = (String) params.getOrDefault("txType", "consume");
        Boolean dualOffline = (Boolean) params.getOrDefault("dualOffline", true);
        return Result.success("交易创建成功", offlineService.createOfflineTransaction(
                walletId, merchantId, merchantName, amount, txType, dualOffline, tenantId));
    }

    @PostMapping("/sync/{offlineTxNo}")
    @RequirePermission("dcep:offline:edit")
    public Result<DcepOfflineTransaction> sync(@PathVariable String offlineTxNo) {
        DcepOfflineTransaction existing = offlineService.findByOfflineTxNo(offlineTxNo);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "离线交易不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该离线交易");
        return Result.success("同步完成", offlineService.syncTransaction(offlineTxNo));
    }

    @GetMapping("/detect-double-spend")
    @RequirePermission("dcep:offline:list")
    public Result<Boolean> detectDoubleSpend(
            @RequestParam Long walletId,
            @RequestParam String offlineCert) {
        return Result.success(offlineService.detectDoubleSpend(walletId, offlineCert));
    }
}