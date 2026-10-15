package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.ServiceMaterialDeductService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/stock/service-material/deduct")
@RequiredArgsConstructor
@SysOpLog(module = "Auto Deduct")
public class ServiceMaterialDeductController {

    private final ServiceMaterialDeductService serviceMaterialDeductService;

    @PostMapping("/execute")
    public Result<ServiceMaterialDeductService.DeductResult> execute(
            @RequestBody ServiceMaterialDeductService.DeductContext context) {
        ServiceMaterialDeductService.DeductResult result = serviceMaterialDeductService.deductForService(context);
        return Result.success(result.success ? "扣减成功" : "扣减部分失败", result);
    }

    @PostMapping("/rollback")
    public Result<Void> rollback(@RequestBody Map<String, Object> params) {
        Long orderId = ((Number) params.get("orderId")).longValue();
        String reason = (String) params.get("reason");
        boolean ok = serviceMaterialDeductService.rollbackByOrderId(orderId, reason);
        return ok ? Result.success("回滚成功", null) : Result.error("回滚失败");
    }
}
