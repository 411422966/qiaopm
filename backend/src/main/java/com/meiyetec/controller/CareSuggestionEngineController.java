package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.entity.MemberSkinProfile;
import com.meiyetec.service.CareSuggestionEngineService;
import com.meiyetec.service.MemberSkinProfileService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/care/suggestion")
@RequiredArgsConstructor
@SysOpLog(module = "Care Suggestion Engine")
public class CareSuggestionEngineController {

    private final CareSuggestionEngineService careSuggestionEngineService;
    private final MemberSkinProfileService memberSkinProfileService;

    @PostMapping("/generate/{memberId}")
    @RequirePermission("crm:care:suggestion:engine:edit")
    public Result<List<CareSuggestionLog>> generate(@PathVariable Long memberId,
                                                    @RequestBody(required = false) Map<String, Object> params) {
        Long operatorId = params == null || params.get("operatorId") == null
                ? null : Long.valueOf(params.get("operatorId").toString());
        String operatorName = params == null ? null : (String) params.get("operatorName");
        String triggerType = (params != null && params.get("triggerType") != null)
                ? (String) params.get("triggerType") : "manual";
        List<CareSuggestionLog> logs = careSuggestionEngineService.generateForMember(memberId, operatorId, operatorName, triggerType);
        return Result.success("已生成 " + logs.size() + " 条建议", logs);
    }

    @PostMapping("/generate-batch")
    @RequirePermission("crm:care:suggestion:engine:edit")
    public Result<Map<String, Object>> generateBatch(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Number> memberIds = (List<Number>) params.get("memberIds");
        Long operatorId = params.get("operatorId") == null ? null : Long.valueOf(params.get("operatorId").toString());
        String operatorName = (String) params.get("operatorName");
        List<Long> ids = new ArrayList<>();
        if (memberIds != null) {
            for (Number n : memberIds) ids.add(n.longValue());
        }
        int count = careSuggestionEngineService.generateForMembers(ids, operatorId, operatorName);
        Map<String, Object> data = new HashMap<>();
        data.put("processedCount", ids.size());
        data.put("generatedCount", count);
        return Result.success("批量生成完成", data);
    }

    @GetMapping("/skin-profile/{memberId}")
    @RequirePermission("crm:care:suggestion:engine:view")
    public Result<MemberSkinProfile> getSkinProfile(@PathVariable Long memberId) {
        return Result.success(memberSkinProfileService.getLatestByMember(memberId));
    }

    @PostMapping("/skin-profile")
    @RequirePermission("crm:care:suggestion:engine:edit")
    public Result<Void> saveSkinProfile(@RequestBody MemberSkinProfile profile) {
        memberSkinProfileService.saveOrUpdate(profile);
        return Result.success("保存成功", null);
    }
}