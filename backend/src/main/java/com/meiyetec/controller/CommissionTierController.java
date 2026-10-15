package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CommissionTier;
import com.meiyetec.service.CommissionTierService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commission/tier")
@RequiredArgsConstructor
@SysOpLog(module = "CommissionTier")
public class CommissionTierController {

    private final CommissionTierService commissionTierService;

    @GetMapping("/by-rule/{ruleId}")
    public Result<List<CommissionTier>> getByRuleId(@PathVariable Long ruleId) {
        return Result.success(commissionTierService.getTiersByRuleId(ruleId));
    }

    @PostMapping("/batch/{ruleId}")
    public Result<Void> batchSave(@PathVariable Long ruleId, @RequestBody List<CommissionTier> tiers) {
        commissionTierService.batchSaveTiers(ruleId, tiers);
        return Result.success("保存成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        commissionTierService.removeById(id);
        return Result.success("删除成功", null);
    }
}
