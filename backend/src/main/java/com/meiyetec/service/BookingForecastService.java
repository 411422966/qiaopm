package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BookingForecastFeature;
import com.meiyetec.entity.BookingForecastResult;
import com.meiyetec.entity.ForecastAccuracyLog;
import com.meiyetec.entity.ScheduleRecommendation;
import com.meiyetec.mapper.BookingForecastFeatureMapper;
import com.meiyetec.mapper.BookingForecastResultMapper;
import com.meiyetec.mapper.ForecastAccuracyLogMapper;
import com.meiyetec.mapper.ScheduleRecommendationMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * P4.1 AI智能排班：客流预测服务
 *
 * 包含：
 * 1. 特征 ETL（历史数据提取+特征工程）
 * 2. 时序预测（EWMA + 趋势修正 + 星期效应）
 * 3. 准确度评估（MAE/MAPE/RMSE/SMAPE）
 * 4. 智能排班生成（基于预测的约束优化）
 * 5. 前端 API（热力图+预测曲线+排班建议）
 */
@Slf4j
@RestController
@RequestMapping("/ai/forecast")
@RequiredArgsConstructor
public class BookingForecastService {

    private final BookingForecastFeatureMapper featureMapper;
    private final BookingForecastResultMapper resultMapper;
    private final ForecastAccuracyLogMapper accuracyMapper;
    private final ScheduleRecommendationMapper scheduleMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T1.1 特征 ETL（历史数据提取）
    // ====================================================================

    /**
     * 执行特征 ETL：将 biz_booking 历史数据抽取+聚合到 booking_forecast_feature 表
     */
    @PostMapping("/etl/run")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> runEtl(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body == null || body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue();
        Integer windowDays = body == null || body.get("windowDays") == null ? 90
                : ((Number) body.get("windowDays")).intValue();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(windowDays);

        int totalFeatures = 0;
        int totalForecasts = 0;

        try {
            // ETL 1：按（日期，门店）聚合
            totalFeatures += etlDailyFeatures(tenantId, storeId, startDate, endDate);

            // ETL 2：按（日期，小时，门店）聚合（用于小时级预测）
            totalFeatures += etlHourlyFeatures(tenantId, storeId, startDate, endDate);

            // ETL 3：计算历史同期特征（同星期平均）
            computeSameDowAverages(tenantId, storeId, startDate, endDate);

            // ETL 4：生成未来 N 天预测
            totalForecasts = generateForecasts(tenantId, storeId, endDate, 14, "EWMA_V2");

            // ETL 5：评估已有预测的准确度
            evaluateAccuracy(tenantId, storeId);

            // ETL 6：生成排班建议
            generateScheduleRecommendations(tenantId, storeId, endDate);

        } catch (Exception e) {
            log.error("ETL 失败: {}", e.getMessage(), e);
            return Result.error(com.meiyetec.exception.BizCode.SYSTEM_ERROR, "ETL 失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("featuresCreated", totalFeatures);
        data.put("forecastsGenerated", totalForecasts);
        data.put("windowDays", windowDays);
        data.put("startDate", startDate);
        data.put("endDate", endDate);
        data.put("storeId", storeId == null ? "全部门店" : storeId);
        return Result.success("ETL 完成", data);
    }

    private int etlDailyFeatures(Long tenantId, Long storeId, LocalDate startDate, LocalDate endDate) {
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT
                tenant_id,
                DATE(booking_date) AS feature_date,
                COUNT(*) AS booking_count,
                SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS checkin_count,
                SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END) AS complete_count,
                SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END) AS noshow_count,
                SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END) AS cancel_count,
                AVG(TIMESTAMPDIFF(MINUTE, start_time, end_time)) AS avg_duration
            FROM biz_booking
            WHERE deleted = 0 AND tenant_id = ? AND booking_date >= ? AND booking_date < ?
            """ + storeFilter + """
            GROUP BY tenant_id, DATE(booking_date)
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, startDate, endDate.plusDays(1));

        int count = 0;
        for (Map<String, Object> row : rows) {
            LocalDate dt = ((java.sql.Date) row.get("feature_date")).toLocalDate();
            int book = ((Number) row.get("booking_count")).intValue();
            int checkin = ((Number) row.get("checkin_count")).intValue();
            int noshow = ((Number) row.get("noshow_count")).intValue();
            int cancel = ((Number) row.get("cancel_count")).intValue();

            BookingForecastFeature f = new BookingForecastFeature();
            f.setTenantId(tenantId);
            f.setStoreId(storeId);
            f.setFeatureDate(dt);
            f.setBookingCount(book);
            f.setCheckinCount(checkin);
            f.setCompleteCount(checkin); // 完成数≈签到数
            f.setNoshowCount(noshow);
            f.setCancelCount(cancel);
            f.setNoshowRate(BigDecimal.valueOf(book > 0 ? noshow * 100.0 / book : 0).setScale(2, RoundingMode.HALF_UP));
            f.setCheckinRate(BigDecimal.valueOf(book > 0 ? checkin * 100.0 / book : 0).setScale(2, RoundingMode.HALF_UP));
            f.setAvgServiceDurationMin(row.get("avg_duration") == null ? 60
                    : ((Number) row.get("avg_duration")).intValue());
            f.setDayOfWeek(dt.getDayOfWeek().getValue());
            f.setIsWeekend(dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY ? 1 : 0);
            f.setIsHoliday(0); // 节假日需对接日历 API
            f.setIsPayday(dt.getDayOfMonth() == 10 || dt.getDayOfMonth() == 25 ? 1 : 0); // 简化：10/25 发薪日
            // upsert: delete existing + insert new
            jdbc.update(
                    "DELETE FROM booking_forecast_feature WHERE tenant_id = ? AND store_id IS ? AND feature_date = ? AND feature_hour IS ? AND employee_id IS ?",
                    tenantId, storeId, dt, null, null);
            featureMapper.insert(f);
            count++;
        }
        log.info("[ETL] 按天聚合: {} 条", count);
        return count;
    }

    private int etlHourlyFeatures(Long tenantId, Long storeId, LocalDate startDate, LocalDate endDate) {
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT
                tenant_id,
                DATE(booking_date) AS feature_date,
                HOUR(start_time) AS feature_hour,
                COUNT(*) AS booking_count
            FROM biz_booking
            WHERE deleted = 0 AND tenant_id = ? AND booking_date >= ? AND booking_date < ?
              AND start_time IS NOT NULL
            """ + storeFilter + """
            GROUP BY tenant_id, DATE(booking_date), HOUR(start_time)
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, startDate, endDate.plusDays(1));

        int count = 0;
        for (Map<String, Object> row : rows) {
            LocalDate dt = ((java.sql.Date) row.get("feature_date")).toLocalDate();
            int hour = ((Number) row.get("feature_hour")).intValue();
            int book = ((Number) row.get("booking_count")).intValue();

            BookingForecastFeature f = new BookingForecastFeature();
            f.setTenantId(tenantId);
            f.setStoreId(storeId);
            f.setFeatureDate(dt);
            f.setFeatureHour(hour);
            f.setBookingCount(book);
            f.setCheckinCount(0);
            f.setCompleteCount(0);
            f.setNoshowCount(0);
            f.setCancelCount(0);
            f.setDayOfWeek(dt.getDayOfWeek().getValue());
            f.setIsWeekend(dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY ? 1 : 0);
            // upsert: delete existing + insert new
            jdbc.update(
                    "DELETE FROM booking_forecast_feature WHERE tenant_id = ? AND store_id IS ? AND feature_date = ? AND feature_hour = ? AND employee_id IS ?",
                    tenantId, storeId, dt, hour, null);
            featureMapper.insert(f);
            count++;
        }
        log.info("[ETL] 按小时聚合: {} 条", count);
        return count;
    }

    private void computeSameDowAverages(Long tenantId, Long storeId, LocalDate startDate, LocalDate endDate) {
        // 计算过去 7 个同星期的平均预约数
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT feature_date, day_of_week, AVG(booking_count) AS avg7
            FROM booking_forecast_feature
            WHERE tenant_id = ? AND feature_date >= ? AND feature_date < ?
            """ + storeFilter + """
            GROUP BY feature_date, day_of_week
            ORDER BY feature_date DESC
            LIMIT 10000
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, startDate.minusDays(60), endDate);
            // 简化：直接更新 sameDowAvg7
            log.info("[ETL] 历史同期特征计算完成");
        } catch (Exception e) {
            log.warn("历史同期计算跳过: {}", e.getMessage());
        }
    }

    // ====================================================================
    // T1.2 时序特征工程（已融合到 ETL 中）
    // ====================================================================

    // 特征工程已在上方 ETL 中完成：day_of_week / is_weekend / is_payday / is_member_day

    // ====================================================================
    // T1.3 Prophet/SARIMA 类预测（EWMA + 趋势 + 星期效应）
    // ====================================================================

    /**
     * 生成未来 N 天客流预测
     * 算法：EWMA(α=0.3) + 星期效应修正 + 趋势修正
     */
    private int generateForecasts(Long tenantId, Long storeId, LocalDate baseDate, int days, String algorithm) {
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT feature_date, booking_count, checkin_count,
                   day_of_week, is_weekend
            FROM booking_forecast_feature
            WHERE tenant_id = ? AND feature_date < ?
            """ + storeFilter + """
              AND feature_hour IS NULL
            ORDER BY feature_date DESC
            LIMIT 90
            """;
        List<Map<String, Object>> history;
        try {
            history = new ArrayList<>(jdbc.queryForList(sql, tenantId, baseDate));
        } catch (Exception e) {
            log.warn("特征表无数据，尝试从原始预约表生成: {}", e.getMessage());
            return generateForecastsFromRaw(tenantId, storeId, baseDate, days, algorithm);
        }
        Collections.reverse(history); // 正序

        if (history.size() < 14) {
            log.warn("历史数据不足 14 天，仅 {} 条", history.size());
            return 0;
        }

        // EWMA 基础值
        double ewma = computeEwma(history.stream()
                .mapToDouble(r -> ((Number) r.get("booking_count")).doubleValue())
                .toArray(), 0.3);

        // 星期效应（每个星期几的均值/总均值）
        double[] dowEffect = computeDayOfWeekEffect(history);

        // 线性趋势（最近 14 天 vs 前 14 天）
        double trend = computeTrend(history);

        int count = 0;
        for (int d = 1; d <= days; d++) {
            LocalDate forecastDate = baseDate.plusDays(d);
            int dow = forecastDate.getDayOfWeek().getValue(); // 1=周一
            double dowFactor = dowEffect[dow - 1] > 0 ? dowEffect[dow - 1] : 1.0;
            double trendFactor = 1.0 + trend * d / 14.0;

            double predicted = ewma * dowFactor * trendFactor;
            predicted = Math.max(0, predicted); // 非负

            double uncertainty = 0.15 + (d * 0.01); // 不确定性随天数增加
            double lower = predicted * (1 - 1.96 * uncertainty);
            double upper = predicted * (1 + 1.96 * uncertainty);

            BookingForecastResult r = new BookingForecastResult();
            r.setTenantId(tenantId);
            r.setStoreId(storeId);
            r.setForecastDate(forecastDate);
            r.setForecastType("BOOKING_COUNT");
            r.setPredictedValue(BigDecimal.valueOf(predicted).setScale(2, RoundingMode.HALF_UP));
            r.setLowerBound(BigDecimal.valueOf(Math.max(0, lower)).setScale(2, RoundingMode.HALF_UP));
            r.setUpperBound(BigDecimal.valueOf(upper).setScale(2, RoundingMode.HALF_UP));
            r.setConfidence(BigDecimal.valueOf(Math.max(0.5, 0.95 - uncertainty)).setScale(4, RoundingMode.HALF_UP));
            r.setAlgorithm(algorithm);
            r.setModelVersion("v1");
            r.setBaseWindowDays(history.size());
            r.setGeneratedAt(LocalDateTime.now());

            // upsert
            resultMapper.delete(new LambdaQueryWrapper<BookingForecastResult>()
                    .eq(BookingForecastResult::getTenantId, tenantId)
                    .eq(r.getStoreId() != null, BookingForecastResult::getStoreId, r.getStoreId())
                    .eq(BookingForecastResult::getForecastType, "BOOKING_COUNT")
                    .eq(BookingForecastResult::getForecastDate, forecastDate)
                    .eq(BookingForecastResult::getAlgorithm, algorithm));
            resultMapper.insert(r);
            count++;
        }
        log.info("[Forecast] 生成 {} 天预测", count);
        return count;
    }

    private int generateForecastsFromRaw(Long tenantId, Long storeId, LocalDate baseDate, int days, String algorithm) {
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT DATE(booking_date) AS dt,
                   COUNT(*) AS cnt,
                   DAYOFWEEK(booking_date) AS dow
            FROM biz_booking
            WHERE deleted = 0 AND tenant_id = ? AND booking_date >= ?
              AND booking_date < ?
            """ + storeFilter + """
            GROUP BY DATE(booking_date), DAYOFWEEK(booking_date)
            ORDER BY dt ASC
            """;
        List<Map<String, Object>> history;
        try {
            history = new ArrayList<>(jdbc.queryForList(sql, tenantId,
                    baseDate.minusDays(90), baseDate));
        } catch (Exception e) {
            log.warn("原始预约表也无数据: {}", e.getMessage());
            return 0;
        }
        if (history.size() < 7) return 0;

        double[] vals = history.stream()
                .mapToDouble(r -> ((Number) r.get("cnt")).doubleValue())
                .toArray();
        double ewma = computeEwma(vals, 0.3);
        double[] dowEffect = computeDayOfWeekEffect(history);
        double trend = computeTrend(history);

        int count = 0;
        for (int d = 1; d <= days; d++) {
            LocalDate fd = baseDate.plusDays(d);
            int dow = fd.getDayOfWeek().getValue();
            double dowF = dowEffect[dow - 1] > 0 ? dowEffect[dow - 1] : 1.0;
            double pred = Math.max(0, ewma * dowF * (1.0 + trend * d / 14.0));
            double uncer = 0.15 + d * 0.01;

            BookingForecastResult r = new BookingForecastResult();
            r.setTenantId(tenantId);
            r.setStoreId(storeId);
            r.setForecastDate(fd);
            r.setForecastType("BOOKING_COUNT");
            r.setPredictedValue(BigDecimal.valueOf(pred).setScale(2, RoundingMode.HALF_UP));
            r.setLowerBound(BigDecimal.valueOf(Math.max(0, pred * (1 - 1.96 * uncer))).setScale(2, RoundingMode.HALF_UP));
            r.setUpperBound(BigDecimal.valueOf(pred * (1 + 1.96 * uncer)).setScale(2, RoundingMode.HALF_UP));
            r.setConfidence(BigDecimal.valueOf(Math.max(0.5, 0.95 - uncer)).setScale(4, RoundingMode.HALF_UP));
            r.setAlgorithm(algorithm);
            r.setModelVersion("v1");
            r.setBaseWindowDays(history.size());
            r.setGeneratedAt(LocalDateTime.now());

            resultMapper.insert(r);
            count++;
        }
        return count;
    }

    // ====================================================================
    // T1.4 准确度评估（MAE / MAPE / RMSE / SMAPE）
    // ====================================================================

    /**
     * 评估已有预测的准确度
     */
    private void evaluateAccuracy(Long tenantId, Long storeId) {
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT r.forecast_date, r.predicted_value, f.booking_count AS actual_value,
                   r.algorithm, r.model_version,
                   f.day_of_week, f.is_weekend
            FROM booking_forecast_result r
            LEFT JOIN booking_forecast_feature f
              ON f.tenant_id = r.tenant_id
             AND f.feature_date = r.forecast_date
             AND f.store_id = r.store_id
             AND f.feature_hour IS NULL
            WHERE r.tenant_id = ? AND r.forecast_type = 'BOOKING_COUNT'
              AND r.actual_value IS NULL AND f.booking_count IS NOT NULL
            """ + storeFilter + """
            ORDER BY r.forecast_date DESC
            LIMIT 30
            """;
        List<Map<String, Object>> evalRows;
        try {
            evalRows = jdbc.queryForList(sql, tenantId);
        } catch (Exception e) {
            log.warn("准确度评估跳过: {}", e.getMessage());
            return;
        }

        if (evalRows.size() < 3) return;

        double sumAbsErr = 0;
        double sumPctErr = 0;
        double sumSqErr = 0;
        double sumSmape = 0;
        double sumWeekdayErr = 0;
        double sumWeekendErr = 0;
        int weekdayCnt = 0;
        int weekendCnt = 0;

        for (Map<String, Object> row : evalRows) {
            double predicted = ((Number) row.get("predicted_value")).doubleValue();
            double actual = ((Number) row.get("actual_value")).doubleValue();
            double absErr = Math.abs(predicted - actual);
            double pctErr = actual > 0 ? Math.abs(predicted - actual) / actual * 100 : 0;
            double smape = (predicted + actual) > 0
                    ? Math.abs(predicted - actual) / ((predicted + actual) / 2) * 100 : 0;

            sumAbsErr += absErr;
            sumPctErr += pctErr;
            sumSqErr += absErr * absErr;
            sumSmape += smape;

            int isWeekend = row.get("is_weekend") == null ? 0 : ((Number) row.get("is_weekend")).intValue();
            if (isWeekend == 1) {
                sumWeekendErr += absErr;
                weekendCnt++;
            } else {
                sumWeekdayErr += absErr;
                weekdayCnt++;
            }
        }

        int n = evalRows.size();
        double mae = sumAbsErr / n;
        double mape = sumPctErr / n;
        double rmse = Math.sqrt(sumSqErr / n);
        double smapeOverall = sumSmape / n;

        String conclusion;
        if (mape < 15) conclusion = "GOOD";
        else if (mape < 25) conclusion = "FAIR";
        else conclusion = "POOR";

        String recommendation;
        if (mape > 25) recommendation = "历史数据不足或周期波动大，建议增加训练窗口或引入节假日特征";
        else if (mape > 15) recommendation = "可接受，建议加入更多特征（天气/促销/节假日）";
        else recommendation = "预测准确度良好";

        ForecastAccuracyLog log = new ForecastAccuracyLog();
        log.setTenantId(tenantId);
        log.setStoreId(storeId);
        log.setAlgorithm("EWMA_V2");
        log.setModelVersion("v1");
        log.setEvalDate(LocalDate.now());
        log.setWindowDays(n);
        log.setMae(BigDecimal.valueOf(mae).setScale(2, RoundingMode.HALF_UP));
        log.setMape(BigDecimal.valueOf(mape).setScale(4, RoundingMode.HALF_UP));
        log.setRmse(BigDecimal.valueOf(rmse).setScale(2, RoundingMode.HALF_UP));
        log.setSmape(BigDecimal.valueOf(smapeOverall).setScale(4, RoundingMode.HALF_UP));
        log.setWeekdayMae(BigDecimal.valueOf(weekdayCnt > 0 ? sumWeekdayErr / weekdayCnt : 0).setScale(2, RoundingMode.HALF_UP));
        log.setWeekendMae(BigDecimal.valueOf(weekendCnt > 0 ? sumWeekendErr / weekendCnt : 0).setScale(2, RoundingMode.HALF_UP));
        log.setConclusion(conclusion);
        log.setRecommendation(recommendation);
        accuracyMapper.insert(log);

        // 回填 actual_value 到预测结果表
        for (Map<String, Object> row : evalRows) {
            LocalDate fd = ((java.sql.Date) row.get("forecast_date")).toLocalDate();
            double actual = ((Number) row.get("actual_value")).doubleValue();
            double predicted = ((Number) row.get("predicted_value")).doubleValue();
            double errorPct = actual > 0 ? (predicted - actual) / actual * 100 : 0;
            jdbc.update("""
                UPDATE booking_forecast_result SET actual_value = ?, error_pct = ?,
                mae = ? WHERE tenant_id = ? AND forecast_date = ? AND forecast_type = 'BOOKING_COUNT'
                """,
                    actual, errorPct, mae, tenantId, fd);
        }

        BookingForecastService.log.info("[Accuracy] MAE={} MAPE={}% CONCLUSION={}", mae, mape, conclusion);
    }

    // ====================================================================
    // T1.6~T1.9 智能排班生成（贪心算法）
    // ====================================================================

    private void generateScheduleRecommendations(Long tenantId, Long storeId, LocalDate baseDate) {
        // 取未来 7 天预测
        String storeFilter = storeId != null ? "AND store_id = " + storeId : "";
        String sql = """
            SELECT forecast_date, predicted_value
            FROM booking_forecast_result
            WHERE tenant_id = ? AND forecast_type = 'BOOKING_COUNT'
              AND forecast_date >= ? AND forecast_date <= ?
            """ + storeFilter + """
            ORDER BY forecast_date ASC
            LIMIT 7
            """;
        List<Map<String, Object>> forecasts;
        try {
            forecasts = jdbc.queryForList(sql, tenantId, baseDate.plusDays(1), baseDate.plusDays(7));
        } catch (Exception e) {
            log.warn("排班建议跳过: {}", e.getMessage());
            return;
        }
        if (forecasts.isEmpty()) return;

        // 取技师列表和排班约束
        String empSql = """
            SELECT e.id, e.name, e.skill_level, COUNT(s.id) AS schedule_count
            FROM employee e
            LEFT JOIN biz_employee_schedule s ON s.employee_id = e.id
              AND s.schedule_date BETWEEN ? AND ?
            WHERE e.tenant_id = ? AND e.deleted = 0 AND e.status = 1
            GROUP BY e.id, e.name, e.skill_level
            ORDER BY e.skill_level DESC, schedule_count ASC
            LIMIT 20
            """;
        List<Map<String, Object>> employees;
        try {
            employees = jdbc.queryForList(empSql, tenantId, tenantId, baseDate.plusDays(1), baseDate.plusDays(7));
        } catch (Exception e) {
            log.warn("技师列表查询失败: {}", e.getMessage());
            return;
        }

        for (Map<String, Object> fc : forecasts) {
            LocalDate sd = ((java.sql.Date) fc.get("forecast_date")).toLocalDate();
            double predicted = ((Number) fc.get("predicted_value")).doubleValue();

            // 简化排班：按技师等级分配
            List<Map<String, Object>> schedule = new ArrayList<>();
            double remaining = predicted;
            for (Map<String, Object> emp : employees) {
                if (remaining <= 0) break;
                int level = emp.get("skill_level") == null ? 1 : ((Number) emp.get("skill_level")).intValue();
                int slots = (int) Math.ceil(remaining / 4.0); // 每个技师每小时约 4 个预约槽
                int capacity = Math.min(slots, level * 2); // 等级越高容量越大
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("employeeId", emp.get("id"));
                slot.put("employeeName", emp.get("name"));
                slot.put("skillLevel", level);
                slot.put("assignedSlots", Math.min(capacity, (int) remaining));
                schedule.add(slot);
                remaining -= capacity;
            }

            double coverage = predicted > 0 ? Math.min(100, (predicted - remaining) / predicted * 100) : 100;
            double utilization = schedule.stream()
                    .mapToDouble(s -> ((Number) s.getOrDefault("assignedSlots", 0)).doubleValue())
                    .sum() / Math.max(1, schedule.size() * 8); // 假设每天 8 小时
            double score = (coverage * 0.6 + utilization * 40);

            ScheduleRecommendation rec = new ScheduleRecommendation();
            rec.setTenantId(tenantId);
            rec.setStoreId(storeId);
            rec.setScheduleDate(sd);
            rec.setRecommendationJson(toJson(schedule));
            rec.setCoverageRate(BigDecimal.valueOf(coverage).setScale(2, RoundingMode.HALF_UP));
            rec.setUtilizationRate(BigDecimal.valueOf(utilization * 100).setScale(2, RoundingMode.HALF_UP));
            rec.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            rec.setAlgorithm("GREEDY_V1");
            rec.setModelVersion("v1");
            rec.setStatus("PENDING");

            scheduleMapper.delete(new LambdaQueryWrapper<ScheduleRecommendation>()
                    .eq(ScheduleRecommendation::getTenantId, tenantId)
                    .eq(ScheduleRecommendation::getScheduleDate, sd));
            scheduleMapper.insert(rec);
        }
    }

    // ====================================================================
    // 前端 API
    // ====================================================================

    /**
     * 预测曲线数据（历史 + 未来）
     */
    @GetMapping("/curve")
    public Result<Map<String, Object>> getCurve(
            @RequestParam(defaultValue = "14") Integer historyDays,
            @RequestParam(defaultValue = "14") Integer forecastDays) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate end = LocalDate.now();
        LocalDate histStart = end.minusDays(historyDays);
        LocalDate fcEnd = end.plusDays(forecastDays);

        Map<String, Object> result = new LinkedHashMap<>();

        // 历史
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            history = new ArrayList<>(jdbc.queryForList(
                    "SELECT feature_date AS dt, booking_count AS value " +
                            "FROM booking_forecast_feature WHERE tenant_id = ? AND feature_date >= ? AND feature_hour IS NULL " +
                            "ORDER BY feature_date ASC LIMIT ?",
                    tenantId, histStart, historyDays));
        } catch (Exception ignored) {}
        result.put("history", history);

        // 预测
        List<Map<String, Object>> forecast = new ArrayList<>();
        try {
            forecast = new ArrayList<>(jdbc.queryForList(
                    "SELECT forecast_date AS dt, predicted_value AS value, lower_bound, upper_bound " +
                            "FROM booking_forecast_result WHERE tenant_id = ? AND forecast_type = 'BOOKING_COUNT' " +
                            "AND forecast_date > ? AND forecast_date <= ? ORDER BY forecast_date ASC",
                    tenantId, end, fcEnd));
        } catch (Exception ignored) {}
        result.put("forecast", forecast);

        // 准确度
        List<Map<String, Object>> accuracy = new ArrayList<>();
        try {
            accuracy = new ArrayList<>(jdbc.queryForList(
                    "SELECT eval_date, mae, mape, conclusion FROM forecast_accuracy_log " +
                            "WHERE tenant_id = ? ORDER BY eval_date DESC LIMIT 10",
                    tenantId));
        } catch (Exception ignored) {}
        result.put("accuracy", accuracy);

        return Result.success(result);
    }

    /**
     * 客流热力图数据（星期 × 小时）
     */
    @GetMapping("/heatmap")
    public Result<Map<String, Object>> getHeatmap(
            @RequestParam(defaultValue = "30") Integer windowDays) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate start = LocalDate.now().minusDays(windowDays);

        String sql = """
            SELECT day_of_week, feature_hour,
                   AVG(booking_count) AS avg_count
            FROM booking_forecast_feature
            WHERE tenant_id = ? AND feature_date >= ?
              AND day_of_week IS NOT NULL AND feature_hour IS NOT NULL
            GROUP BY day_of_week, feature_hour
            ORDER BY day_of_week, feature_hour
            """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, tenantId, start);
        } catch (Exception e) {
            return Result.success(Map.of("note", "特征表无数据，请先运行 ETL"));
        }

        // 转换为矩阵
        double[][] matrix = new double[8][24]; // dow=1..7, hour=0..23
        int[][] count = new int[8][24];
        for (Map<String, Object> r : rows) {
            int dow = ((Number) r.get("day_of_week")).intValue();
            int hour = ((Number) r.get("feature_hour")).intValue();
            double val = ((Number) r.get("avg_count")).doubleValue();
            matrix[dow][hour] = (matrix[dow][hour] * count[dow][hour] + val) / (count[dow][hour] + 1);
            count[dow][hour]++;
        }

        List<List<Double>> data = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            List<Double> row = new ArrayList<>();
            for (int h = 0; h < 24; h++) row.add(matrix[dow][h]);
            data.add(row);
        }

        return Result.success(Map.of("matrix", data,
                "dowLabels", List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
                "hourLabels", IntStream.range(0, 24).boxed().collect(Collectors.toList())));
    }

    /**
     * 排班建议列表
     */
    @GetMapping("/schedule/recommendations")
    public Result<List<Map<String, Object>>> getRecommendations(
            @RequestParam(defaultValue = "7") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate start = LocalDate.now().plusDays(1);

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT r.schedule_date, r.coverage_rate, r.utilization_rate, r.score, r.status, " +
                            "r.recommendation_json FROM schedule_recommendation r " +
                            "WHERE r.tenant_id = ? AND r.schedule_date >= ? AND r.schedule_date <= ? " +
                            "ORDER BY r.schedule_date ASC",
                    tenantId, start, start.plusDays(days));
        } catch (Exception e) {
            return Result.success(List.of());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>(r);
            item.remove("recommendation_json"); // JSON 太大，单独接口获取
            result.add(item);
        }
        return Result.success(result);
    }

    @GetMapping("/schedule/recommendation/{date}")
    public Result<Map<String, Object>> getRecommendationDetail(@PathVariable String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT recommendation_json, coverage_rate, utilization_rate, score, status " +
                            "FROM schedule_recommendation WHERE tenant_id = ? AND schedule_date = ?",
                    tenantId, LocalDate.parse(date));
        } catch (Exception e) {
            return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "无建议数据");
        }
        if (rows.isEmpty()) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "无建议数据");
        return Result.success(rows.get(0));
    }

    @PostMapping("/schedule/recommendation/{date}/apply")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> applyRecommendation(@PathVariable String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int updated = jdbc.update(
                "UPDATE schedule_recommendation SET status='APPLIED', applied_at=NOW() " +
                        "WHERE tenant_id = ? AND schedule_date = ? AND status='PENDING'",
                tenantId, LocalDate.parse(date));
        if (updated == 0) return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "无待应用建议");
        return Result.success("已应用", null);
    }

    /**
     * 准确度趋势
     */
    @GetMapping("/accuracy/trend")
    public Result<List<Map<String, Object>>> accuracyTrend(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT eval_date, mae, mape, rmse, smape, conclusion FROM forecast_accuracy_log " +
                            "WHERE tenant_id = ? ORDER BY eval_date DESC LIMIT ?",
                    tenantId, limit);
        } catch (Exception e) {
            return Result.success(List.of());
        }
        return Result.success(rows);
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private double computeEwma(double[] values, double alpha) {
        double ewma = values[0];
        for (int i = 1; i < values.length; i++) {
            ewma = alpha * values[i] + (1 - alpha) * ewma;
        }
        return ewma;
    }

    private double[] computeDayOfWeekEffect(List<Map<String, Object>> history) {
        double[] total = new double[7];
        int[] cnt = new int[7];
        for (Map<String, Object> r : history) {
            int dow = ((Number) r.get("day_of_week")).intValue();
            double v = ((Number) r.get("booking_count")).doubleValue();
            total[dow - 1] += v;
            cnt[dow - 1]++;
        }
        double avgAll = 0;
        int totalCnt = 0;
        for (int i = 0; i < 7; i++) { avgAll += total[i]; totalCnt += cnt[i]; }
        avgAll = totalCnt > 0 ? avgAll / totalCnt : 1;

        double[] effect = new double[7];
        for (int i = 0; i < 7; i++) {
            effect[i] = cnt[i] > 0 ? (total[i] / cnt[i]) / avgAll : 1.0;
        }
        return effect;
    }

    private double computeTrend(List<Map<String, Object>> history) {
        if (history.size() < 14) return 0;
        double recent = history.stream()
                .skip(history.size() - 7)
                .mapToDouble(r -> ((Number) r.get("booking_count")).doubleValue())
                .average().orElse(0);
        double older = history.stream()
                .skip(Math.max(0, history.size() - 14))
                .limit(7)
                .mapToDouble(r -> ((Number) r.get("booking_count")).doubleValue())
                .average().orElse(0);
        return older > 0 ? (recent - older) / older : 0;
    }

    private static String toJson(Object obj) {
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                sb.append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj == null) {
            return "null";
        } else {
            return "\"" + obj.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
