package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CommissionCondition;
import com.meiyetec.service.CommissionConditionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commission/condition")
@RequiredArgsConstructor
@SysOpLog(module = "CommissionCondition")
public class CommissionConditionController {

    private final CommissionConditionService commissionConditionService;

    @GetMapping("/by-rule/{ruleId}")
    public Result<List<CommissionCondition>> getByRuleId(@PathVariable Long ruleId) {
        return Result.success(commissionConditionService.getConditionsByRuleId(ruleId));
    }

    @PostMapping("/batch/{ruleId}")
    public Result<Void> batchSave(@PathVariable Long ruleId, @RequestBody List<CommissionCondition> conditions) {
        commissionConditionService.batchSaveConditions(ruleId, conditions);
        return Result.success("保存成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        commissionConditionService.removeById(id);
        return Result.success("删除成功", null);
    }
}
