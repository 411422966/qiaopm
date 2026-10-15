package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.CrmCareOverviewService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/crm/care/ops")
@RequiredArgsConstructor
@SysOpLog(module = "CrmCareOps")
public class CrmCareOpsController {

    private final CrmCareOverviewService careOverviewService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long ownerId) {
        return Result.success(careOverviewService.overview(storeId, ownerId));
    }
}
