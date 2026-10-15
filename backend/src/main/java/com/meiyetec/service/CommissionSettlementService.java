package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CommissionRecord;
import com.meiyetec.entity.CommissionSettlement;
import com.meiyetec.entity.CommissionSettlementRecord;
import com.meiyetec.entity.Employee;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CommissionRecordMapper;
import com.meiyetec.mapper.CommissionSettlementMapper;
import com.meiyetec.mapper.CommissionSettlementRecordMapper;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommissionSettlementService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CommissionSettlementMapper settlementMapper;
    private final CommissionSettlementRecordMapper recordMapper;
    private final CommissionRecordMapper commRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final JdbcTemplate jdbc;

    @Transactional(rollbackFor = Exception.class)
    public CommissionSettlement generate(Long storeId, String salaryMonth) {
        // T07-D3：从 TenantContextHolder 取租户；缺失则拒绝，不修改现有方法签名
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        }
        String month = normalizeMonth(salaryMonth);

        CommissionSettlement existing = settlementMapper.selectOne(new LambdaQueryWrapper<CommissionSettlement>()
                .eq(CommissionSettlement::getStoreId, storeId)
                .eq(CommissionSettlement::getSalaryMonth, month));
        if (existing != null && existing.getStatus() != null && existing.getStatus() > 1) {
            throw new IllegalStateException("该月结算单已确认或已发放，不能重新生成");
        }

        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .ne(Employee::getStatus, 0)
                .orderByAsc(Employee::getId));

        CommissionSettlement settlement;
        if (existing != null) {
            settlement = existing;
            // P0-TEST-002: 物理删除旧 settlement_record（@TableLogic 逻辑删除会保留记录，与 uk_settlement_commission 冲突）
            jdbc.update("DELETE FROM commission_settlement_record WHERE settlement_id = ?", existing.getId());
        } else {
            settlement = new CommissionSettlement();
            settlement.setTenantId(tenantId);
            settlement.setStoreId(storeId);
            settlement.setSalaryMonth(month);
            settlement.setSettlementNo(generateSettlementNo(storeId, month));
            settlement.setEmployeeCount(0);
            settlement.setTotalCommission(BigDecimal.ZERO);
            settlement.setTotalAdjustment(BigDecimal.ZERO);
            settlement.setTotalPayable(BigDecimal.ZERO);
            settlement.setStatus(1);
            settlementMapper.insert(settlement);
        }

        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalAdjustment = BigDecimal.ZERO;
        BigDecimal totalPayable = BigDecimal.ZERO;

        for (Employee emp : employees) {
            BigDecimal commission = sumCommission(settlement.getStoreId(), emp.getId(), month);
            BigDecimal adjustment = sumAdjustment(settlement.getStoreId(), emp.getId(), month);
            BigDecimal payable = commission.add(adjustment);

            // P0-TEST-002: 取该员工第一条 commission_record.id 作为 commissionRecordId（满足 uk_settlement_commission 唯一约束）
            // 无 commission 记录的员工用 storeId*1000000+employeeId 作为占位（保证唯一）
            Long firstRecordId = firstCommissionRecordId(settlement.getStoreId(), emp.getId(), month);
            if (firstRecordId == null) {
                firstRecordId = settlement.getStoreId() * 1000000L + emp.getId();
            }

            CommissionSettlementRecord rec = new CommissionSettlementRecord();
            rec.setTenantId(tenantId);
            rec.setStoreId(settlement.getStoreId());
            rec.setSettlementId(settlement.getId());
            rec.setCommissionRecordId(firstRecordId);
            rec.setEmployeeId(emp.getId());
            rec.setEmployeeName(emp.getEmployeeName());
            rec.setBaseSalary(emp.getSalary() == null ? BigDecimal.ZERO : emp.getSalary());
            rec.setCommissionAmount(commission);
            rec.setAdjustmentAmount(adjustment);
            rec.setPayableAmount(payable);
            recordMapper.insert(rec);

            totalCommission = totalCommission.add(commission);
            totalAdjustment = totalAdjustment.add(adjustment);
            totalPayable = totalPayable.add(payable);
        }

        settlement.setEmployeeCount(employees.size());
        settlement.setTotalCommission(totalCommission);
        settlement.setTotalAdjustment(totalAdjustment);
        settlement.setTotalPayable(totalPayable);
        settlement.setStatus(1);
        settlementMapper.updateById(settlement);

        return settlement;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommissionSettlement confirm(Long settlementId, Long operatorId) {
        CommissionSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) throw new IllegalArgumentException("结算单不存在");
        if (settlement.getStatus() == null || settlement.getStatus() != 1) {
            throw new IllegalStateException("只能确认待确认状态的结算单");
        }
        settlement.setStatus(2);
        settlement.setConfirmTime(LocalDateTime.now());
        settlement.setConfirmBy(operatorId);
        settlementMapper.updateById(settlement);
        return settlement;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommissionSettlement pay(Long settlementId, Long operatorId) {
        CommissionSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) throw new IllegalArgumentException("结算单不存在");
        if (settlement.getStatus() == null || settlement.getStatus() != 2) {
            throw new IllegalStateException("只能发放已确认的结算单");
        }
        settlement.setStatus(3);
        settlement.setPayTime(LocalDateTime.now());
        settlement.setPayBy(operatorId);
        settlementMapper.updateById(settlement);

        jdbc.update("UPDATE commission_record SET settlement_id=? WHERE deleted=0 AND store_id=? AND salary_month=?",
                settlementId, settlement.getStoreId(), settlement.getSalaryMonth());
        return settlement;
    }

    public CommissionSettlement getById(Long id) {
        return settlementMapper.selectById(id);
    }

    public List<Map<String, Object>> getRecords(Long settlementId) {
        return jdbc.queryForList(
                "SELECT r.*, e.employee_code FROM commission_settlement_record r " +
                "LEFT JOIN pos_employee e ON r.employee_id=e.id " +
                "WHERE r.settlement_id=? ORDER BY r.employee_id",
                settlementId);
    }

    private BigDecimal sumCommission(Long storeId, Long employeeId, String salaryMonth) {
        Map<String, Object> sum = jdbc.queryForMap(
                "SELECT COALESCE(SUM(commission_amount),0) total FROM commission_record " +
                "WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=?",
                storeId, employeeId, salaryMonth);
        return decimal(sum.get("total"));
    }

    /**
     * P0-TEST-002: 取某员工某月第一条 commission_record.id（满足 settlement_record 唯一约束）
     */
    private Long firstCommissionRecordId(Long storeId, Long employeeId, String salaryMonth) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM commission_record " +
                "WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=? " +
                "ORDER BY id ASC LIMIT 1",
                Long.class, storeId, employeeId, salaryMonth);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private BigDecimal sumAdjustment(Long storeId, Long employeeId, String salaryMonth) {
        Map<String, Object> sum = jdbc.queryForMap(
                "SELECT COALESCE(SUM(adjustment_amount),0) total FROM commission_salary " +
                "WHERE deleted=0 AND status=1 AND store_id=? AND employee_id=? AND salary_month=?",
                storeId, employeeId, salaryMonth);
        return decimal(sum.get("total"));
    }

    private String generateSettlementNo(Long storeId, String salaryMonth) {
        return "CS" + salaryMonth.replace("-", "") + String.format("%03d", storeId) +
                String.format("%04d", (int)(Math.random() * 10000));
    }

    private String normalizeMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now().toString() : YearMonth.parse(month).toString();
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
