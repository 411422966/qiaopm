package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.FeatureFlag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeatureFlagMapper extends BaseMapper<FeatureFlag> {}