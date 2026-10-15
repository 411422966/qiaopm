package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    void updateStatus(@Param("id") Long id, @Param("status") Integer status);
}