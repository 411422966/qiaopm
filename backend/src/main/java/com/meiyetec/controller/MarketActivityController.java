package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.MarketActivityService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M09 营销活动管理 Controller（P0-5 活动管理后台）
 *
 * <p>提供营销活动的 CRUD + 看板 + 详情全链路接口。</p>
 *
 * <p>权限码：</p>
 * <ul>
 *   <li>{@code market:activity:list}   - 活动列表</li>
 *   <li>{@code market:activity:detail} - 活动详情</li>
 *   <li>{@code market:activity:add}    - 创建活动</li>
 *   <li>{@code market:activity:edit}   - 更新活动</li>
 *   <li>{@code market:activity:delete} - 删除活动</li>
 *   <li>{@code market:activity:stat}   - 看板统计</li>
 * </ul>
 */
@RestController
@RequestMapping("/market/activity")
@RequiredArgsConstructor
@SysOpLog(module = "营销活动")
public class MarketActivityController {

    private final MarketActivityService activityService;

    @GetMapping("/page")
    @RequirePermission("market:activity:list")
    public Result activityPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.ok(activityService.activityPage(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("market:activity:detail")
    public Result getActivity(@PathVariable Long id) {
        return Result.ok(activityService.getActivity(id));
    }

    @PostMapping
    @RequirePermission("market:activity:add")
    public Result createActivity(@RequestBody Map<String, Object> params) {
        activityService.createActivity(params);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequirePermission("market:activity:edit")
    public Result updateActivity(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        activityService.updateActivity(id, params);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("market:activity:delete")
    public Result deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return Result.ok();
    }

    @GetMapping("/stats")
    @RequirePermission("market:activity:stat")
    public Result stats() {
        return Result.ok(activityService.stats());
    }

    @GetMapping("/{id}/detail")
    @RequirePermission("market:activity:detail")
    public Result detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }

    /**
     * 新增（扩展）：切换活动上下线状态
     */
    @PostMapping("/{id}/toggle")
    @RequirePermission("market:activity:edit")
    public Result toggleStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        return Result.ok(activityService.toggleStatus(id, params));
    }

    /**
     * 新增（扩展）：复制活动
     */
    @PostMapping("/{id}/copy")
    @RequirePermission("market:activity:add")
    public Result copyActivity(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        return Result.ok(activityService.copyActivity(id, params));
    }

    /**
     * 新增（扩展）：活动报名/参与记录列表
     */
    @GetMapping("/{id}/participants")
    @RequirePermission("market:activity:list")
    public Result participants(@PathVariable Long id,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(activityService.participants(id, pageNum, pageSize));
    }
}
