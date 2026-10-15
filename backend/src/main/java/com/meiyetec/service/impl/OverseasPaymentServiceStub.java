package com.meiyetec.service.impl;
import com.meiyetec.service.OverseasPaymentService;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.*;
import java.math.*;
import java.sql.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.stream.*;
import com.meiyetec.entity.*;
import com.meiyetec.util.*;
import com.meiyetec.iot.*;
import com.meiyetec.payment.*;
import com.meiyetec.dto.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.mapper.OverseasPaymentMapper;
@Service
public class OverseasPaymentServiceStub extends ServiceImpl<OverseasPaymentMapper, OverseasPayment> implements OverseasPaymentService {
    @Override
    public List<OverseasPayment> queryList(Long orderId, Long memberId, String gatewayCode, String status, LocalDateTime startTime, LocalDateTime endTime, Long tenantId) { return null; }
    @Override
    public String selectBestGateway(String currency, String countryCode, String paymentMethod, BigDecimal amount, Long tenantId) { return null; }
    @Override
    public OverseasPayment createPayment(String orderType, Long orderId, String orderNo, Long memberId, String memberName, String memberEmail, BigDecimal amount, String currency, String paymentMethod, Long tenantId) { return null; }
    @Override
    public OverseasPayment refund(String paymentId, BigDecimal amount, String reason, Long tenantId) { return null; }
    @Override
    public Map<String, Object> statistics(LocalDateTime startTime, LocalDateTime endTime, Long tenantId) { return null; }
}