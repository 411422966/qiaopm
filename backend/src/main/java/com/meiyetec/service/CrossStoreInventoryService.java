package com.meiyetec.service;

import com.meiyetec.entity.WmsStockTransfer;
import java.util.Map;

/**
 * M12 跨店库存统一 Service 接口
 */
public interface CrossStoreInventoryService {

    // 1. 跨店聚合视图
    Map<String, Object> aggregateInventory(Long tenantId, Long productId, String keyword,
                                           String alertOnly, Integer limit);

    // 2. 智能调度扫描（BALANCE-V2）
    Map<String, Object> scanSchedule(Long tenantId, Map<String, Object> body);

    // 3. 采纳建议 → 生成调拨单
    Map<String, Object> acceptSuggestion(Long tenantId, Long suggestionId, Map<String, Object> body);

    // 4. 建议分页
    Map<String, Object> suggestionPage(Long tenantId, int pageNum, int pageSize,
                                       Integer accepted, String priority);

    // 5. 创建调拨单
    WmsStockTransfer createTransfer(Long tenantId, WmsStockTransfer t);

    // 6. 调拨单分页
    Map<String, Object> transferPage(Long tenantId, int pageNum, int pageSize,
                                     String status, String keyword);

    // 7. 调拨单详情
    Map<String, Object> transferDetail(Long tenantId, Long id);

    // 8. 审批
    WmsStockTransfer approveTransfer(Long tenantId, Long id, Map<String, Object> body);

    // 9. 发货
    WmsStockTransfer shipTransfer(Long tenantId, Long id);

    // 10. 收货
    WmsStockTransfer receiveTransfer(Long tenantId, Long id, Map<String, Object> body);

    // 11. 取消调拨
    WmsStockTransfer cancelTransfer(Long tenantId, Long id, Map<String, Object> body);

    // 12. 调度仪表盘
    Map<String, Object> dashboard(Long tenantId);

    // 13. 门店下拉
    Object storeList(Long tenantId);

    // 14. 商品下拉
    Object productList(Long tenantId, String keyword);
}
