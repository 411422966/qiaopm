package com.meiyetec.service;

import com.meiyetec.entity.WmsStockTransfer;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M12 跨店库存统一 API 控制器
 *
 * <p>委托给 {@link com.meiyetec.service.impl.CrossStoreInventoryServiceImpl} 实现。
 * 路径前缀：/wms/cross-store
 */
@Slf4j
@RestController
@RequestMapping("/wms/cross-store")
@RequiredArgsConstructor
public class CrossStoreInventoryController {

    private final com.meiyetec.service.CrossStoreInventoryService delegate;

    private Long tenantId() {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) throw new IllegalStateException("未识别租户");
        return tid;
    }

    // 1. 跨店聚合视图
    @GetMapping("/inventory/aggregate")
    public Result<Map<String, Object>> aggregateInventory(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String alertOnly,
            @RequestParam(defaultValue = "100") Integer limit) {
        return Result.success(delegate.aggregateInventory(tenantId(), productId, keyword, alertOnly, limit));
    }

    // 2. 智能调度扫描
    @PostMapping("/schedule/scan")
    public Result<Map<String, Object>> scanSchedule(@RequestBody(required = false) Map<String, Object> body) {
        return Result.success(delegate.scanSchedule(tenantId(), body));
    }

    // 3. 采纳建议
    @PostMapping("/suggestion/{id}/accept")
    public Result<Map<String, Object>> acceptSuggestion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success(delegate.acceptSuggestion(tenantId(), id, body));
    }

    // 4. 建议分页
    @GetMapping("/suggestion/page")
    public Result<Map<String, Object>> suggestionPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer accepted,
            @RequestParam(required = false) String priority) {
        return Result.success(delegate.suggestionPage(tenantId(), pageNum, pageSize, accepted, priority));
    }

    // 5. 创建调拨单
    @PostMapping("/transfer")
    public Result<WmsStockTransfer> createTransfer(@RequestBody WmsStockTransfer t) {
        WmsStockTransfer result = delegate.createTransfer(tenantId(), t);
        return Result.success("调拨单已创建（待审批）", result);
    }

    // 6. 调拨单分页
    @GetMapping("/transfer/page")
    public Result<Map<String, Object>> transferPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(delegate.transferPage(tenantId(), pageNum, pageSize, status, keyword));
    }

    // 7. 调拨单详情
    @GetMapping("/transfer/{id}")
    public Result<Map<String, Object>> transferDetail(@PathVariable Long id) {
        return Result.success(delegate.transferDetail(tenantId(), id));
    }

    // 8. 审批
    @PostMapping("/transfer/{id}/approve")
    public Result<WmsStockTransfer> approveTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success("已审批", delegate.approveTransfer(tenantId(), id, body));
    }

    // 9. 发货
    @PostMapping("/transfer/{id}/ship")
    public Result<WmsStockTransfer> shipTransfer(@PathVariable Long id) {
        return Result.success("已发货", delegate.shipTransfer(tenantId(), id));
    }

    // 10. 收货
    @PostMapping("/transfer/{id}/receive")
    public Result<WmsStockTransfer> receiveTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success("已收货", delegate.receiveTransfer(tenantId(), id, body));
    }

    // 11. 取消调拨
    @PostMapping("/transfer/{id}/cancel")
    public Result<WmsStockTransfer> cancelTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.success("已取消", delegate.cancelTransfer(tenantId(), id, body));
    }

    // 12. 调度仪表盘
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(delegate.dashboard(tenantId()));
    }

    // 13. 门店下拉
    @GetMapping("/store/list")
    public Result<Object> storeList() {
        return Result.success(delegate.storeList(tenantId()));
    }

    // 14. 商品下拉
    @GetMapping("/product/list")
    public Result<Object> productList(@RequestParam(required = false) String keyword) {
        return Result.success(delegate.productList(tenantId(), keyword));
    }
}
