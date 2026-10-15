package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktJourney;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketingAutomationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 营销旅程 controller（前端 /mkt/journey/* 实际对应的端点）
 *  - journey.vue 实际使用的后端
 *  - 对应 mkt_journey + mkt_journey_instance 表
 *
 * 路由清单（与前端 journey.vue 完全对齐）：
 *  POST /mkt/journey             （创建 — body）
 *  POST /mkt/journey/page        （分页查询 — body 参数）
 *  GET  /mkt/journey/page        （分页查询 — query 参数）
 *  GET  /mkt/journey/{id}        （详情）
 *  PUT  /mkt/journey/{id}        （更新）
 *  POST /mkt/journey/{id}/publish（发布）
 *  POST /mkt/journey/{id}/pause  （暂停）
 *  POST /mkt/journey/{id}/enter  （会员进入 — body）
 *  POST /mkt/journey/instance/{instanceId}/next （推进下一节点）
 */
@RestController
@RequestMapping("/mkt/journey")
@RequiredArgsConstructor
@SysOpLog(module = "营销旅程")
public class MktJourneyController {

    private final MarketingAutomationService marketingAutomationService;

    @PostMapping
    public Result<MktJourney> create(@RequestBody Map<String, Object> body) {
        return marketingAutomationService.createJourney(body);
    }

    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> pagePost(@RequestBody Map<String, Object> params) {
        int pageNum = (int) params.getOrDefault("pageNum", 1);
        int pageSize = (int) params.getOrDefault("pageSize", 20);
        Integer status = params.get("status") == null ? null : ((Number) params.get("status")).intValue();
        String journeyType = (String) params.get("journeyType");
        String keyword = (String) params.get("keyword");
        return marketingAutomationService.journeyPage(pageNum, pageSize, status, journeyType, keyword);
    }

    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> pageGet(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String journeyType,
            @RequestParam(required = false) String keyword) {
        return marketingAutomationService.journeyPage(pageNum, pageSize, status, journeyType, keyword);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return marketingAutomationService.journeyDetail(id);
    }

    @PutMapping("/{id}")
    public Result<MktJourney> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingAutomationService.updateJourney(id, body);
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        return marketingAutomationService.publishJourney(id);
    }

    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        return marketingAutomationService.pauseJourney(id);
    }

    @PostMapping("/{id}/enter")
    public Result<Map<String, Object>> enter(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingAutomationService.enterJourney(id, body);
    }

    @PostMapping("/instance/{instanceId}/next")
    public Result<Map<String, Object>> nextNode(@PathVariable Long instanceId) {
        return marketingAutomationService.nextNode(instanceId);
    }
}
