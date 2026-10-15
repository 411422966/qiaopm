package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.mapper.WalletTransactionMapper;
import com.meiyetec.service.WalletTransactionService;
import org.springframework.stereotype.Service;

@Service
public class WalletTransactionServiceImpl
        extends ServiceImpl<WalletTransactionMapper, WalletTransaction>
        implements WalletTransactionService {
}
