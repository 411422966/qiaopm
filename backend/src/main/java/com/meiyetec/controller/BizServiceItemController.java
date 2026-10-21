package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.mapper.BizServiceItemMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * R2.1-A: /biz/service-item 路径别名（桥接到 BizServiceItemService 的 /biz/service/item）
 * 解决前端 /biz/service-item/{id} 路由 MISS 问题
 */
@RestController
@RequestMapping("/biz/service-item")
@RequiredArgsConstructor
public class BizServiceItemController {

    private final BizServiceItemMapper itemMapper;

    @Autowired
    private com.meiyetec.service.BizServiceItemService bizService;

    @GetMapping
    @RequirePermission("biz:service-item:list")
    public Result<?> list() {
        return bizService.itemPage(1, 100, null, null, null, null);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("biz:service-item:list")
    public Result<?> detail(@PathVariable Long id) {
        return bizService.itemDetail(id);
    }

    @PostMapping
    @RequirePermission("biz:service-item:edit")
    public Result<?> create(@RequestBody com.meiyetec.entity.BizServiceItem item) {
        return bizService.createItem(item);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("biz:service-item:edit")
    public Result<?> update(@PathVariable Long id, @RequestBody com.meiyetec.entity.BizServiceItem item) {
        return bizService.updateItem(id, item);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("biz:service-item:edit")
    public Result<?> delete(@PathVariable Long id) {
        return bizService.deleteItem(id);
    }
}