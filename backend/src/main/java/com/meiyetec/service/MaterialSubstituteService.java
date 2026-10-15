package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.MaterialSubstitute;
import java.util.List;
import java.util.Map;

public interface MaterialSubstituteService extends IService<MaterialSubstitute> {
    List<MaterialSubstitute> findByMainProduct(Long mainProductId, Long tenantId);
    List<MaterialSubstitute> findAllWithDetails(Long tenantId);

    /**
     * P2-12 IND-12：根据款式 ID 解析 requiredTools → 查 stock_product → 取 BOM
     * → 按 MaterialSubstitute 规则做库存替代 → 返回前端可直接展示的耗材列表。
     *
     * <p>链路：
     * <pre>
     * style.requiredTools(JSON) → stock_product.id → biz_service_material BOM
     *                             → stock_product 库存
     *                             → MaterialSubstitute 替代规则
     *                             → 返回 resolvedMaterials
     * </pre>
     *
     * @param styleId     款式 ID
     * @param tenantId    租户 ID
     * @return 解析后的耗材列表，每项含 isSubstitute / stockQty / substituteReason
     */
    List<Map<String, Object>> getMaterialsForStyle(Long styleId, Long tenantId);
}
