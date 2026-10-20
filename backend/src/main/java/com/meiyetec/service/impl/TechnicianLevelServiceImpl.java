package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.mapper.TechnicianLevelMapper;
import com.meiyetec.service.CommissionCalculationService;
import com.meiyetec.service.TechnicianLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                "SELECT e.id, e.name, e.employee_level, e.create_time, " +
                "COALESCE(tl.sort_order, 9999) AS sort_order, " +
                "tl.id AS level_id, tl.level_code, tl.level_name " +
                "FROM sys_user e " +
                "LEFT JOIN biz_technician_level tl ON tl.level_code = e.employee_level AND tl.deleted = 0 " +
                "WHERE e.id = ? AND e.deleted = 0", employeeId)
                .stream().findFirst().orElse(null);
        if (emp == null) {
            result.put("error", "员工不存在");
            return result;
        }
        result.put("employeeName", emp.get("name"));
        result.put("currentLevelCode", emp.get("level_code"));
        result.put("currentLevelName", emp.get("level_name"));

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

        // 条件1: 累计业绩
        BigDecimal totalSales = jdbc.queryForObject(
                "SELECT COALESCE(SUM(paid_amount), 0) FROM pos_order " +
                "WHERE employee_id = ? AND status = 2 AND deleted = 0",
                BigDecimal.class, employeeId);
        BigDecimal requiredSales = nextLevel.getPromotionSales() == null
                ? BigDecimal.ZERO : nextLevel.getPromotionSales();
        boolean salesOk = totalSales.compareTo(requiredSales) >= 0;
        result.put("totalSales", totalSales);
        result.put("requiredSales", requiredSales);
        result.put("salesOk", salesOk);

        // 条件2: 好评率
        Long totalOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_order WHERE employee_id = ? AND status = 2 AND deleted = 0",
                Long.class, employeeId);
        Long goodOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_order WHERE employee_id = ? AND status = 2 AND deleted = 0 AND rating >= 4",
                Long.class, employeeId);
        BigDecimal ratingRate = totalOrders != null && totalOrders > 0
                ? BigDecimal.valueOf(goodOrders * 100.0 / totalOrders).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal requiredRating = nextLevel.getPromotionRating() == null
                ? BigDecimal.ZERO : nextLevel.getPromotionRating();
        boolean ratingOk = ratingRate.compareTo(requiredRating) >= 0;
        result.put("ratingRate", ratingRate);
        result.put("requiredRating", requiredRating);
        result.put("ratingOk", ratingOk);

        // 条件3: 服务月数
        LocalDateTime joinDate = emp.get("create_time") == null ? null
                : ((java.sql.Timestamp) emp.get("create_time")).toLocalDateTime();
        long months = joinDate == null ? 0
                : ChronoUnit.MONTHS.between(joinDate.toLocalDate(), LocalDate.now());
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
        String nextLevelCode = (String) evaluation.get("nextLevelCode");
        String nextLevelName = (String) evaluation.get("nextLevelName");

        int updated = jdbc.update(
                "UPDATE pos_employee SET employee_level = ? WHERE id = ?",
                nextLevelId, employeeId);
        if (updated == 0) {
            throw new RuntimeException("员工等级更新失败");
        }

        // 记录晋升日志（表不存在则忽略）
        try {
            jdbc.update(
                    "INSERT INTO biz_technician_level_evaluation " +
                    "(employee_id, from_level, to_level, to_level_id, evaluation_note, operator_id, created_at, deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NOW(), 0)",
                    employeeId,
                    evaluation.get("currentLevelCode"),
                    nextLevelCode,
                    nextLevelId,
                    note == null ? "" : note,
                    operatorId);
        } catch (Exception e) {
            // 表不存在时静默跳过
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
        return result;
    }
}
