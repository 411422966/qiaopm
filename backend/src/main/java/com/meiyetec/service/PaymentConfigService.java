package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PaymentChannel;
import com.meiyetec.entity.PaymentConfig;
import com.meiyetec.entity.PaymentTransactionLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.PaymentChannelMapper;
import com.meiyetec.mapper.PaymentConfigMapper;
import com.meiyetec.mapper.PaymentTransactionLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.10：支付配置管理（修复 Dcep Mock）
 *
 * <p>提供：
 * <ul>
 *   <li>支付渠道 CRUD（8 预置渠道）</li>
 *   <li>支付配置管理（merchant / app / key / 费率）</li>
 *   <li>支付流水日志（请求/响应/错误/耗时）</li>
 *   <li>手续费计算</li>
 *   <li>渠道启用/禁用</li>
 * </ul>
 *
 * <p>修复：原 DcepService.mockPrepay() → 真实支付配置
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentConfigService {

    private final PaymentConfigMapper configMapper;
    private final PaymentChannelMapper channelMapper;
    private final PaymentTransactionLogMapper logMapper;

    // ====================================================================
    // 1. 支付渠道
    // ====================================================================

    @GetMapping("/channel/list")
    public Result<List<PaymentChannel>> listChannels() {
        return Result.success(channelMapper.selectList(
                new LambdaQueryWrapper<PaymentChannel>()
                        .eq(PaymentChannel::getEnabled, 1)
                        .orderByAsc(PaymentChannel::getSortOrder)));
    }

    @PostMapping("/channel/{code}/toggle")
    @Transactional(rollbackFor = Exception.class)
    public Result<PaymentChannel> toggleChannel(@PathVariable String code, @RequestBody Map<String, Object> body) {
        PaymentChannel ch = channelMapper.selectOne(
                new LambdaQueryWrapper<PaymentChannel>().eq(PaymentChannel::getChannelCode, code));
        if (ch == null) return Result.error(BizCode.NOT_FOUND, "渠道不存在");
        ch.setEnabled(body.get("enabled") == null ? 1 : (((Number) body.get("enabled")).intValue()));
        channelMapper.updateById(ch);
        return Result.success("已切换", ch);
    }

    // ====================================================================
    // 2. 支付配置
    // ====================================================================

    @PostMapping("/config")
    @Transactional(rollbackFor = Exception.class)
    public Result<PaymentConfig> upsertConfig(@RequestBody PaymentConfig c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        c.setTenantId(tenantId);
        if (c.getFeeRate() == null) c.setFeeRate(new BigDecimal("0.006"));
        if (c.getFeeType() == null) c.setFeeType("PERCENTAGE");
        if (c.getEnv() == null) c.setEnv("PROD");
        if (c.getStatus() == null) c.setStatus("ACTIVE");

        PaymentConfig exist = configMapper.selectOne(
                new LambdaQueryWrapper<PaymentConfig>()
                        .eq(PaymentConfig::getTenantId, tenantId)
                        .eq(PaymentConfig::getConfigCode, c.getConfigCode()));
        if (exist != null) {
            exist.setConfigName(c.getConfigName());
            exist.setChannelCode(c.getChannelCode());
            exist.setMerchantId(c.getMerchantId());
            exist.setAppId(c.getAppId());
            exist.setNotifyUrl(c.getNotifyUrl());
            exist.setFeeRate(c.getFeeRate());
            exist.setFeeType(c.getFeeType());
            exist.setEnv(c.getEnv());
            exist.setStatus(c.getStatus());
            configMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        configMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/config/list")
    public Result<List<PaymentConfig>> listConfigs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(configMapper.selectList(
                new LambdaQueryWrapper<PaymentConfig>()
                        .eq(PaymentConfig::getTenantId, tenantId)
                        .orderByAsc(PaymentConfig::getChannelCode)));
    }

    @GetMapping("/config/{code}")
    public Result<PaymentConfig> getConfig(@PathVariable String code) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(configMapper.selectOne(
                new LambdaQueryWrapper<PaymentConfig>()
                        .eq(PaymentConfig::getTenantId, tenantId)
                        .eq(PaymentConfig::getConfigCode, code)));
    }

    @PostMapping("/config/{code}/toggle")
    @Transactional(rollbackFor = Exception.class)
    public Result<PaymentConfig> toggleConfig(@PathVariable String code, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PaymentConfig c = configMapper.selectOne(
                new LambdaQueryWrapper<PaymentConfig>()
                        .eq(PaymentConfig::getTenantId, tenantId)
                        .eq(PaymentConfig::getConfigCode, code));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "配置不存在");
        c.setStatus(((Number) body.get("status")).intValue() == 1 ? "ACTIVE" : "DISABLED");
        configMapper.updateById(c);
        return Result.success("已切换", c);
    }

    // ====================================================================
    // 3. 手续费计算
    // ====================================================================

    @PostMapping("/fee/calculate")
    public Result<Map<String, Object>> calculateFee(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String channelCode = (String) body.get("channelCode");
        Long tenantId = TenantContextHolder.get().getTenantId();
        PaymentConfig c = configMapper.selectOne(
                new LambdaQueryWrapper<PaymentConfig>()
                        .eq(PaymentConfig::getTenantId, tenantId)
                        .eq(PaymentConfig::getChannelCode, channelCode)
                        .eq(PaymentConfig::getStatus, "ACTIVE"));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "渠道配置不存在");

        BigDecimal fee = calcFee(amount, c.getFeeRate(), c.getFeeType(), c.getMinFee(), c.getMaxFee());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount);
        data.put("channelCode", channelCode);
        data.put("feeRate", c.getFeeRate());
        data.put("feeType", c.getFeeType());
        data.put("fee", fee);
        data.put("netAmount", amount.subtract(fee));
        data.put("minFee", c.getMinFee());
        data.put("maxFee", c.getMaxFee());
        return Result.success(data);
    }

    /**
     * 手续费计算：
     * PERCENTAGE: amount * rate（取 min/max 限制）
     * FIXED: minFee
     */
    public static BigDecimal calcFee(BigDecimal amount, BigDecimal rate,
                                      String feeType, BigDecimal minFee, BigDecimal maxFee) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal fee;
        if ("FIXED".equals(feeType)) {
            fee = minFee != null ? minFee : BigDecimal.ZERO;
        } else {
            fee = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            if (minFee != null && fee.compareTo(minFee) < 0) fee = minFee;
            if (maxFee != null && fee.compareTo(maxFee) > 0) fee = maxFee;
        }
        return fee;
    }

    // ====================================================================
    // 4. 支付流水日志
    // ====================================================================

    @PostMapping("/log/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<PaymentTransactionLog> createLog(@RequestBody PaymentTransactionLog l) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        l.setTenantId(tenantId);
        if (l.getStatus() == null) l.setStatus("INIT");
        if (l.getCreatedAt() == null) l.setCreatedAt(LocalDateTime.now());
        logMapper.insert(l);
        return Result.success("已记录", l);
    }

    @PostMapping("/log/{id}/callback")
    @Transactional(rollbackFor = Exception.class)
    public Result<PaymentTransactionLog> callback(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        PaymentTransactionLog l = logMapper.selectById(id);
        if (l == null) return Result.error(BizCode.NOT_FOUND, "流水不存在");
        if (body.get("status") != null) l.setStatus((String) body.get("status"));
        if (body.get("tradeNo") != null) l.setTradeNo((String) body.get("tradeNo"));
        if (body.get("responsePayload") != null) l.setResponsePayload(String.valueOf(body.get("responsePayload")));
        if (body.get("errorCode") != null) l.setErrorCode((String) body.get("errorCode"));
        if (body.get("errorMessage") != null) l.setErrorMessage((String) body.get("errorMessage"));
        if (l.getCreatedAt() != null) {
            l.setCostMs((int) Duration.between(l.getCreatedAt(), LocalDateTime.now()).toMillis());
        }
        logMapper.updateById(l);
        return Result.success("已回调", l);
    }

    @GetMapping("/log/list")
    public Result<List<PaymentTransactionLog>> listLogs(@RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(logMapper.selectList(
                new LambdaQueryWrapper<PaymentTransactionLog>()
                        .eq(PaymentTransactionLog::getTenantId, tenantId)
                        .orderByDesc(PaymentTransactionLog::getId)
                        .last("LIMIT " + limit)));
    }

    @GetMapping("/log/stat")
    public Result<Map<String, Object>> logStat(@RequestParam(required = false) String channelCode) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PaymentTransactionLog> qw = new LambdaQueryWrapper<>();
        qw.eq(PaymentTransactionLog::getTenantId, tenantId);
        if (channelCode != null) qw.eq(PaymentTransactionLog::getChannelCode, channelCode);
        List<PaymentTransactionLog> all = logMapper.selectList(qw);

        long success = all.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long failed = all.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
        double avgCost = all.stream().filter(l -> l.getCostMs() != null)
                .mapToInt(PaymentTransactionLog::getCostMs).average().orElse(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", all.size());
        data.put("success", success);
        data.put("failed", failed);
        data.put("avgCostMs", avgCost);
        return Result.success(data);
    }
}