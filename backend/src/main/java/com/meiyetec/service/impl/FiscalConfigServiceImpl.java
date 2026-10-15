package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.FiscalConfig;
import com.meiyetec.mapper.FiscalConfigMapper;
import com.meiyetec.service.FiscalConfigService;
import org.springframework.stereotype.Service;

@Service
public class FiscalConfigServiceImpl
        extends ServiceImpl<FiscalConfigMapper, FiscalConfig>
        implements FiscalConfigService {
}
