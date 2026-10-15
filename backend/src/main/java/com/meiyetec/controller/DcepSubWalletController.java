package com.meiyetec.controller;

import com.meiyetec.entity.DcepSubWallet;
import com.meiyetec.entity.DcepSubWalletLedger;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DcepSubWalletService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dcep/sub-wallet")
@RequiredArgsConstructor
public class DcepSubWalletController {

    private final DcepSubWalletService subWalletService;

    // ============================================================
    // 查询
    // ============================================================

    @GetMapping("/list")
    public Result<List<DcepSubWallet>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long parentWalletId,
            @RequestParam(required = false) String walletType,
            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(subWalletService.queryList(tenantId, storeId, parentWalletId, walletType, status));
    }

    @GetMapping("/{id:\\d+}")
    public Result<DcepSubWallet> getById(@PathVariable Long id) {
        DcepSubWallet w = subWalletService.getById(id);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "子钱包不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && w.getTenantId() != null && !w.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该子钱包");
        return Result.success(w);
    }

    @GetMapping("/by-code/{code}")
    public Result<DcepSubWallet> getByCode(@PathVariable String code) {
        DcepSubWallet w = subWalletService.findByCode(code);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "子钱包不存在");
        return Result.success(w);
    }

    @GetMapping("/{id:\\d+}/stats")
    public Result<Map<String, Object>> stats(@PathVariable Long id) {
        return Result.success(subWalletService.getStats(id));
    }

    // ============================================================
    // 管理
    // ============================================================

    @PostMapping("/create")
    public Result<DcepSubWallet> create(@RequestBody Map<String, Object> params) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Long storeId = params.get("storeId") == null ? null
                : ((Number) params.get("storeId")).longValue();
        Long parentWalletId = ((Number) params.get("parentWalletId")).longValue();
        String parentWalletType = (String) params.getOrDefault("parentWalletType", "hardware");
        String subWalletName = (String) params.get("subWalletName");
        String walletType = (String) params.get("walletType");
        BigDecimal maxBalance = params.get("maxBalance") == null ? null
                : new BigDecimal(params.get("maxBalance").toString());
        BigDecimal minBalance = params.get("minBalance") == null ? null
                : new BigDecimal(params.get("minBalance").toString());
        BigDecimal transferInRatio = params.get("transferInRatio") == null ? null
                : new BigDecimal(params.get("transferInRatio").toString());
        BigDecimal transferOutRatio = params.get("transferOutRatio") == null ? null
                : new BigDecimal(params.get("transferOutRatio").toString());
        Integer autoSweep = (Integer) params.getOrDefault("autoSweepEnabled", 0);
        BigDecimal sweepThreshold = params.get("sweepThreshold") == null ? null
                : new BigDecimal(params.get("sweepThreshold").toString());
        Long sweepTargetId = params.get("sweepTargetId") == null ? null
                : ((Number) params.get("sweepTargetId")).longValue();
        String settlementMode = (String) params.get("settlementMode");
        String tags = (String) params.get("tags");
        return Result.success("子钱包创建成功",
                subWalletService.createSubWallet(tenantId, storeId, parentWalletId,
                        parentWalletType, subWalletName, walletType, maxBalance, minBalance,
                        transferInRatio, transferOutRatio, autoSweep, sweepThreshold,
                        sweepTargetId, settlementMode, tags));
    }

    @PutMapping("/{id:\\d+}/config")
    public Result<DcepSubWallet> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        BigDecimal maxBalance = params.get("maxBalance") == null ? null
                : new BigDecimal(params.get("maxBalance").toString());
        BigDecimal minBalance = params.get("minBalance") == null ? null
                : new BigDecimal(params.get("minBalance").toString());
        BigDecimal transferInRatio = params.get("transferInRatio") == null ? null
                : new BigDecimal(params.get("transferInRatio").toString());
        BigDecimal transferOutRatio = params.get("transferOutRatio") == null ? null
                : new BigDecimal(params.get("transferOutRatio").toString());
        Integer autoSweep = (Integer) params.get("autoSweepEnabled");
        BigDecimal sweepThreshold = params.get("sweepThreshold") == null ? null
                : new BigDecimal(params.get("sweepThreshold").toString());
        Long sweepTargetId = params.get("sweepTargetId") == null ? null
                : ((Number) params.get("sweepTargetId")).longValue();
        String settlementMode = (String) params.get("settlementMode");
        String tags = (String) params.get("tags");
        String remark = (String) params.get("remark");
        return Result.success("配置已更新",
                subWalletService.updateConfig(id, maxBalance, minBalance,
                        transferInRatio, transferOutRatio, autoSweep, sweepThreshold,
                        sweepTargetId, settlementMode, tags, remark));
    }

    @PostMapping("/{id:\\d+}/freeze")
    public Result<DcepSubWallet> freeze(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String reason = (String) params.getOrDefault("reason", "运营冻结");
        return Result.success("已冻结", subWalletService.freeze(id, reason));
    }

    @PostMapping("/{id:\\d+}/unfreeze")
    public Result<DcepSubWallet> unfreeze(@PathVariable Long id) {
        return Result.success("已解冻", subWalletService.unfreeze(id));
    }

    @PostMapping("/{id:\\d+}/close")
    public Result<DcepSubWallet> close(@PathVariable Long id) {
        return Result.success("子钱包已关闭", subWalletService.close(id));
    }

    // ============================================================
    // 账间转账
    // ============================================================

    @PostMapping("/transfer")
    public Result<Map<String, DcepSubWalletLedger>> transfer(@RequestBody Map<String, Object> params) {
        Long fromId = ((Number) params.get("fromId")).longValue();
        Long toId = ((Number) params.get("toId")).longValue();
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        BigDecimal fee = params.get("fee") == null ? null
                : new BigDecimal(params.get("fee").toString());
        String orderNo = (String) params.get("orderNo");
        Long operatorId = params.get("operatorId") == null ? null
                : ((Number) params.get("operatorId")).longValue();
        String operatorName = (String) params.get("operatorName");
        String remark = (String) params.get("remark");
        return Result.success("转账完成",
                subWalletService.transfer(fromId, toId, amount, fee, orderNo, operatorId, operatorName, remark));
    }

    // ============================================================
    // 账本流水
    // ============================================================

    @GetMapping("/{id:\\d+}/ledger")
    public Result<List<DcepSubWalletLedger>> ledger(
            @PathVariable Long id,
            @RequestParam(required = false) String txType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(subWalletService.queryLedger(id, txType, startTime, endTime, limit));
    }

    // ============================================================
    // 自动归集（定时任务）
    // ============================================================

    @PostMapping("/sweep/{parentWalletId:\\d+}")
    public Result<Integer> autoSweep(@PathVariable Long parentWalletId) {
        int count = subWalletService.autoSweep(parentWalletId);
        return Result.success("归集完成，共 " + count + " 笔", count);
    }
}
