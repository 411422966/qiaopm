package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.ServiceMaterialCost;
import com.meiyetec.mapper.ServiceMaterialCostMapper;
import com.meiyetec.service.ServiceMaterialCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceMaterialCostServiceImpl extends ServiceImpl<ServiceMaterialCostMapper, ServiceMaterialCost>
        implements ServiceMaterialCostService {

    private final ServiceMaterialCostMapper serviceMaterialCostMapper;

    @Override
    public List<Map<String, Object>> getServiceCostAnalysis() {
        List<Map<String, Object>> raw = serviceMaterialCostMapper.getServiceCostAnalysis();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            BigDecimal servicePrice = toDecimal(row.get("service_price"));
            BigDecimal materialCost = toDecimal(row.get("material_cost"));
            BigDecimal costRatio = BigDecimal.ZERO;
            if (servicePrice != null && servicePrice.signum() > 0) {
                costRatio = materialCost
                        .multiply(new BigDecimal("100"))
                        .divide(servicePrice, 2, RoundingMode.HALF_UP);
            }
            row.put("cost_ratio", costRatio);
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Object> getServiceCost(Long serviceId) {
        Map<String, Object> row = serviceMaterialCostMapper.getServiceCost(serviceId);
        if (row == null) return null;
        BigDecimal servicePrice = toDecimal(row.get("service_price"));
        BigDecimal materialCost = toDecimal(row.get("material_cost"));
        BigDecimal costRatio = BigDecimal.ZERO;
        if (servicePrice != null && servicePrice.signum() > 0) {
            costRatio = materialCost
                    .multiply(new BigDecimal("100"))
                    .divide(servicePrice, 2, RoundingMode.HALF_UP);
        }
        row.put("cost_ratio", costRatio);
        return row;
    }

    private BigDecimal toDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return new BigDecimal(obj.toString());
        return BigDecimal.ZERO;
    }
}
