package com.meiyetec.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Map;

public interface CouponService {
    
    IPage<Map<String, Object>> couponPage(int pageNum, int pageSize, String name, Integer status);
    
    Map<String, Object> getCoupon(Long id);
    
    void createCoupon(Map<String, Object> params);
    
    void updateCoupon(Long id, Map<String, Object> params);
    
    void deleteCoupon(Long id);
    
    IPage<Map<String, Object>> memberCoupons(Long memberId, Integer status, int pageNum, int pageSize);
    
    Map<String, Object> grantCoupon(Long couponId, Long memberId, Integer source);
    
    Map<String, Object> useCoupon(Long grantId, Long orderId);

    Integer getMemberCouponCount(Long memberId);

    int batchGrantByLevel(Map<String, Object> params);
}
