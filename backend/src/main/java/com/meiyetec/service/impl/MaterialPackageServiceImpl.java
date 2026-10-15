package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MaterialPackage;
import com.meiyetec.entity.MaterialPackageItem;
import com.meiyetec.mapper.MaterialPackageItemMapper;
import com.meiyetec.mapper.MaterialPackageMapper;
import com.meiyetec.service.MaterialPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaterialPackageServiceImpl extends ServiceImpl<MaterialPackageMapper, MaterialPackage>
        implements MaterialPackageService {

    private final MaterialPackageMapper materialPackageMapper;
    private final MaterialPackageItemMapper itemMapper;

    @Override
    public List<MaterialPackage> queryList(String packageType, Integer status) {
        return materialPackageMapper.queryList(packageType, status);
    }

    @Override
    public List<MaterialPackage> findHotPackages(int limit) {
        return materialPackageMapper.findHotPackages(limit);
    }

    @Override
    public List<Map<String, Object>> statisticsByType(Long storeId) {
        return materialPackageMapper.statisticsByType(storeId);
    }

    @Override
    public MaterialPackage getDetail(Long id) {
        MaterialPackage pkg = getById(id);
        if (pkg != null) {
            pkg.setRemark(pkg.getRemark());
            // 通过Spring的BeanWrapper或者自定义JSON转换
            try {
                java.lang.reflect.Field itemsField = MaterialPackage.class.getDeclaredField("items");
                itemsField.setAccessible(true);
                itemsField.set(pkg, itemMapper.findByPackageId(id));
            } catch (Exception e) {}
        }
        return pkg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceItems(Long packageId, List<MaterialPackageItem> items) {
        // 删除旧明细
        List<MaterialPackageItem> existing = itemMapper.findByPackageId(packageId);
        for (MaterialPackageItem old : existing) {
            itemMapper.deleteById(old.getId());
        }
        // 新增明细
        if (items != null && !items.isEmpty()) {
            for (MaterialPackageItem item : items) {
                item.setId(null);
                item.setPackageId(packageId);
                if (item.getSubtotal() == null && item.getQuantity() != null && item.getUnitPrice() != null) {
                    item.setSubtotal(item.getQuantity().multiply(item.getUnitPrice()));
                }
                itemMapper.insert(item);
            }
        }
        // 重新计算
        recalculatePrice(packageId);
        return true;
    }

    @Override
    public void recalculatePrice(Long packageId) {
        List<MaterialPackageItem> items = itemMapper.findByPackageId(packageId);
        BigDecimal totalCost = BigDecimal.ZERO;
        for (MaterialPackageItem item : items) {
            if (item.getSubtotal() != null) {
                totalCost = totalCost.add(item.getSubtotal());
            }
        }
        MaterialPackage pkg = getById(packageId);
        if (pkg != null) {
            pkg.setTotalCost(totalCost);
            // 实际售价 = 售价 * 折扣
            if (pkg.getSalePrice() != null && pkg.getDiscountRate() != null) {
                pkg.setFinalPrice(pkg.getSalePrice().multiply(pkg.getDiscountRate()).setScale(2, RoundingMode.HALF_UP));
            }
            updateById(pkg);
        }
    }
}

