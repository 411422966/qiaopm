package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.StockDualVerifyRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.StockDualVerifyService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * P2-11 IND-05 双人核销端点
 *
 * <p>POST /stock/dual-verify              — 提交复核（通过 / 驳回）
 * <p>GET  /stock/dual-verify/page        — 分页查询
 * <p>GET  /stock/dual-verify/pending    — 当前租户所有待复核
 * <p>GET  /stock/dual-verify/threshold  — 查询阈值
 * <p>GET  /stock/dual-verify/check/{id} — 按盘点ID查
 * <p>GET  /stock/dual-verify/record/{id} — 按库存记录ID查
 */
@RestController
@RequestMapping("/stock/dual-verify")
@SysOpLog(module = "双人核销")
@RequiredArgsConstructor
public class StockDualVerifyController {

    private final StockDualVerifyService dualVerifyService;

    private Long tenantId() {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return tid;
    }

    /**
     * 提交复核（通过或驳回）
     *
     * @param dualVerifyId 双人核销记录ID（必填）
     * @param action       VERIFY=通过 REJECT=驳回
     * @param reviewerId   复核人ID（从员工下拉选）
     * @param reviewerName 复核人姓名
     * @param reviewerGps 复核人GPS JSON
     * @param remark       驳回原因（REJECT 时必填）
     */
    @PostMapping
    @RequirePermission("stock:check")
    public Result<StockDualVerifyRecord> dualVerify(
            @RequestParam Long dualVerifyId,
            @RequestParam(defaultValue = "VERIFY") String action,
            @RequestParam Long reviewerId,
            @RequestParam String reviewerName,
            @RequestParam(required = false) String reviewerGps,
            @RequestParam(required = false) String remark) {

        if ("REJECT".equalsIgnoreCase(action) && (remark == null || remark.isEmpty())) {
            return Result.error(BizCode.BAD_REQUEST, "驳回时必须填写原因");
        }

        StockDualVerifyRecord result;
        if ("REJECT".equalsIgnoreCase(action)) {
            result = dualVerifyService.rejectVerify(dualVerifyId, reviewerId, reviewerName, reviewerGps, remark);
            return Result.success("已驳回，请走冲正流程", result);
        } else {
            result = dualVerifyService.completeVerify(dualVerifyId, reviewerId, reviewerName, reviewerGps);
            return Result.success("复核通过", result);
        }
    }

    /**
     * 分页查询双人核销记录
     */
    @GetMapping("/page")
    @RequirePermission("stock:check")
    public Result<Page<StockDualVerifyRecord>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String verifyType,
            @RequestParam(required = false) String verifyStatus) {
        return Result.success(dualVerifyService.page(tenantId(), pageNum, pageSize, verifyType, verifyStatus));
    }

    /**
     * 当前租户所有待复核记录（仪表盘/待办）
     */
    @GetMapping("/pending")
    @RequirePermission("stock:check")
    public Result<List<StockDualVerifyRecord>> pending() {
        return Result.success(dualVerifyService.listPending(tenantId()));
    }

    /**
     * 查询当前阈值（元）
     */
    @GetMapping("/threshold")
    @RequirePermission("stock:check")
    public Result<BigDecimal> threshold() {
        return Result.success(dualVerifyService.getThreshold(tenantId()));
    }

    /**
     * 按盘点单ID查双人核销记录
     */
    @GetMapping("/check/{checkId}")
    @RequirePermission("stock:check")
    public Result<StockDualVerifyRecord> byCheck(@PathVariable Long checkId) {
        StockDualVerifyRecord r = dualVerifyService.findByCheckId(checkId);
        return Result.success(r);
    }

    /**
     * 按库存记录ID查双人核销记录
     */
    @GetMapping("/record/{recordId}")
    @RequirePermission("stock:check")
    public Result<StockDualVerifyRecord> byRecord(@PathVariable Long recordId) {
        StockDualVerifyRecord r = dualVerifyService.findByRecordId(recordId);
        return Result.success(r);
    }
}
