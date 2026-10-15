package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PaymentIntent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentIntentMapper extends BaseMapper<PaymentIntent> {}