package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepConfig;
import com.meiyetec.mapper.DcepConfigMapper;
import com.meiyetec.service.DcepConfigService;
import org.springframework.stereotype.Service;

@Service
public class DcepConfigServiceImpl extends ServiceImpl<DcepConfigMapper, DcepConfig> implements DcepConfigService {}
