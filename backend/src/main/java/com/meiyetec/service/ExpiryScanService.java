package com.meiyetec.service;

import com.meiyetec.entity.StockInventory;

import java.util.List;
import java.util.Map;

/**
 * 效期扫描引擎
 * 定期扫描库存，生成预警
 */
public interface ExpiryScanService {

    /**
     * 执行扫描
     *
     * @param storeId 门店ID(可选)
     * @return 扫描统计
     */
    Map<String, Object> scan(Long storeId);

    /**
     * 查询临期批次
     */
    List<StockInventory> findNearExpiry(Long storeId, Integer withinDays);
}
