package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DcepSmartContract;
import com.meiyetec.service.DcepSmartContractService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dcep/contract")
@RequiredArgsConstructor
@SysOpLog(module = "DCEP Smart Contract")
public class DcepSmartContractController {

    private final DcepSmartContractService contractService;

    @GetMapping("/list")
    public Result<List<DcepSmartContract>> list(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long walletId) {
        return Result.success(contractService.queryList(contractType, status, walletId));
    }

    @GetMapping("/{id:\\d+}")
    public Result<DcepSmartContract> getById(@PathVariable Long id) {
        return Result.success(contractService.getById(id));
    }

    @PostMapping("/deploy")
    public Result<DcepSmartContract> deploy(@RequestBody Map<String, Object> params) {
        String contractName = (String) params.get("contractName");
        String contractType = (String) params.get("contractType");
        Long walletId = ((Number) params.get("walletId")).longValue();
        Long memberId = ((Number) params.get("memberId")).longValue();
        BigDecimal escrowAmount = new BigDecimal(params.get("escrowAmount").toString());
        String triggerConditions = (String) params.get("triggerConditions");
        LocalDateTime expiredTime = LocalDateTime.parse((String) params.get("expiredTime"));
        return Result.success("部署成功", contractService.deployContract(
            contractName, contractType, walletId, memberId, escrowAmount, triggerConditions, expiredTime));
    }

    @PostMapping("/{id:\\d+}/trigger")
    public Result<Void> trigger(@PathVariable Long id, @RequestParam String action) {
        contractService.triggerContract(id, action);
        return Result.success("执行成功", null);
    }

    @PostMapping("/auto-execute-expired")
    public Result<Integer> autoExecute() {
        return Result.success(contractService.autoExecuteExpired());
    }
}
