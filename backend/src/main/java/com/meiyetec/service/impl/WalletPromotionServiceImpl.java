package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.WalletPromotion;
import com.meiyetec.mapper.WalletPromotionMapper;
import com.meiyetec.service.WalletPromotionService;
import org.springframework.stereotype.Service;

@Service
public class WalletPromotionServiceImpl extends ServiceImpl<WalletPromotionMapper, WalletPromotion>
        implements WalletPromotionService {
}
