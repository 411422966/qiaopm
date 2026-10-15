package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.StyleLibrary;
import com.meiyetec.service.StyleLibraryService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/style")
@RequiredArgsConstructor
@SysOpLog(module = "款式库")
public class StyleLibraryController {

    private final StyleLibraryService styleLibraryService;

    @GetMapping("/list")
    public Result<List<StyleLibrary>> list(
            @RequestParam(required = false) String styleType,
            @RequestParam(required = false) String styleTag,
            @RequestParam(required = false) Integer difficultyLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer isNew,
            @RequestParam(required = false) Integer isHot,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sort) {
        // 兼容两套字段名：styleType=category, styleTag=tag
        String cat = category != null ? category : styleType;
        String tg  = tag != null ? tag : styleTag;
        return Result.success(styleLibraryService.queryList(cat, tg, difficultyLevel, status, isNew, isHot));
    }

    @GetMapping("/hot")
    public Result<List<StyleLibrary>> hot() {
        return Result.success(styleLibraryService.findHotStyles());
    }

    @GetMapping("/{id:\\d+}")
    public Result<StyleLibrary> getById(@PathVariable Long id) {
        return Result.success(styleLibraryService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody StyleLibrary style) {
        styleLibraryService.save(style);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StyleLibrary style) {
        style.setId(id);
        styleLibraryService.updateById(style);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        styleLibraryService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/statistics/type")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(styleLibraryService.statisticsByType(storeId));
    }

    @PostMapping("/{id:\\d+}/view")
    public Result<Void> recordView(@PathVariable Long id) {
        styleLibraryService.recordView(id);
        return Result.success(null, null);
    }

    @PostMapping("/{id:\\d+}/like")
    public Result<Integer> toggleLike(@PathVariable Long id) {
        int likes = styleLibraryService.toggleLike(id);
        return Result.success(likes);
    }

    /**
     * P2-12 IND-12：款式快速下单
     *
     * <p>请求体（JSON）：
     * <pre>
     * {
     *   "styleId": 1,
     *   "memberId": 10,
     *   "technicianId": 5,    // 可选
     *   "storeId": 3,         // 可选
     *   "bookingDate": "2026-09-10",
     *   "startTime": "14:00",  // HH:mm
     *   "remark": "加急"
     * }
     * </pre>
     *
     * <p>返回：
     * <pre>
     * {
     *   "bookingId": 123,
     *   "bookingNo": "BK...",
     *   "styleId": 1,
     *   "styleName": "经典法式白边",
     *   "technicianName": "小红",
     *   "bookingDate": "2026-09-10",
     *   "startTime": "14:00",
     *   "endTime": "15:00",
     *   "status": 1,
     *   "materials": [
     *     { "productId": 1, "productName": "底胶", "quantity": 1, "unit": "瓶", "isSubstitute": false },
     *     { "productId": 2, "productName": "白胶（替代）", "quantity": 1, "unit": "瓶", "isSubstitute": true }
     *   ],
     *   "message": "预约成功"
     * }
     * </pre>
     */
    @PostMapping("/quick-order")
    public Result<Map<String, Object>> quickOrder(@RequestBody Map<String, Object> body) {
        return Result.success(styleLibraryService.quickOrderFull(body));
    }

    /**
     * P2-12 IND-12：兼容旧版 /style/{id}/order 路径
     */
    @PostMapping("/{id:\\d+}/order")
    public Result<Map<String, Object>> quickOrderLegacy(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        body.put("styleId", id);
        return Result.success(styleLibraryService.quickOrderFull(body));
    }

    @PostMapping("/init-default")
    public Result<Map<String, Object>> initDefault() {
        int created = styleLibraryService.initDefault();
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("message", created > 0 ? "成功创建 " + created + " 个默认款式" : "已有款式数据，无需重复初始化");
        return Result.success(result);
    }

    @GetMapping("/recommend")
    public Result<List<StyleLibrary>> recommend(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String styleType,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(styleLibraryService.recommend(memberId, styleType, limit));
    }
}
