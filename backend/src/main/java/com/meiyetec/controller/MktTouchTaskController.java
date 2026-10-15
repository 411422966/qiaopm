package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MarketingTouchTask;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketingAutomationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 营销触达任务 controller（前端 /mkt/task/* 实际对应的端点）
 *  - touch-task.vue 实际使用的后端
 *  - 对应 marketing_touch_task + marketing_touch_log 表
 *
 * 路由清单（与前端 touch-task.vue 完全对齐）：
 *  POST /mkt/task/page           （分页查询 — body 参数）
 *  GET  /mkt/task/page           （也支持 query 形式）
 *  POST /mkt/task                （创建任务）
 *  POST /mkt/task/{id}/start     （启动）
 *  POST /mkt/task/{id}/pause     （暂停）
 *  POST /mkt/task/{id}/resume    （恢复）
 *  POST /mkt/task/{id}/cancel    （取消）
 *  GET  /mkt/task/{id}/log       （明细分页）
 */
@RestController
@RequestMapping("/mkt/task")
@RequiredArgsConstructor
@SysOpLog(module = "营销触达任务")
public class MktTouchTaskController {

    private final MarketingAutomationService marketingAutomationService;

    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> pagePost(@RequestBody Map<String, Object> params) {
        int pageNum = (int) params.getOrDefault("pageNum", 1);
        int pageSize = (int) params.getOrDefault("pageSize", 20);
        Integer status = params.get("status") == null ? null : ((Number) params.get("status")).intValue();
        String channelType = (String) params.get("channelType");
        String keyword = (String) params.get("keyword");
        return marketingAutomationService.taskPage(pageNum, pageSize, status, channelType, keyword);
    }

    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> pageGet(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String keyword) {
        return marketingAutomationService.taskPage(pageNum, pageSize, status, channelType, keyword);
    }

    @PostMapping
    public Result<MarketingTouchTask> create(@RequestBody Map<String, Object> body) {
        return marketingAutomationService.createTask(body);
    }

    @PostMapping("/{id}/start")
    public Result<Map<String, Object>> start(@PathVariable Long id) {
        return marketingAutomationService.startTask(id);
    }

    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id) {
        return marketingAutomationService.pauseTask(id);
    }

    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        return marketingAutomationService.resumeTask(id);
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        return marketingAutomationService.cancelTask(id);
    }

    @GetMapping("/{id}/log")
    public Result<Page<Map<String, Object>>> log(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {
        return marketingAutomationService.taskLogPage(id, pageNum, pageSize, status);
    }
}
