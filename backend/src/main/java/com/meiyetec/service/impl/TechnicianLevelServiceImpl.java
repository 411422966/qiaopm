package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.mapper.TechnicianLevelMapper;
import com.meiyetec.service.CommissionCalculationService;
import com.meiyetec.service.TechnicianLevelService;
import com.meiyetec.util.EmployeeMetricSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicianLevelServiceImpl extends ServiceImpl<TechnicianLevelMapper, TechnicianLevel>
        implements TechnicianLevelService {

    private final TechnicianLevelMapper technicianLevelMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    private CommissionCalculationService commissionService;

    @Override
    public List<TechnicianLevel> getActiveLevels() {
        return technicianLevelMapper.findActiveLevels();
    }

    @Override
    public TechnicianLevel getNextLevel(Integer currentSortOrder) {
        return technicianLevelMapper.findNextLevel(currentSortOrder);
    }

    @Override
    public List<Map<String, Object>> countEmployeesByLevel() {
        return technicianLevelMapper.countEmployeesByLevel();
    }

    @Override
    public boolean toggleStatus(Long id, Integer status) {
        TechnicianLevel level = getById(id);
        if (level == null) {
            throw new RuntimeException("等级不存在");
        }
        level.setStatus(status);
        return updateById(level);
    }

    // ====================================================================
    // M08 晋升机制
    // ====================================================================

    @Override
    public Map<String, Object> evaluatePromotion(Long employeeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employeeId);

        Map<String, Object> emp = jdbc.queryForList(
                "SELECT pe.id AS id, " +
                "COALESCE(e.real_name, pe.employee_name) AS employee_name, " +
                "pe.employee_level, pe.entry_date, pe.create_time, pe.tenant_id, " +
                "COALESCE(tl.sort_order, 9999) AS sort_order, " +
                "tl.id AS level_id, tl.level_code, tl.level_name " +
                "FROM pos_employee pe " +
                "LEFT JOIN sys_user e ON e.phone = pe.phone AND e.deleted = 0 " +
                "LEFT JOIN biz_technician_level tl ON tl.id = pe.employee_level AND tl.deleted = 0 " +
                "WHERE pe.id = ? AND pe.deleted = 0", employeeId)
                .stream().findFirst().orElse(null);
        if (emp == null) {
            result.put("error", "员工不存在");
            return result;
        }
        result.put("employeeName", emp.get("employee_name"));
        result.put("currentLevelId", emp.get("level_id"));
        result.put("currentLevelCode", emp.get("level_code"));
        result.put("currentLevelName", emp.get("level_name"));

        // 员工所属租户（好评率租户隔离用；pos_employee.tenant_id 允许为空 → 不做租户过滤）
        Long employeeTenantId = emp.get("tenant_id") == null ? null
                : ((Number) emp.get("tenant_id")).longValue();
        // 入职日期 / 建档时间（服务月数口径：entry_date 优先，缺失回落 create_time）
        LocalDate entryDate = ToLocalDate(emp.get("entry_date"));
        LocalDateTime createTime = ToLocalDateTime(emp.get("create_time"));

        Integer currentSort = ((Number) emp.get("sort_order")).intValue();
        TechnicianLevel nextLevel = getNextLevel(currentSort);
        if (nextLevel == null) {
            result.put("nextLevel", null);
            result.put("eligible", false);
            result.put("reason", "已是最高等级，无需晋升");
            return result;
        }
        result.put("nextLevelCode", nextLevel.getLevelCode());
        result.put("nextLevelName", nextLevel.getLevelName());
        result.put("nextLevelId", nextLevel.getId());

        // 条件1: 累计业绩（员工业绩挂在 pos_order_item.employee_id，pos_order 本身无 employee_id 列）
        BigDecimal totalSales;
        try {
            totalSales = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(oi.final_price), 0) FROM pos_order_item oi " +
                    "JOIN pos_order o ON o.id = oi.order_id AND o.deleted = 0 AND o.status = 2 " +
                    "WHERE oi.deleted = 0 AND oi.employee_id = ? " +
                    (employeeTenantId == null ? "" : "AND o.tenant_id = ? "),
                    BigDecimal.class,
                    employeeTenantId == null
                            ? new Object[]{employeeId}
                            : new Object[]{employeeId, employeeTenantId});
        } catch (Exception e) {
            totalSales = BigDecimal.ZERO;
            log.warn("[M08] calculate totalSales failed for employee={}: {}", employeeId, e.getMessage());
        }
        BigDecimal requiredSales = nextLevel.getPromotionSales() == null
                ? BigDecimal.ZERO : nextLevel.getPromotionSales();
        boolean salesOk = totalSales.compareTo(requiredSales) >= 0;
        result.put("totalSales", totalSales);
        result.put("requiredSales", requiredSales);
        result.put("salesOk", salesOk);

        // 条件2: 好评率（统一走 EmployeeMetricSupport：真实客户评价表 crm_review，
        //         无评价数据一律记 0，无任何硬编码兜底；与评定记录页同口径）
        BigDecimal ratingRate;
        try {
            ratingRate = EmployeeMetricSupport.CalculateRatingScore(jdbc, employeeId, employeeTenantId);
        } catch (Exception e) {
            ratingRate = BigDecimal.ZERO;
            log.warn("[M08] calculate ratingRate failed for employee={}: {}", employeeId, e.getMessage());
        }
        BigDecimal requiredRating = nextLevel.getPromotionRating() == null
                ? BigDecimal.ZERO : nextLevel.getPromotionRating();
        boolean ratingOk = ratingRate.compareTo(requiredRating) >= 0;
        result.put("ratingRate", ratingRate);
        result.put("requiredRating", requiredRating);
        result.put("ratingOk", ratingOk);

        // 条件3: 服务月数（入职日期 entry_date 优先，缺失回落建档时间 create_time，与评定记录页同口径）
        long months = EmployeeMetricSupport.CalculateServiceMonths(entryDate, createTime);
        Integer requiredMonths = nextLevel.getPromotionMonths() == null ? 0 : nextLevel.getPromotionMonths();
        boolean monthsOk = months >= requiredMonths;
        result.put("monthsOfService", months);
        result.put("requiredMonths", requiredMonths);
        result.put("monthsOk", monthsOk);

        boolean eligible = salesOk && ratingOk && monthsOk;
        result.put("eligible", eligible);
        result.put("reason", eligible ? "满足全部晋升条件" : "未满足全部条件");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> promote(Long employeeId, Long operatorId, String note) {
        Map<String, Object> evaluation = evaluatePromotion(employeeId);
        if (Boolean.FALSE.equals(evaluation.get("eligible"))) {
            throw new RuntimeException("不满足晋升条件：" + evaluation.get("reason"));
        }

        Long nextLevelId = (Long) evaluation.get("nextLevelId");
        String nextLevelName = (String) evaluation.get("nextLevelName");
        Long fromLevelId = evaluation.get("currentLevelId") == null
                ? null : ((Number) evaluation.get("currentLevelId")).longValue();

        int updated = jdbc.update(
                "UPDATE pos_employee SET employee_level = ? WHERE id = ?",
                nextLevelId, employeeId);
        if (updated == 0) {
            throw new RuntimeException("员工等级更新失败");
        }

        // 记录晋升评定（写入 biz_level_evaluation，与 LevelEvaluationServiceImpl 共用同一张评定表）
        String evaluationLogError = null;
        try {
            jdbc.update(
                    "INSERT INTO biz_level_evaluation " +
                    "(employee_id, from_level_id, to_level_id, evaluation_type, result, evaluation_date, " +
                    "total_sales, rating_score, service_months, complaint_rate, evaluation_note, " +
                    "operator_id, operator_name, status, effective_time, deleted) " +
                    "VALUES (?, ?, ?, ?, ?, CURDATE(), ?, ?, ?, NULL, ?, ?, NULL, ?, NOW(), 0)",
                    employeeId,
                    fromLevelId,
                    nextLevelId,
                    "manual",
                    "upgrade",
                    evaluation.get("totalSales"),
                    evaluation.get("ratingRate"),
                    evaluation.get("monthsOfService"),
                    note == null ? "" : note,
                    operatorId,
                    "active");
        } catch (Exception e) {
            // 落库失败不阻断晋升，但必须把错误暴露出来（此前是完全静默的假成功）
            evaluationLogError = e.getMessage();
        }

        // 联动提成重算
        try {
            commissionService.recalculateByEmployee(employeeId);
        } catch (Exception e) {
            // 提成重算失败不影响晋升
        }

        Map<String, Object> result = new LinkedHashMap<>(evaluation);
        result.put("promoted", true);
        result.put("message", "晋升成功：" + evaluation.get("currentLevelName") + " → " + nextLevelName);
        if (evaluationLogError != null) {
            result.put("evaluationLogError", evaluationLogError);
        }
        return result;
    }

    /**
     * 把 JDBC 返回的日期列（java.sql.Date / Timestamp / LocalDate）统一转成 LocalDate。
     */
    private static LocalDate ToLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).toLocalDate();
        }
        return null;
    }

    /**
     * 把 JDBC 返回的时间列（java.sql.Timestamp / LocalDateTime）统一转成 LocalDateTime。
     */
    private static LocalDateTime ToLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        return null;
    }
}
