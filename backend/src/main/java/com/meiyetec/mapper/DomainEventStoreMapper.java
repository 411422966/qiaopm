package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DomainEventStore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DomainEventStoreMapper extends BaseMapper<DomainEventStore> {}