package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.PackageOrder;
import com.meiyetec.mapper.PackageOrderMapper;
import com.meiyetec.service.PackageOrderService;
import org.springframework.stereotype.Service;

@Service
public class PackageOrderServiceImpl extends ServiceImpl<PackageOrderMapper, PackageOrder> implements PackageOrderService {
}
