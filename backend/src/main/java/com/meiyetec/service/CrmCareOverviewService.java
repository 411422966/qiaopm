package com.meiyetec.service;

import java.util.Map;

/**
 * R3.0-H P0.5：CRM 护理运营看板汇总数据
 */
public interface CrmCareOverviewService {

    /**
     * 运营看板概览数据
     *
     * @param storeId 门店筛选（可选，null 表示全部）
     * @param ownerId 客服/跟进人筛选（可选，null 表示全部）
     * @return 汇总指标与趋势数据
     */
    Map<String, Object> overview(Long storeId, Long ownerId);
}
