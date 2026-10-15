package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.AggregateRoot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AggregateRootMapper extends BaseMapper<AggregateRoot> {}