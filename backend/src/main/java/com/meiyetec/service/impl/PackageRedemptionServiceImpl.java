package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.PackageRedemption;
import com.meiyetec.mapper.PackageRedemptionMapper;
import com.meiyetec.service.PackageRedemptionService;
import org.springframework.stereotype.Service;

@Service
public class PackageRedemptionServiceImpl extends ServiceImpl<PackageRedemptionMapper, PackageRedemption> implements PackageRedemptionService {
}
