package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.LevelEvaluation;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.mapper.LevelEvaluationMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.service.LevelEvaluationService;
import com.meiyetec.service.TechnicianLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelEvaluationServiceImpl extends ServiceImpl<LevelEvaluationMapper, LevelEvaluation>
        implements LevelEvaluationService {

    private final LevelEvaluationMapper levelEvaluationMapper;
    private final TechnicianLevelService technicianLevelService;
    private final EmployeeMapper employeeMapper;
    private final PosOrderMapper posOrderMapper;
    private final JdbcTemplate jdbc;

    @Override
    public List<LevelEvaluation> getByEmployeeId(Long employeeId) {
        return levelEvaluationMapper.findByEmployeeId(employeeId);
    }

    @Override
    public List<Map<String, Object>> queryRecords(Long employeeId, Long storeId, String status) {
        return levelEvaluationMapper.queryRecords(employeeId, storeId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LevelEvaluation evaluate(Long employeeId, Long operatorId, String operatorName, Long storeId) {
        Map<String, Object> metrics = calculateEmployeeMetrics(employeeId);
        if (metrics == null || metrics.isEmpty()) {
            throw new RuntimeException("未找到员工信息");
        }
        Long currentLevelId = (Long) metrics.get("currentLevelId");
        BigDecimal totalSales = (BigDecimal) metrics.getOrDefault("totalSales", BigDecimal.ZERO);
        BigDecimal ratingScore = (BigDecimal) metrics.getOrDefault("ratingScore", BigDecimal.ZERO);
        Integer serviceMonths = (Integer) metrics.getOrDefault("serviceMonths", 0);
        List<TechnicianLevel> allLevels = technicianLevelService.getActiveLevels();
        TechnicianLevel targetLevel = null;
        for (TechnicianLevel level : allLevels) {
            boolean salesOk = level.getPromotionSales() == null ||
                    totalSales.compareTo(level.getPromotionSales()) >= 0;
            boolean ratingOk = level.getPromotionRating() == null ||
                    ratingScore.compareTo(level.getPromotionRating()) >= 0;
            boolean monthsOk = level.getPromotionMonths() == null ||
                    serviceMonths >= level.getPromotionMonths();
            if (salesOk && ratingOk && monthsOk) {
                if (targetLevel == null || level.getSortOrder() < targetLevel.getSortOrder()) {
                    targetLevel = level;
                }
            }
        }
        if (targetLevel == null) {
            throw new RuntimeException("未找到合适的等级");
        }
        String result = "keep";
        if (currentLevelId == null) {
            result = "upgrade";
        } else {
            TechnicianLevel currentLevel = technicianLevelService.getById(currentLevelId);
            if (currentLevel == null || targetLevel.getSortOrder() < currentLevel.getSortOrder()) {
                result = "upgrade";
            } else if (targetLevel.getSortOrder() > currentLevel.getSortOrder()) {
                result = "downgrade";
            }
        }
        LevelEvaluation evaluation = new LevelEvaluation();
        evaluation.setEmployeeId(employeeId);
        evaluation.setFromLevelId(currentLevelId);
        evaluation.setToLevelId(targetLevel.getId());
        evaluation.setEvaluationType("auto");
        evaluation.setResult(result);
        evaluation.setEvaluationDate(LocalDate.now());
        evaluation.setTotalSales(totalSales);
        evaluation.setRatingScore(ratingScore);
        evaluation.setServiceMonths(serviceMonths);
        evaluation.setOperatorId(operatorId);
        evaluation.setOperatorName(operatorName);
        evaluation.setStatus("active");
        evaluation.setEffectiveTime(LocalDateTime.now());
        evaluation.setStoreId(storeId);
        save(evaluation);

        // M08: 写入历史后，回写员工当前等级 (upgrade / downgrade 时)
        Employee employee = employeeMapper.selectById(employeeId);
        if (!"keep".equals(result) && employee != null && targetLevel != null
                && !targetLevel.getId().equals(currentLevelId)) {
            employee.setEmployeeLevel(targetLevel.getId());
            employeeMapper.updateById(employee);
        }
        return evaluation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LevelEvaluation adjustLevel(Long employeeId, Long toLevelId, String note, Long operatorId, String operatorName, Long storeId) {
        TechnicianLevel targetLevel = technicianLevelService.getById(toLevelId);
        if (targetLevel == null) {
            throw new RuntimeException("目标等级不存在");
        }
        Map<String, Object> metrics = calculateEmployeeMetrics(employeeId);
        Long currentLevelId = (Long) metrics.get("currentLevelId");
        LevelEvaluation evaluation = new LevelEvaluation();
        evaluation.setEmployeeId(employeeId);
        evaluation.setFromLevelId(currentLevelId);
        evaluation.setToLevelId(toLevelId);
        evaluation.setEvaluationType("manual");
        evaluation.setResult("keep");
        evaluation.setEvaluationDate(LocalDate.now());
        evaluation.setEvaluationNote(note);
        evaluation.setOperatorId(operatorId);
        evaluation.setOperatorName(operatorName);
        evaluation.setStatus("active");
        evaluation.setEffectiveTime(LocalDateTime.now());
        evaluation.setStoreId(storeId);
        save(evaluation);
        return evaluation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revert(Long evaluationId) {
        LevelEvaluation evaluation = getById(evaluationId);
        if (evaluation == null) {
            throw new RuntimeException("评定记录不存在");
        }
        evaluation.setStatus("reverted");
        return updateById(evaluation);
    }

    @Override
    public Map<String, Object> calculateEmployeeMetrics(Long employeeId) {
        Map<String, Object> metrics = new HashMap<>();

        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            return metrics;
        }

        // 1. 当前等级
        Long currentLevelId = employee.getEmployeeLevel();
        metrics.put("currentLevelId", currentLevelId);

        // 2. 服务月数（从入职日期算到今天）
        Integer serviceMonths = 0;
        if (employee.getEntryDate() != null) {
            long months = ChronoUnit.MONTHS.between(
                    employee.getEntryDate().atStartOfDay(),
                    LocalDate.now().atStartOfDay());
            serviceMonths = (int) Math.max(0, months);
        }
        metrics.put("serviceMonths", serviceMonths);

        // 3. 业绩 (累计已完成订单 final_amount，按 pos_order_item.employee_id 过滤)
        BigDecimal totalSales = BigDecimal.ZERO;
        try {
            Long tenantId = employee.getTenantId();
            // 用 jdbc 直查更清晰（pos_order_item 表名）
            Long t = tenantId;
            Number sum = (t == null)
                    ? jdbc.queryForObject(
                        "SELECT COALESCE(SUM(oi.final_price),0) FROM pos_order_item oi " +
                        "JOIN pos_order o ON oi.order_id = o.id " +
                        "WHERE oi.employee_id = ? AND o.status = 2 AND o.deleted = 0 AND oi.deleted = 0",
                        Number.class, employeeId)
                    : jdbc.queryForObject(
                        "SELECT COALESCE(SUM(oi.final_price),0) FROM pos_order_item oi " +
                        "JOIN pos_order o ON oi.order_id = o.id " +
                        "WHERE oi.employee_id = ? AND o.tenant_id = ? AND o.status = 2 AND o.deleted = 0 AND oi.deleted = 0",
                        Number.class, employeeId, t);
            if (sum != null) totalSales = new BigDecimal(sum.toString());
        } catch (Exception e) {
            log.warn("[M08] calculate totalSales failed for employee={}: {}", employeeId, e.getMessage());
        }
        metrics.put("totalSales", totalSales);

        // 4. 好评率：取自 booking 完成态的评价（如表存在）；无数据回落 0
        BigDecimal ratingScore = BigDecimal.ZERO;
        try {
            Number avg = jdbc.queryForObject(
                "SELECT COALESCE(AVG(rating), 0) FROM booking_review " +
                "WHERE employee_id = ? AND deleted = 0",
                Number.class, employeeId);
            // 好评率 = (rating/5) * 100
            if (avg != null && avg.doubleValue() > 0) {
                ratingScore = BigDecimal.valueOf(Math.round(avg.doubleValue() / 5.0 * 1000.0) / 10.0);
            }
        } catch (Exception e) {
            // booking_review 不存在时静默回落
            ratingScore = new BigDecimal("85.0");
        }
        metrics.put("ratingScore", ratingScore);

        // 5. 投诉率：complaint_ticket 关联此员工的工单 / 完成订单数
        BigDecimal complaintRate = BigDecimal.ZERO;
        try {
            Number complaints = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_complaint_ticket WHERE handler_id = ? AND deleted = 0",
                Number.class, employeeId);
            Number totalOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_order WHERE status = 2 AND deleted = 0 " +
                "AND EXISTS (SELECT 1 FROM pos_order_item WHERE order_id = pos_order.id AND employee_id = ? AND deleted = 0)",
                Number.class, employeeId);
            long c = complaints == null ? 0 : complaints.longValue();
            long tot = totalOrders == null ? 0 : totalOrders.longValue();
            if (tot > 0) {
                complaintRate = BigDecimal.valueOf(Math.round((c * 100.0 / tot) * 10.0) / 10.0);
            }
        } catch (Exception e) {
            log.warn("[M08] calculate complaintRate failed for employee={}: {}", employeeId, e.getMessage());
        }
        metrics.put("complaintRate", complaintRate);

        return metrics;
    }
}

