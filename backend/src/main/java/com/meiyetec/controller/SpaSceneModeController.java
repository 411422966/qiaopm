package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.dto.SpaSceneRecommendDTO;
import com.meiyetec.entity.SpaSceneMode;
import com.meiyetec.entity.SpaSceneRoomSlot;
import com.meiyetec.service.SpaSceneModeService;
import com.meiyetec.util.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/spa-scene")
@SysOpLog(module = "SPA Scene Mode")
public class SpaSceneModeController {

    private final SpaSceneModeService sceneService;

    public SpaSceneModeController(SpaSceneModeService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping("/list")
    @RequirePermission("spa:scene:list")
    public Result<List<SpaSceneMode>> list(@RequestParam(required = false) Integer status) {
        return Result.success(sceneService.listAll(status));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("spa:scene:list")
    public Result<SpaSceneMode> get(@PathVariable Long id) {
        return Result.success(sceneService.getById(id));
    }

    @PostMapping
    @RequirePermission("spa:scene:edit")
    public Result<SpaSceneMode> create(@RequestBody SpaSceneMode entity) {
        return Result.success("创建成功", sceneService.save(entity));
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("spa:scene:edit")
    public Result<SpaSceneMode> update(@PathVariable Long id, @RequestBody SpaSceneMode entity) {
        entity.setId(id);
        return Result.success("更新成功", sceneService.update(entity));
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("spa:scene:edit")
    public Result<Void> delete(@PathVariable Long id) {
        sceneService.delete(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/{id:\\d+}/slots")
    @RequirePermission("spa:scene:list")
    public Result<List<SpaSceneRoomSlot>> listSlots(@PathVariable Long id) {
        return Result.success(sceneService.listSlots(id));
    }

    @PutMapping("/{id:\\d+}/slots")
    @RequirePermission("spa:scene:edit")
    public Result<Void> saveSlots(@PathVariable Long id,
                                  @RequestBody List<SpaSceneRoomSlot> slots) {
        sceneService.saveSlots(id, slots);
        return Result.success("房间组合已保存", null);
    }

    @GetMapping("/{id:\\d+}/recommend")
    @RequirePermission("spa:scene:list")
    public Result<SpaSceneRecommendDTO> recommend(@PathVariable Long id) {
        return Result.success(sceneService.recommend(id));
    }
}