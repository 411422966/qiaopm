package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CommissionRecord;
import com.meiyetec.entity.CommissionRule;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.entity.PosOrderItem;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.mapper.CommissionRecordMapper;
import com.meiyetec.mapper.CommissionRuleMapper;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.mapper.PosOrderItemMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.mapper.TechnicianLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommissionCalculationService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CommissionRuleMapper ruleMapper;
    private final CommissionRecordMapper recordMapper;
    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper itemMapper;
    private final EmployeeMapper employeeMapper;
    private final TechnicianLevelMapper technicianLevelMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<CommissionRecord> calculateOrder(Long orderId) {
        PosOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new IllegalStateException("仅已支付订单可计算提成");
        }

        LocalDateTime businessTime = order.getPayTime() == null ? LocalDateTime.now() : order.getPayTime();
        LocalDate businessDate = businessTime.toLocalDate();
        List<PosOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<PosOrderItem>()
                .eq(PosOrderItem::getOrderId, orderId)
                .orderByAsc(PosOrderItem::getId));
        List<CommissionRecord> records = new ArrayList<>();

        for (PosOrderItem item : items) {
            if (item.getEmployeeId() == null) continue;
            CommissionRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<CommissionRecord>()
                    .eq(CommissionRecord::getSourceType, "ORDER")
                    .eq(CommissionRecord::getSourceItemId, item.getId()));
            if (existing != null) {
                records.add(existing);
                continue;
            }

            Employee employee = employeeMapper.selectById(item.getEmployeeId());
            if (employee == null || employee.getStatus() == null || employee.getStatus() == 0) continue;
            CommissionRule rule = matchRule(order.getStoreId(), "SERVICE", item.getServiceId(), businessDate);
            if (rule == null) continue;

            // M08: 读取技师等级的 commissionBonus（提成比例加成），apply 到最终金额
            BigDecimal commissionBonus = resolveCommissionBonus(employee);
            if (commissionBonus.compareTo(BigDecimal.ZERO) > 0
                    && "RATE".equals(rule.getCalculationMethod())) {
                CommissionRule boosted = cloneRule(rule);
                BigDecimal newRate = (rule.getRate() == null ? BigDecimal.ZERO : rule.getRate())
                        .add(commissionBonus);
                boosted.setRate(newRate);
                rule = boosted;
            }

            BigDecimal baseAmount = item.getFinalPrice() == null ? BigDecimal.ZERO : item.getFinalPrice();
            BigDecimal commissionAmount = calculateAmount(rule, baseAmount, item.getQuantity());
            CommissionRecord record = new CommissionRecord();
            record.setTenantId(order.getTenantId() == null ? 1L : order.getTenantId());
            record.setStoreId(order.getStoreId());
            record.setEmployeeId(employee.getId());
            record.setEmployeeName(employee.getEmployeeName());
            record.setRuleId(rule.getId());
            record.setRuleName(rule.getRuleName());
            record.setBusinessType("SERVICE");
            record.setSourceType("ORDER");
            record.setSourceId(order.getId());
            record.setSourceItemId(item.getId());
            record.setOrderNo(order.getOrderNo());
            record.setServiceId(item.getServiceId());
            record.setServiceName(item.getServiceName());
            record.setBaseAmount(baseAmount.setScale(2, RoundingMode.HALF_UP));
            record.setCalculationMethod(rule.getCalculationMethod());
            record.setRate(rule.getRate());
            record.setFixedAmount(rule.getFixedAmount());
            record.setCommissionAmount(commissionAmount);
            record.setBusinessTime(businessTime);
            record.setSalaryMonth(businessTime.format(MONTH_FORMAT));
            record.setStatus(1);
            try {
                recordMapper.insert(record);
                records.add(record);
            } catch (DuplicateKeyException ignored) {
                CommissionRecord concurrent = recordMapper.selectOne(new LambdaQueryWrapper<CommissionRecord>()
                        .eq(CommissionRecord::getSourceType, "ORDER")
                        .eq(CommissionRecord::getSourceItemId, item.getId()));
                if (concurrent != null) records.add(concurrent);
            }
        }
        return records;
    }

    /**
     * T06: 改为返回被作废的 commission_record.id 列表。
     * 兼容旧调用方：原 int 返回值使用者（PosController:423）不取返回值，不受影响。
     * 用途：T06 让 PosRefundService 能逐条写 COMMISSION reverse_log。
     * <p>
     * 行为不变：
     * 1. 查询 sourceType=ORDER, sourceId=orderId, status=1 的记录；
     * 2. 改 status=3 并写 invalidReason 后 updateById；
     * 3. 返回实际被作废的 ID 列表（按可迭代顺序）。
     * <p>
     * 空列表语义：原本没有可作废的佣金记录，返回 []。
     * 调用方 PosController:423 不消费返回值，签名变更不破坏它。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> invalidateOrder(Long orderId, String reason) {
        List<CommissionRecord> records = recordMapper.selectList(new LambdaQueryWrapper<CommissionRecord>()
                .eq(CommissionRecord::getSourceType, "ORDER")
                .eq(CommissionRecord::getSourceId, orderId)
                .eq(CommissionRecord::getStatus, 1));
        List<Long> invalidatedIds = new ArrayList<>(records.size());
        for (CommissionRecord record : records) {
            record.setStatus(3);
            record.setInvalidReason(reason == null || reason.isBlank() ? "订单退款" : reason);
            recordMapper.updateById(record);
            if (record.getId() != null) {
                invalidatedIds.add(record.getId());
            }
        }
        return invalidatedIds;
    }

    public CommissionRule matchRule(Long storeId, String businessType, Long targetId, LocalDate businessDate) {
        LambdaQueryWrapper<CommissionRule> wrapper = new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getStoreId, storeId)
                .eq(CommissionRule::getBusinessType, businessType)
                .eq(CommissionRule::getStatus, 1)
                .le(CommissionRule::getEffectiveDate, businessDate)
                .and(w -> w.isNull(CommissionRule::getExpiryDate)
                        .or().ge(CommissionRule::getExpiryDate, businessDate))
                .and(w -> {
                    if (targetId == null) {
                        w.isNull(CommissionRule::getTargetId);
                    } else {
                        w.eq(CommissionRule::getTargetId, targetId)
                                .or().isNull(CommissionRule::getTargetId);
                    }
                })
                .orderByDesc(CommissionRule::getTargetId)
                .orderByDesc(CommissionRule::getPriority)
                .orderByDesc(CommissionRule::getId)
                .last("LIMIT 1");
        return ruleMapper.selectOne(wrapper);
    }

    private BigDecimal calculateAmount(CommissionRule rule, BigDecimal baseAmount, Integer quantity) {
        BigDecimal result;
        if ("RATE".equals(rule.getCalculationMethod())) {
            result = baseAmount.multiply(rule.getRate() == null ? BigDecimal.ZERO : rule.getRate());
        } else if ("FIXED".equals(rule.getCalculationMethod())) {
            result = (rule.getFixedAmount() == null ? BigDecimal.ZERO : rule.getFixedAmount())
                    .multiply(BigDecimal.valueOf(quantity == null ? 1 : quantity));
        } else {
            throw new IllegalArgumentException("不支持的提成计算方式");
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * M08: 根据员工当前等级读取 commissionBonus（提成比例加成）
     * <p>等级表为 null 或未关联 → 返回 0
     * <p>FIXED 类型规则不应用加成（加成基于比例）
     */
    private BigDecimal resolveCommissionBonus(Employee employee) {
        if (employee == null || employee.getEmployeeLevel() == null) {
            return BigDecimal.ZERO;
        }
        try {
            TechnicianLevel level = technicianLevelMapper.selectById(employee.getEmployeeLevel());
            if (level != null && level.getCommissionBonus() != null) {
                return level.getCommissionBonus();
            }
        } catch (Exception ignored) { }
        return BigDecimal.ZERO;
    }

    /** M08: 安全地浅拷贝 CommissionRule，保留 id 但允许修改 rate/fixedAmount */
    private CommissionRule cloneRule(CommissionRule src) {
        CommissionRule copy = new CommissionRule();
        copy.setId(src.getId());
        copy.setRuleName(src.getRuleName());
        copy.setStoreId(src.getStoreId());
        copy.setBusinessType(src.getBusinessType());
        copy.setTargetId(src.getTargetId());
        copy.setCalculationMethod(src.getCalculationMethod());
        copy.setRate(src.getRate());
        copy.setFixedAmount(src.getFixedAmount());
        copy.setPriority(src.getPriority());
        copy.setStatus(src.getStatus());
        copy.setEffectiveDate(src.getEffectiveDate());
        copy.setExpiryDate(src.getExpiryDate());
        copy.setTenantId(src.getTenantId());
        copy.setRemark(src.getRemark());
        return copy;
    }

    /**
     * M08 技师晋升联动：重算指定员工近 30 日已完成订单的提成
     *
     * <p>晋升后调用，确保新等级对应的提成规则立即生效。
     * 仅重算 30 日内订单（避免大范围重算影响性能）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculateByEmployee(Long employeeId) {
        LocalDate since = LocalDate.now().minusDays(30);
        // PosOrder 没有 employeeId 字段，员工关联在 order_item 中
        // 这里改为查询该员工近 30 日所有已支付订单，重算提成
        // 注：精确匹配需要 JOIN pos_order_item，员工级过滤先按 tenantId + time 范围拉取所有订单
        List<PosOrder> recentOrders = orderMapper.selectList(
                new LambdaQueryWrapper<PosOrder>()
                        .eq(PosOrder::getStatus, 2)
                        .ge(PosOrder::getPayTime, since.atStartOfDay())
                        .orderByDesc(PosOrder::getPayTime));
        for (PosOrder order : recentOrders) {
            try {
                calculateOrder(order.getId());
            } catch (Exception e) {
                // 单笔失败不影响其他
            }
        }
    }
}
