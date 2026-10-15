package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.StockShortageLog;
import com.meiyetec.service.StockShortageLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock/shortage")
@RequiredArgsConstructor
@SysOpLog(module = "库存不足")
public class StockShortageLogController {

    private final StockShortageLogService stockShortageLogService;

    @GetMapping("/list")
    public Result<List<StockShortageLog>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId) {
        return Result.success(stockShortageLogService.queryList(status, storeId));
    }
}
