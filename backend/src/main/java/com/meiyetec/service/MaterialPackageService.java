package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.MaterialPackage;
import com.meiyetec.entity.MaterialPackageItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
public interface MaterialPackageService extends IService<MaterialPackage> {    List<MaterialPackage> queryList(String packageType, Integer status);    List<MaterialPackage> findHotPackages(int limit);    List<Map<String, Object>> statisticsByType(Long storeId);    
/**    
 * 获取材料包详情(含明细)     
*/    MaterialPackage getDetail(Long id);    
/**    
 * 批量替换材料包明细     
*/    boolean replaceItems(Long packageId, List<MaterialPackageItem> items);    
/**    
 * 重新计算包总价     
*/    void recalculatePrice(Long packageId);}
