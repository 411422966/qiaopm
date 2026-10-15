package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.PackageItem;
import com.meiyetec.mapper.PackageItemMapper;
import com.meiyetec.service.PackageItemService;
import org.springframework.stereotype.Service;

@Service
public class PackageItemServiceImpl extends ServiceImpl<PackageItemMapper, PackageItem> implements PackageItemService {
}
