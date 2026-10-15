package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.PosReconciliation;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ReconciliationService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * R2.0-C T-C04 支付对账 API。
 */
@RestController
@RequestMapping("/pos/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService service;

    @PostMapping("/run")
    @RequirePermission("pos:reconciliation:run")
    public Result<Map<String, Object>> run(@RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now().minusDays(1);
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success("对账完成", service.run(date, tenantId, null));
    }

    @GetMapping("/page")
    @RequirePermission("pos:reconciliation:list")
    public Result<PageResult<PosReconciliation>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return Result.success(service.page(pageNum, pageSize, status, handleStatus, dateFrom, dateTo));
    }

    @PostMapping("/{id:\\d+}/handle")
    @RequirePermission("pos:reconciliation:handle")
    public Result<PosReconciliation> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String action = (String) body.getOrDefault("action", "HANDLED");
        String note = (String) body.get("note");
        Long handleBy = body.get("handleBy") != null ? ((Number) body.get("handleBy")).longValue() : null;
        String handleByName = (String) body.get("handleByName");
        if (handleBy == null) throw new BusinessException(BizCode.BAD_REQUEST, "处理人不能为空");
        return Result.success("已处理", service.handle(id, action, note, handleBy, handleByName));
    }
}
