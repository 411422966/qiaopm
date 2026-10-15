package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CrmContactMap;
import com.meiyetec.mapper.CrmContactMapMapper;
import com.meiyetec.service.CrmContactMapService;
import org.springframework.stereotype.Service;

@Service
public class CrmContactMapServiceImpl
        extends ServiceImpl<CrmContactMapMapper, CrmContactMap>
        implements CrmContactMapService {
}
