package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.BnplOrder;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BnplOrderMapper;
import com.meiyetec.service.BnplService;
import com.meiyetec.payment.bnpl.BnplProvider;
import com.meiyetec.payment.bnpl.BnplProviderRouter;
import com.meiyetec.payment.bnpl.BnplProvider.WebhookResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BnplServiceImpl implements BnplService {

    private final BnplOrderMapper bnplOrderMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BnplProviderRouter bnplProviderRouter;

    private static final Map<String, int[]> PLATFORM_PERIODS = Map.of(
        "atome",    new int[]{3},
        "klarna",   new int[]{3, 6, 12},
        "afterpay", new int[]{4},
        "affirm",   new int[]{3, 6, 12}
    );

    private static final Map<String, BigDecimal> PLATFORM_FEES = Map.of(
        "atome",    BigDecimal.valueOf(0.015),
        "klarna",   BigDecimal.valueOf(0.029),
        "afterpay", BigDecimal.valueOf(0.020),
        "affirm",   BigDecimal.valueOf(0.050)
    );

    @Override
    public Result<Map<String, Object>> queryCredit(String provider, String phone, Long tenantId) {
        if (provider == null || provider.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "BNPL 平台必填");
        }
        if (!PLATFORM_PERIODS.containsKey(provider)) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "不支持的平台: " + provider + "，支持的: " + PLATFORM_PERIODS.keySet());
        }

        int[] periods = PLATFORM_PERIODS.get(provider);
        BigDecimal feeRate = PLATFORM_FEES.getOrDefault(provider, BigDecimal.valueOf(0.02));

        BigDecimal creditLimit = BigDecimal.valueOf(1000 + Math.abs(phone.hashCode() % 9000));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("phone", phone);
        data.put("creditLimit", creditLimit);
        data.put("availableLimit", creditLimit);
        data.put("supportedPeriods", periods);
        data.put("feeRate", feeRate);
        data.put("status", "APPROVED");
        data.put("queryTime", LocalDateTime.now().toString());

        return Result.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<BnplOrder> createOrder(Long memberId, String provider,
            String bizOrderNo, BigDecimal amount, String currency, Integer installments, Long tenantId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "金额必须大于 0");
        }
        if (!PLATFORM_PERIODS.containsKey(provider)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "不支持的平台: " + provider);
        }
        int[] allowed = PLATFORM_PERIODS.get(provider);
        if (installments == null || Arrays.stream(allowed).noneMatch(p -> p == installments)) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    provider + " 支持分期: " + Arrays.toString(allowed));
        }

        Map<String, Object> member = jdbc.queryForMap(
            "SELECT name, phone, email FROM crm_member WHERE id = ? AND deleted = 0", memberId);

        BigDecimal feeRate = PLATFORM_FEES.getOrDefault(provider, BigDecimal.valueOf(0.02));
        BigDecimal platformFee = amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal downPayment = amount.multiply(BigDecimal.valueOf(0.3333)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal installmentAmount = amount.subtract(downPayment)
                .divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);

        List<Map<String, Object>> schedule = new ArrayList<>();
        for (int i = 1; i <= installments; i++) {
            Map<String, Object> inst = new LinkedHashMap<>();
            inst.put("seq", i);
            inst.put("amount", installmentAmount);
            inst.put("dueDate", LocalDate.now().plusMonths(i).format(DateTimeFormatter.ISO_DATE));
            inst.put("status", "PENDING");
            schedule.add(inst);
        }

        BnplOrder order = new BnplOrder();
        order.setTenantId(tenantId == null ? 1L : tenantId);
        order.setBizOrderNo(bizOrderNo);
        order.setMemberId(memberId);
        order.setMemberName((String) member.get("name"));
        order.setMemberEmail((String) member.get("email"));
        order.setMemberPhone((String) member.get("phone"));
        order.setProvider(provider.toUpperCase());
        order.setProviderOrderNo(provider.toUpperCase() + "-" + System.currentTimeMillis());
        order.setAmount(amount);
        order.setCurrency(currency == null ? "CNY" : currency);
        order.setInstallments(installments);
        order.setInstallmentAmount(installmentAmount);
        order.setDownPayment(downPayment);
        order.setPlatformFee(platformFee);
        order.setStatus(1);
        try {
            order.setRepaymentSchedule(new ObjectMapper().writeValueAsString(schedule));
        } catch (JsonProcessingException e) {
            order.setRepaymentSchedule("[]");
        }
        order.setFirstRepayDate(LocalDate.now().plusMonths(1));
        order.setRemark("BNPL-" + provider + "-" + installments + "期");

        bnplOrderMapper.insert(order);
        return Result.success("BNPL 订单已创建", order);
    }

    @Override
    public Result<Page<BnplOrder>> listOrders(String provider, Long memberId,
            Integer status, long current, long size, Long tenantId) {
        LambdaQueryWrapper<BnplOrder> q = new LambdaQueryWrapper<>();
        if (tenantId != null) q.eq(BnplOrder::getTenantId, tenantId);
        if (provider != null && !provider.isEmpty()) q.eq(BnplOrder::getProvider, provider.toUpperCase());
        if (memberId != null) q.eq(BnplOrder::getMemberId, memberId);
        if (status != null) q.eq(BnplOrder::getStatus, status);
        q.orderByDesc(BnplOrder::getCreateTime);

        Page<BnplOrder> page = new Page<>(current, size);
        Page<BnplOrder> result = bnplOrderMapper.selectPage(page, q);
        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancel(Long orderId, Long tenantId) {
        BnplOrder order = bnplOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(BizCode.NOT_FOUND);
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            throw new BusinessException(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) {
            throw new BusinessException(BizCode.BAD_REQUEST, "仅待确认状态可取消");
        }
        order.setStatus(6);
        bnplOrderMapper.updateById(order);
        return Result.success("已取消", null);
    }

    @Override
    public Result<Map<String, Object>> statistics(String startDate, String endDate, Long tenantId) {
        String where = "WHERE deleted = 0 ";
        List<Object> args = new ArrayList<>();
        if (tenantId != null) { where += "AND tenant_id = ? "; args.add(tenantId); }
        if (startDate != null && !startDate.isEmpty()) {
            where += "AND create_time >= ? ";
            args.add(startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            where += "AND create_time <= ? ";
            args.add(endDate);
        }

        String sql = "SELECT provider, COUNT(*) AS cnt, "
                + "COALESCE(SUM(amount), 0) AS total_amount, "
                + "COALESCE(SUM(platform_fee), 0) AS total_fee, "
                + "SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END) AS completed, "
                + "SUM(CASE WHEN status = 8 THEN 1 ELSE 0 END) AS refunded "
                + "FROM biz_bnpl_order "
                + where + " GROUP BY provider ORDER BY total_amount DESC";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("byProvider", rows);
        data.put("periodStart", startDate);
        data.put("periodEnd", endDate);
        return Result.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> simulateNotify(Long orderId, String eventType, Long tenantId) {
        BnplOrder order = bnplOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(BizCode.NOT_FOUND);
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            throw new BusinessException(BizCode.FORBIDDEN, "无权操作该订单");

        int newStatus = switch (eventType) {
            case "order.confirmed" -> 2;
            case "order.shipped" -> 3;
            case "order.delivered" -> 4;
            case "order.completed" -> 5;
            case "order.cancelled" -> 6;
            case "order.refunded" -> 8;
            default -> order.getStatus();
        };
        order.setStatus(newStatus);
        bnplOrderMapper.updateById(order);
        return Result.success("状态已更新: " + eventType, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> handleWebhook(String provider, String eventType,
            String payload, String signature, String secret, Long tenantId) {

        BnplProvider bnpl = bnplProviderRouter.get(provider);
        WebhookResult wr = bnpl.handleWebhook(eventType, payload, signature, secret);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider);
        result.put("signatureValid", wr.isSignatureValid());
        result.put("replayDetected", wr.isReplayDetected());
        result.put("eventType", wr.getEventType());

        if (!wr.isSignatureValid()) {
            result.put("errorCode", wr.getErrorCode());
            result.put("errorMsg", wr.getErrorMsg());
            return Result.error(BizCode.PERMISSION_DENIED,
                    "Webhook 验签失败: " + wr.getErrorMsg());
        }

        if (wr.getNewStatus() == null) {
            result.put("statusUpdate", "IGNORED");
            result.put("reason", "未知事件类型: " + eventType);
            return Result.success(result);
        }

        if (wr.getProviderOrderNo() != null) {
            // 按 providerOrderNo 查找本地订单并更新
            LambdaQueryWrapper<BnplOrder> q = new LambdaQueryWrapper<>();
            q.eq(BnplOrder::getProviderOrderNo, wr.getProviderOrderNo());
            if (tenantId != null) q.eq(BnplOrder::getTenantId, tenantId);
            BnplOrder order = bnplOrderMapper.selectOne(q);
            if (order != null) {
                result.put("localOrderId", order.getId());
                result.put("previousStatus", order.getStatus());
                result.put("newStatus", wr.getNewStatus());
                order.setStatus(wr.getNewStatus());
                bnplOrderMapper.updateById(order);
                result.put("statusUpdate", "UPDATED");
            } else {
                result.put("statusUpdate", "ORDER_NOT_FOUND");
                result.put("providerOrderNo", wr.getProviderOrderNo());
                log.warn("[BNPL-WEBHOOK] Order not found by providerOrderNo={}", wr.getProviderOrderNo());
            }
        } else {
            result.put("statusUpdate", "NO_ORDER_REFERENCE");
        }

        return Result.success(result);
    }

    @Override
    public Map<String, Boolean> listProviders() {
        return bnplProviderRouter.listProviders();
    }
}
