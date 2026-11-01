package com.meiyetec.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技师等级评定指标的公共计算口径（M08）。
 *
 * <p>历史上 LevelEvaluationServiceImpl（评定记录页）与 TechnicianLevelServiceImpl（晋升评估页）
 * 各自实现了一套指标算法，出现口径漂移：好评率会退化成硬编码 85.0、服务月数在
 * entry_date 缺失时直接记 0。此处统一收敛为唯一实现。
 *
 * <p>好评率口径（无任何硬编码兜底，匹配不到评价一律记 0）：
 * <ul>
 *   <li>评价来源表 crm_review（deleted = 0，status = 2 已发布）；</li>
 *   <li>血缘①：source_type = 'ORDER' → source_id = pos_order.id → 该订单项的服务员工；</li>
 *   <li>血缘②：source_type = 'STAFF' → source_id = pos_employee.id（直接评价该员工）。</li>
 * </ul>
 *
 * <p>服务月数口径：以 pos_employee.entry_date 为准，缺失时回落 create_time，两者都缺失记 0。
 */
public final class EmployeeMetricSupport {

    private EmployeeMetricSupport() {
    }

    /**
     * 好评率（百分数，一位小数）：好评（overall_rating >= 4）数 / 总评价数 * 100。
     *
     * @param jdbc      数据访问
     * @param employeeId 被评员工 ID
     * @param tenantId  租户 ID，可为 null（表示不做租户过滤）
     * @return 无匹配评价时返回 0
     */
    public static BigDecimal CalculateRatingScore(JdbcTemplate jdbc, Long employeeId, Long tenantId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(DISTINCT r.id) AS total, " +
                "COUNT(DISTINCT CASE WHEN r.overall_rating >= 4 THEN r.id END) AS good " +
                "FROM crm_review r WHERE r.deleted = 0 AND r.status = 2 ");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            sql.append("AND r.tenant_id = ? ");
            args.add(tenantId);
        }
        sql.append(
                "AND ( (r.source_type = 'ORDER' AND EXISTS (" +
                "  SELECT 1 FROM pos_order o " +
                "  JOIN pos_order_item oi ON oi.order_id = o.id AND oi.deleted = 0 " +
                "  WHERE o.id = r.source_id AND o.deleted = 0 AND o.status = 2 " +
                "    AND o.tenant_id = r.tenant_id AND oi.employee_id = ?)) " +
                "OR (r.source_type = 'STAFF' AND r.source_id = ?) ) ");
        args.add(employeeId);
        args.add(employeeId);
        Map<String, Object> stats = jdbc.queryForMap(sql.toString(), args.toArray());
        long total = ((Number) stats.get("total")).longValue();
        long good = ((Number) stats.get("good")).longValue();
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.round(good * 1000.0 / total) / 10.0);
    }

    /**
     * 服务月数：以入职日期 entry_date 为准，缺失回落建档时间 create_time，两者都缺失记 0。
     */
    public static int CalculateServiceMonths(LocalDate entryDate, LocalDateTime createTime) {
        LocalDate joinDate = entryDate;
        if (joinDate == null && createTime != null) {
            joinDate = createTime.toLocalDate();
        }
        if (joinDate == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.MONTHS.between(joinDate, LocalDate.now()));
    }
}
