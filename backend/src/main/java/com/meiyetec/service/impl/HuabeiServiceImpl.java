package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.HuabeiOrder;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.HuabeiOrderMapper;
import com.meiyetec.service.HuabeiService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HuabeiServiceImpl implements HuabeiService {

    private final HuabeiOrderMapper huabeiOrderMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public Result<Map<String, Object>> queryCreditScore(Long memberId, String phone, Long tenantId) {
        if (phone == null || phone.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "手机号必填");
        }

        int score = 350 + Math.abs(phone.hashCode() % 600);
        score = Math.min(score, 950);

        BigDecimal creditLimit = BigDecimal.valueOf(score).multiply(BigDecimal.valueOf(100));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", memberId);
        data.put("phone", phone);
        data.put("zmScore", score);
        data.put("creditLevel", scoreLevel(score));
        data.put("creditLimit", creditLimit);
        data.put("usedLimit", BigDecimal.ZERO);
        data.put("availableLimit", creditLimit);
        data.put("queryTime", LocalDateTime.now().toString());

        return Result.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HuabeiOrder> createInstallmentOrder(Long memberId, String bizOrderNo,
            BigDecimal amount, Integer period, Long tenantId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "金额必须大于 0");
        }
        if (period == null || !List.of(3, 6, 12).contains(period)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "分期期数必须是 3/6/12");
        }

        Map<String, Object> member = jdbc.queryForMap(
            "SELECT name, phone FROM crm_member WHERE id = ? AND deleted = 0", memberId);
        String memberName = (String) member.get("name");
        String memberPhone = (String) member.get("phone");

        var creditResult = queryCreditScore(memberId, memberPhone, tenantId);
        Map<String, Object> credit = (Map<String, Object>) creditResult.getData();
        int score = (Integer) credit.get("zmScore");
        BigDecimal creditLimit = (BigDecimal) credit.get("creditLimit");

        if (amount.compareTo(creditLimit) > 0) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "超出授信额度（" + creditLimit + "元），当前芝麻信用分=" + score);
        }

        BigDecimal rate = switch (period) {
            case 3 -> BigDecimal.valueOf(0.023);
            case 6 -> BigDecimal.valueOf(0.045);
            case 12 -> BigDecimal.valueOf(0.088);
            default -> BigDecimal.ZERO;
        };
        BigDecimal fee = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = amount.add(fee);
        BigDecimal monthlyPayment = total.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);

        HuabeiOrder order = new HuabeiOrder();
        order.setTenantId(tenantId == null ? 1L : tenantId);
        order.setBizOrderNo(bizOrderNo);
        order.setMemberId(memberId);
        order.setMemberName(memberName);
        order.setMemberPhone(memberPhone);
        order.setZmScore(score);
        order.setCreditLimit(creditLimit);
        order.setUsedLimit(BigDecimal.ZERO);
        order.setInstallmentPeriod(period);
        order.setInstallmentRate(rate);
        order.setMonthlyPayment(monthlyPayment);
        order.setAmount(amount);
        order.setFee(fee);
        order.setNetAmount(amount);
        order.setStatus(1);
        order.setFirstRepayDate(LocalDate.now().plusMonths(1));
        order.setRemark("花呗分期-" + period + "期");

        huabeiOrderMapper.insert(order);
        return Result.success("分期申请已创建", order);
    }

    @Override
    public Result<Page<HuabeiOrder>> listOrders(Long memberId, Integer status,
            long current, long size, Long tenantId) {
        LambdaQueryWrapper<HuabeiOrder> q = new LambdaQueryWrapper<>();
        if (tenantId != null) q.eq(HuabeiOrder::getTenantId, tenantId);
        if (memberId != null) q.eq(HuabeiOrder::getMemberId, memberId);
        if (status != null) q.eq(HuabeiOrder::getStatus, status);
        q.orderByDesc(HuabeiOrder::getCreateTime);

        Page<HuabeiOrder> page = new Page<>(current, size);
        Page<HuabeiOrder> result = huabeiOrderMapper.selectPage(page, q);
        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancel(Long orderId, Long tenantId) {
        HuabeiOrder order = huabeiOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(BizCode.NOT_FOUND);
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            throw new BusinessException(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) {
            throw new BusinessException(BizCode.BAD_REQUEST, "仅待授权状态可取消");
        }
        order.setStatus(4);
        huabeiOrderMapper.updateById(order);
        return Result.success("已取消", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> simulateNotify(Long orderId, String status, Long tenantId) {
        HuabeiOrder order = huabeiOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(BizCode.NOT_FOUND);
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            throw new BusinessException(BizCode.FORBIDDEN, "无权操作该订单");

        int newStatus = switch (status) {
            case "paid" -> 3;
            case "cancelled" -> 4;
            case "overdue" -> 5;
            case "settled" -> 6;
            default -> order.getStatus();
        };
        order.setStatus(newStatus);
        huabeiOrderMapper.updateById(order);
        return Result.success("状态已更新: " + status, null);
    }

    @Override
    public Result<HuabeiOrder> preAuth(Long memberId, String bizOrderNo,
            BigDecimal amount, Integer period, Long tenantId) {
        return createInstallmentOrder(memberId, bizOrderNo, amount, period, tenantId);
    }

    private String scoreLevel(int score) {
        if (score >= 900) return "极好";
        if (score >= 700) return "优秀";
        if (score >= 650) return "良好";
        if (score >= 600) return "中等";
        if (score >= 550) return "较差";
        return "差";
    }
}
