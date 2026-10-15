package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.HuabeiOrder;
import com.meiyetec.util.Result;

import java.math.BigDecimal;
import java.util.Map;

public interface HuabeiService {

    Result<Map<String, Object>> queryCreditScore(Long memberId, String phone, Long tenantId);

    Result<HuabeiOrder> preAuth(Long memberId, String bizOrderNo,
                                BigDecimal amount, Integer period, Long tenantId);

    Result<HuabeiOrder> createInstallmentOrder(Long memberId, String bizOrderNo,
                                               BigDecimal amount, Integer period, Long tenantId);

    Result<Page<HuabeiOrder>> listOrders(Long memberId, Integer status,
                                         long current, long size, Long tenantId);

    Result<Void> cancel(Long orderId, Long tenantId);

    Result<Void> simulateNotify(Long orderId, String status, Long tenantId);
}