package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MaterialPackage;
import com.meiyetec.entity.MaterialPackageItem;
import com.meiyetec.service.MaterialPackageService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/material/package")
@RequiredArgsConstructor
@SysOpLog(module = "材料包")
public class MaterialPackageController {

    private final MaterialPackageService materialPackageService;

    @GetMapping("/list")
    @RequirePermission("material:package:list")
    public Result<List<MaterialPackage>> list(
            @RequestParam(required = false) String packageType,
            @RequestParam(required = false) Integer status) {
        return Result.success(materialPackageService.queryList(packageType, status));
    }

    @GetMapping("/hot")
    @RequirePermission("material:package:list")
    public Result<List<MaterialPackage>> hot(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(materialPackageService.findHotPackages(limit));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("material:package:list")
    public Result<MaterialPackage> getDetail(@PathVariable Long id) {
        return Result.success(materialPackageService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("material:package:edit")
    public Result<Void> create(@RequestBody MaterialPackage pkg) {
        materialPackageService.save(pkg);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("material:package:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialPackage pkg) {
        pkg.setId(id);
        materialPackageService.updateById(pkg);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("material:package:edit")
    public Result<Void> delete(@PathVariable Long id) {
        materialPackageService.removeById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 替换明细
     */
    @PutMapping("/{id:\\d+}/items")
    @RequirePermission("material:package:edit")
    public Result<Void> replaceItems(@PathVariable Long id, @RequestBody List<MaterialPackageItem> items) {
        materialPackageService.replaceItems(id, items);
        return Result.success("替换成功", null);
    }

    /**
     * 重新计算价格
     */
    @PostMapping("/{id:\\d+}/recalculate")
    @RequirePermission("material:package:edit")
    public Result<Void> recalculate(@PathVariable Long id) {
        materialPackageService.recalculatePrice(id);
        return Result.success(null, null);
    }

    @GetMapping("/statistics/type")
    @RequirePermission("material:package:list")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(materialPackageService.statisticsByType(storeId));
    }
}