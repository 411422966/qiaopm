package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DcepContractExecutionLog;
import com.meiyetec.service.DcepContractExecutionLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dcep/contract/log")
@RequiredArgsConstructor
@SysOpLog(module = "DCEP Contract Log")
public class DcepContractExecutionLogController {

    private final DcepContractExecutionLogService logService;

    @GetMapping("/list")
    public Result<List<DcepContractExecutionLog>> list(@RequestParam(required = false) Long contractId) {
        return Result.success(logService.queryList(contractId));
    }
}
