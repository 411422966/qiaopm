package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DcepHardwareWallet;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DcepHardwareWalletService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dcep/wallet")
@RequiredArgsConstructor
@SysOpLog(module = "DcepHardwareWallet")
public class DcepHardwareWalletController {

    private final DcepHardwareWalletService walletService;

    @GetMapping("/list")
    public Result<List<DcepHardwareWallet>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String walletType,
            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(walletService.queryList(memberId, walletType, status, tenantId));
    }

    @GetMapping("/{id:\\d+}")
    public Result<DcepHardwareWallet> getById(@PathVariable Long id) {
        DcepHardwareWallet w = walletService.getById(id);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "钱包不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && w.getTenantId() != null && !w.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该钱包");
        return Result.success(w);
    }

    @GetMapping("/by-code/{code}")
    public Result<DcepHardwareWallet> getByCode(@PathVariable String code) {
        DcepHardwareWallet w = walletService.findByCode(code);
        if (w == null) return Result.error(BizCode.NOT_FOUND, "钱包不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && w.getTenantId() != null && !w.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该钱包");
        return Result.success(w);
    }

    @PostMapping("/activate")
    public Result<DcepHardwareWallet> activate(@RequestBody Map<String, Object> params) {
        Long memberId = ((Number) params.get("memberId")).longValue();
        String walletType = (String) params.get("walletType");
        String deviceModel = (String) params.get("deviceModel");
        String seChipId = (String) params.get("seChipId");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return Result.success("激活成功",
                walletService.activateWallet(memberId, walletType, deviceModel, seChipId, tenantId));
    }

    @PostMapping("/{id:\\d+}/recharge")
    public Result<DcepHardwareWallet> recharge(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        DcepHardwareWallet existing = walletService.getById(id);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "钱包不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && existing.getTenantId() != null && !existing.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该钱包");
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        return Result.success("充值成功", walletService.recharge(id, amount));
    }

    // ============================================================
    // DCEP-01 真机对接新增能力
    // ============================================================

    /** 挂失 */
    @PostMapping("/{id:\\d+}/report-lost")
    public Result<DcepHardwareWallet> reportLost(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String reason = (String) params.getOrDefault("reason", "用户主动挂失");
        return Result.success("挂失成功", walletService.reportLost(id, reason));
    }

    /** 解挂（需 PIN 验证） */
    @PostMapping("/{id:\\d+}/unfreeze-lost")
    public Result<DcepHardwareWallet> unfreezeLost(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String pin = (String) params.get("pin");
        return Result.success("解挂成功", walletService.unfreezeLost(id, pin));
    }

    /** 冻结 */
    @PostMapping("/{id:\\d+}/freeze")
    public Result<DcepHardwareWallet> freeze(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String reason = (String) params.getOrDefault("reason", "风控冻结");
        return Result.success("冻结成功", walletService.freeze(id, reason));
    }

    /** 解冻 */
    @PostMapping("/{id:\\d+}/unfreeze")
    public Result<DcepHardwareWallet> unfreeze(@PathVariable Long id) {
        return Result.success("解冻成功", walletService.unfreeze(id));
    }

    /** 销户 */
    @PostMapping("/{id:\\d+}/close")
    public Result<DcepHardwareWallet> closeAccount(@PathVariable Long id) {
        return Result.success("已销户", walletService.closeAccount(id));
    }

    /** 设置 PIN */
    @PostMapping("/{id:\\d+}/pin")
    public Result<Void> setPin(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String pin = (String) params.get("pin");
        walletService.setPin(id, pin);
        return Result.success("PIN 已设置", null);
    }

    /** 验证 PIN */
    @PostMapping("/{id:\\d+}/pin/verify")
    public Result<Boolean> verifyPin(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String pin = (String) params.get("pin");
        return Result.success(walletService.verifyPin(id, pin) ? "PIN 正确" : "PIN 错误",
                walletService.verifyPin(id, pin));
    }

    /** 切换生物识别 */
    @PostMapping("/{id:\\d+}/biometric")
    public Result<DcepHardwareWallet> toggleBiometric(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        boolean enabled = (Boolean) params.getOrDefault("enabled", true);
        return Result.success(enabled ? "已启用生物识别" : "已关闭生物识别",
                walletService.toggleBiometric(id, enabled));
    }

    /** 限额校验 */
    @PostMapping("/{id:\\d+}/limit-check")
    public Result<Map<String, Object>> checkLimit(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        Map<String, Object> r = walletService.checkLimit(id, amount);
        return Result.success(r);
    }

    /** 密钥轮换 */
    @PostMapping("/{id:\\d+}/keys/rotate")
    public Result<Map<String, String>> rotateKeys(@PathVariable Long id) {
        return Result.success("密钥已轮换", walletService.rotateKeys(id));
    }
}
