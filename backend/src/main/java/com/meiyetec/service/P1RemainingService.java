package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * R3.0-F P1 剩余改进：
 *  - P1-3 总部统一下发  /mkt/dispatch/...
 *  - P1-5 智能排班       /schedule/ai/...
 *  - P1-5 需求预测       /intel/forecast/...
 *  - P1-5 推荐算法       /intel/recommend/...
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class P1RemainingService {

    private final MktDispatchMapper dispatchMapper;
    private final MktDispatchTargetMapper dispatchTargetMapper;
    private final ScheduleSuggestionMapper scheduleSuggestionMapper;
    private final BizForecastDemandMapper forecastMapper;
    private final CrmRecommendLogMapper recommendLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // P1-3 总部统一下发
    // ====================================================================

    @PostMapping("/mkt/dispatch")
    @Transactional(rollbackFor = Exception.class)
    public Result<MktDispatch> createDispatch(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String dispatchType = (String) body.get("dispatchType");
        String title = (String) body.get("title");
        if (dispatchType == null || title == null)
            return Result.error(BizCode.BAD_REQUEST, "dispatchType / title 必填");

        MktDispatch d = new MktDispatch();
        d.setTenantId(tenantId);
        d.setBrandId(body.get("brandId") == null ? null
                : ((Number) body.get("brandId")).longValue());
        d.setDispatchNo("DSP" + LocalDateTime.now().toString().replace(":", "").substring(0, 15)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        d.setDispatchType(dispatchType);
        d.setTitle(title);
        d.setPayload(body.get("payload") == null ? null : body.get("payload").toString());
        d.setTargetType((String) body.getOrDefault("targetType", "SPECIFIC"));
        d.setTargetStoreIds(body.get("targetStoreIds") == null ? null
                : body.get("targetStoreIds").toString());
        d.setStatus("DRAFT");
        d.setPlanStartTime(body.get("planStartTime") == null ? null
                : LocalDateTime.parse((String) body.get("planStartTime")));
        d.setPlanEndTime(body.get("planEndTime") == null ? null
                : LocalDateTime.parse((String) body.get("planEndTime")));
        d.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        dispatchMapper.insert(d);

        // 解析目标门店
        List<Long> storeIds = resolveTargetStores(d);
        for (Long storeId : storeIds) {
            MktDispatchTarget t = new MktDispatchTarget();
            t.setTenantId(tenantId);
            t.setDispatchId(d.getId());
            t.setStoreId(storeId);
            t.setStatus("PENDING");
            dispatchTargetMapper.insert(t);
        }
        return Result.success("已创建下发策略（" + storeIds.size() + " 个门店）", d);
    }

    @PostMapping("/mkt/dispatch/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> publish(@PathVariable Long id) {
        MktDispatch d = dispatchMapper.selectById(id);
        if (d == null) return Result.error(BizCode.NOT_FOUND, "下发策略不存在");
        if (!"DRAFT".equals(d.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可发布");

        jdbc.update(
            "UPDATE mkt_dispatch SET status='PUBLISHED', published_at=NOW() WHERE id=?",
            id);
        jdbc.update(
            "UPDATE mkt_dispatch_target SET status='PENDING' WHERE dispatch_id=? AND status='PENDING'",
            id);

        Map<String, Object> data = new HashMap<>();
        data.put("dispatchId", id);
        data.put("dispatchNo", d.getDispatchNo());
        data.put("dispatchType", d.getDispatchType());
        data.put("title", d.getTitle());
        data.put("status", "PUBLISHED");
        data.put("hint", "门店已收到待执行（async worker 模拟）");
        return Result.success("已发布到所有目标门店", data);
    }

    @PostMapping("/mkt/dispatch/{id}/cancel")
    public Result<Void> cancelDispatch(@PathVariable Long id) {
        MktDispatch d = dispatchMapper.selectById(id);
        if (d == null) return Result.error(BizCode.NOT_FOUND, "下发策略不存在");
        if ("DONE".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可取消");
        jdbc.update("UPDATE mkt_dispatch SET status='CANCELLED' WHERE id=?", id);
        jdbc.update(
            "UPDATE mkt_dispatch_target SET status='REJECTED', rejected_reason='总部取消' " +
            "WHERE dispatch_id=? AND status IN ('PENDING','ACCEPTED')", id);
        return Result.success("已取消", null);
    }

    @GetMapping("/mkt/dispatch/page")
    public Result<Map<String, Object>> pageDispatch(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dispatchType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<MktDispatch> qw = new LambdaQueryWrapper<>();
        qw.eq(MktDispatch::getTenantId, tenantId);
        if (status != null && !status.isEmpty()) qw.eq(MktDispatch::getStatus, status);
        if (dispatchType != null && !dispatchType.isEmpty())
            qw.eq(MktDispatch::getDispatchType, dispatchType);
        qw.orderByDesc(MktDispatch::getId);
        long total = dispatchMapper.selectCount(qw);
        List<MktDispatch> records = dispatchMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        p.put("current", pageNum);
        p.put("size", pageSize);
        return Result.success(p);
    }

    /** 门店接收：当前 store 接受/拒绝下发 */
    @PostMapping("/mkt/dispatch/target/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> acceptDispatch(@PathVariable Long id) {
        MktDispatchTarget t = dispatchTargetMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "下发目标不存在");
        if (!"PENDING".equals(t.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可接收");
        jdbc.update(
            "UPDATE mkt_dispatch_target SET status='ACCEPTED', accepted_at=NOW() WHERE id=?",
            id);
        return Result.success("已接收", null);
    }

    @PostMapping("/mkt/dispatch/target/{id}/execute")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> executeDispatch(@PathVariable Long id) {
        MktDispatchTarget t = dispatchTargetMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "下发目标不存在");
        if (!"ACCEPTED".equals(t.getStatus()))
            return Result.error(BizCode.CONFLICT, "请先接收");
        jdbc.update(
            "UPDATE mkt_dispatch_target SET status='EXECUTED', executed_at=NOW() WHERE id=?",
            id);
        // 全部 EXECUTED → 父级 DONE
        Long pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mkt_dispatch_target WHERE dispatch_id=? AND status<>'EXECUTED' AND deleted=0",
            Long.class, t.getDispatchId());
        if (pending != null && pending == 0) {
            jdbc.update("UPDATE mkt_dispatch SET status='DONE' WHERE id=? AND deleted=0",
                    t.getDispatchId());
        }
        return Result.success("已执行", null);
    }

    /** 解析目标门店：ALL/BRAND/SPECIFIC */
    private List<Long> resolveTargetStores(MktDispatch d) {
        if ("ALL".equals(d.getTargetType())) {
            return jdbc.queryForList(
                "SELECT id FROM pos_store WHERE deleted=0 AND tenant_id=?",
                Long.class, d.getTenantId());
        }
        if ("BRAND".equals(d.getTargetType()) && d.getBrandId() != null) {
            return jdbc.queryForList(
                "SELECT id FROM pos_store WHERE deleted=0 AND brand_id=?",
                Long.class, d.getBrandId());
        }
        // SPECIFIC（payload 可能是 JSON 数组）
        if (d.getTargetStoreIds() == null || d.getTargetStoreIds().isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        String s = d.getTargetStoreIds().replaceAll("[\\[\\]\\s]", "");
        for (String x : s.split(",")) {
            try { ids.add(Long.parseLong(x)); } catch (Exception ignore) {}
        }
        return ids;
    }

    // ====================================================================
    // P1-5 智能排班（基于历史 booking 频次 + 节假日加权）
    // ====================================================================

    @PostMapping("/schedule/ai/suggest")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> scheduleSuggest(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue();
        String startDate = (String) body.get("startDate");
        String endDate = (String) body.get("endDate");
        if (storeId == null || startDate == null || endDate == null)
            return Result.error(BizCode.BAD_REQUEST, "storeId / startDate / endDate 必填");

        LocalDate from = LocalDate.parse(startDate);
        LocalDate to = LocalDate.parse(endDate);
        // 拉门店技师
        List<Map<String, Object>> employees = jdbc.queryForList(
            "SELECT id, name FROM pos_employee WHERE deleted=0 AND store_id=? AND status=1 LIMIT 50",
            storeId);
        if (employees.isEmpty())
            return Result.error(BizCode.NOT_FOUND, "门店无可用技师");

        // 历史 30 天 booking 频次（按"星期 + 时段"）
        Map<String, Double> loadByDayHour = new HashMap<>();
        List<Map<String, Object>> history = jdbc.queryForList(
            "SELECT DAYOFWEEK(booking_time) AS dow, HOUR(booking_time) AS hr, COUNT(*) c " +
            "FROM crm_booking WHERE deleted=0 AND tenant_id=? AND store_id=? " +
            "  AND booking_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
            "GROUP BY dow, hr", tenantId, storeId);
        for (Map<String, Object> h : history) {
            int dow = ((Number) h.get("dow")).intValue();
            int hr = ((Number) h.get("hr")).intValue();
            double c = ((Number) h.get("c")).doubleValue();
            loadByDayHour.put(dow + "-" + hr, c);
        }

        // 推荐：按"未来 7 天"逐日生成
        List<ScheduleSuggestion> suggestions = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue() + 1;  // 1-7
            for (Map<String, Object> emp : employees) {
                Long empId = ((Number) emp.get("id")).longValue();
                String empName = (String) emp.get("name");

                // 选取 3 个时段：上午(10)/下午(14)/晚间(19)
                int[] hours = {10, 14, 19};
                for (int hr : hours) {
                    Double load = loadByDayHour.getOrDefault(dow + "-" + hr, 0.0);
                    if (load == 0) continue;  // 无历史订单，跳过
                    int expected = (int) Math.min(100, load * 10);
                    ScheduleSuggestion s = new ScheduleSuggestion();
                    s.setTenantId(tenantId);
                    s.setStoreId(storeId);
                    s.setWorkDate(d);
                    s.setEmployeeId(empId);
                    s.setEmployeeName(empName);
                    s.setShiftCode(hr < 13 ? "DAY" : (hr < 18 ? "MIDDLE" : "NIGHT"));
                    s.setStartTime(LocalTime.of(hr, 0));
                    s.setEndTime(LocalTime.of(hr + 3, 0));
                    s.setExpectedLoad(expected);
                    s.setConfidence(BigDecimal.valueOf(0.6 + Math.min(0.39, load / 100.0))
                            .setScale(2, RoundingMode.HALF_UP));
                    s.setReason("{\"hourly_load\":" + load + ",\"dow\":" + dow + "}");
                    s.setStatus("PENDING");
                    scheduleSuggestionMapper.insert(s);
                    suggestions.add(s);
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("suggestionCount", suggestions.size());
        data.put("dateRange", startDate + " ~ " + endDate);
        data.put("storeId", storeId);
        return Result.success("已生成 " + suggestions.size() + " 个排班建议", data);
    }

    @GetMapping("/schedule/ai/list")
    public Result<Map<String, Object>> listScheduleSuggestions(
            @RequestParam Long storeId,
            @RequestParam(required = false) String workDate,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ScheduleSuggestion> qw = new LambdaQueryWrapper<>();
        qw.eq(ScheduleSuggestion::getStoreId, storeId);
        if (workDate != null && !workDate.isEmpty())
            qw.eq(ScheduleSuggestion::getWorkDate, LocalDate.parse(workDate));
        if (status != null && !status.isEmpty()) qw.eq(ScheduleSuggestion::getStatus, status);
        qw.orderByAsc(ScheduleSuggestion::getWorkDate).orderByAsc(ScheduleSuggestion::getStartTime);
        long total = scheduleSuggestionMapper.selectCount(qw);
        List<ScheduleSuggestion> records = scheduleSuggestionMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        return Result.success(p);
    }

    @PostMapping("/schedule/ai/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> acceptSuggestion(@PathVariable Long id) {
        ScheduleSuggestion ss = scheduleSuggestionMapper.selectById(id);
        if (ss == null) return Result.error(BizCode.NOT_FOUND, "建议不存在");
        if (!"PENDING".equals(ss.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可接收");
        jdbc.update(
            "UPDATE schedule_suggestion SET status='ACCEPTED', accepted_at=NOW() WHERE id=?",
            id);
        // 同时创建 EmployeeSchedule
        EmployeeSchedule es = new EmployeeSchedule();
        es.setEmployeeId(ss.getEmployeeId());
        es.setWorkDate(ss.getWorkDate());
        es.setStartTime(ss.getStartTime());
        es.setEndTime(ss.getEndTime());
        // shiftType: DAY=1, MIDDLE=2, NIGHT=3
        es.setShiftType(ss.getShiftCode() == null ? 1
                : (ss.getShiftCode().equals("NIGHT") ? 3
                : (ss.getShiftCode().equals("MIDDLE") ? 2 : 1)));
        jdbc.update(
            "INSERT INTO employee_schedule (employee_id, work_date, start_time, end_time, shift_type, status) " +
            "VALUES (?, ?, ?, ?, ?, 1) " +
            "ON DUPLICATE KEY UPDATE start_time=VALUES(start_time), end_time=VALUES(end_time)",
            es.getEmployeeId(), es.getWorkDate(), es.getStartTime(), es.getEndTime(), es.getShiftType());
        return Result.success("已接收并写入排班", null);
    }

    // ====================================================================
    // P1-5 需求预测（移动平均 + 线性回归近似）
    // ====================================================================

    @PostMapping("/intel/forecast/demand")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> forecastDemand(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue();
        String forecastType = (String) body.getOrDefault("forecastType", "BOOKING");
        Integer horizonDays = (Integer) body.getOrDefault("horizonDays", 7);
        if (storeId == null) return Result.error(BizCode.BAD_REQUEST, "storeId 必填");

        // 拉历史 30 天每天的 count
        List<Map<String, Object>> history = jdbc.queryForList(
            "SELECT DATE(booking_time) d, COUNT(*) c FROM crm_booking " +
            "WHERE deleted=0 AND tenant_id=? AND store_id=? " +
            "  AND booking_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(booking_time) ORDER BY d", tenantId, storeId);

        // 简单移动平均（7 天窗口） + 趋势因子
        double[] y = new double[history.size()];
        for (int i = 0; i < history.size(); i++)
            y[i] = ((Number) history.get(i).get("c")).doubleValue();

        double avg = y.length == 0 ? 0 : (sum(y) / y.length);
        double trend = linearTrend(y);
        double base7 = y.length < 7 ? avg : (sumWindow(y, y.length - 7, y.length) / 7.0);

        List<BizForecastDemand> list = new ArrayList<>();
        for (int i = 1; i <= horizonDays; i++) {
            double predicted = Math.max(0, base7 + trend * i);
            BigDecimal predictedValue = BigDecimal.valueOf(predicted).setScale(2, RoundingMode.HALF_UP);

            BizForecastDemand d = new BizForecastDemand();
            d.setTenantId(tenantId);
            d.setStoreId(storeId);
            d.setForecastDate(LocalDate.now().plusDays(i));
            d.setForecastType(forecastType);
            d.setTargetId(null);
            d.setTargetName("全门店");
            d.setPredictedValue(predictedValue);
            d.setActualValue(BigDecimal.ZERO);
            d.setModelVersion("BASE-MA7");
            d.setConfidence(BigDecimal.valueOf(0.55).setScale(2, RoundingMode.HALF_UP));
            d.setOperatorName("system");
            d.setForecastAt(LocalDateTime.now());
            forecastMapper.insert(d);
            list.add(d);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("forecastCount", list.size());
        data.put("horizonDays", horizonDays);
        data.put("baseline7d", round2(base7));
        data.put("trend", round2(trend));
        data.put("modelVersion", "BASE-MA7");
        data.put("records", list);
        return Result.success("已生成未来 " + horizonDays + " 天预测", data);
    }

    @GetMapping("/intel/forecast/list")
    public Result<Map<String, Object>> listForecasts(
            @RequestParam Long storeId,
            @RequestParam(required = false) String forecastType,
            @RequestParam(defaultValue = "7") Integer days) {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT forecast_date, forecast_type, predicted_value, actual_value, model_version, confidence " +
            "FROM biz_forecast_demand " +
            "WHERE store_id=? AND forecast_date >= CURDATE() " +
            "  AND forecast_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY) " +
            "  AND forecast_type = COALESCE(?, forecast_type) " +
            "ORDER BY forecast_date", storeId, days, forecastType);
        data.put("records", rows);
        return Result.success(data);
    }

    /** 简单线性趋势（斜率） */
    private static double linearTrend(double[] y) {
        int n = y.length;
        if (n < 2) return 0;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) {
            sx += i; sy += y[i];
            sxy += i * y[i]; sxx += i * i;
        }
        double denom = n * sxx - sx * sx;
        return denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
    }

    private static double sum(double[] y) {
        double s = 0;
        for (double v : y) s += v;
        return s;
    }

    private static double sumWindow(double[] y, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += y[i];
        return s;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ====================================================================
    // P1-5 推荐算法（基于 RFM + LTV + 行为加权）
    // ====================================================================

    /**
     * 综合推荐：会员维度智能推荐优惠券/套餐/服务
     */
    @GetMapping("/intel/recommend/{memberId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> recommend(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 1. 拉会员 RFM segment
        Map<String, Object> rfm = jdbc.queryForList(
            "SELECT rfm_segment, rfm_score, ltv_predicted_365d, last_purchase_days " +
            "FROM crm_rfm_snapshot WHERE member_id=? AND tenant_id=? " +
            "ORDER BY snapshot_date DESC LIMIT 1", memberId, tenantId)
            .stream().findFirst().orElse(null);

        String segment = rfm == null ? "NEW" : (String) rfm.get("rfm_segment");
        BigDecimal ltv = rfm == null ? BigDecimal.ZERO
                : (BigDecimal) rfm.get("ltv_predicted_365d");
        long lastDays = rfm == null ? 9999L
                : ((Number) rfm.getOrDefault("last_purchase_days", 9999)).longValue();

        // 2. 候选优惠券打分
        List<Map<String, Object>> coupons = jdbc.queryForList(
            "SELECT id, coupon_name, discount_amount, min_order_amount " +
            "FROM mkt_coupon WHERE deleted=0 AND tenant_id=? AND enabled=1 AND status=1 LIMIT 20",
            tenantId);
        List<Map<String, Object>> couponScores = new ArrayList<>();
        for (Map<String, Object> c : coupons) {
            double score = scoreCoupon(c, segment, ltv, lastDays);
            Map<String, Object> item = new HashMap<>();
            item.put("type", "COUPON");
            item.put("id", c.get("id"));
            item.put("name", c.get("coupon_name"));
            item.put("score", round2(score));
            item.put("reason", reasonForCoupon(segment, ltv, lastDays));
            couponScores.add(item);
        }

        // 3. 候选套餐（按价位匹配）
        List<Map<String, Object>> packages = jdbc.queryForList(
            "SELECT id, package_name, price FROM crm_package WHERE deleted=0 AND tenant_id=? LIMIT 20",
            tenantId);
        List<Map<String, Object>> pkgScores = new ArrayList<>();
        for (Map<String, Object> p : packages) {
            double score = scorePackage(p, segment, ltv);
            Map<String, Object> item = new HashMap<>();
            item.put("type", "PACKAGE");
            item.put("id", p.get("id"));
            item.put("name", p.get("package_name"));
            item.put("score", round2(score));
            item.put("reason", reasonForPackage(segment, ltv));
            pkgScores.add(item);
        }

        // 4. 排序 + 推荐 Top 3
        couponScores.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        pkgScores.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        List<Map<String, Object>> top = new ArrayList<>();
        top.addAll(couponScores.subList(0, Math.min(3, couponScores.size())));
        top.addAll(pkgScores.subList(0, Math.min(3, pkgScores.size())));
        top.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        top = top.subList(0, Math.min(5, top.size()));

        // 5. 写推荐日志
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> r : top) {
            CrmRecommendLog log = new CrmRecommendLog();
            log.setTenantId(tenantId);
            log.setMemberId(memberId);
            log.setRecommendType((String) r.get("type"));
            log.setTargetId(((Number) r.get("id")).longValue());
            log.setTargetName((String) r.get("name"));
            log.setScore(BigDecimal.valueOf((double) r.get("score")));
            log.setReason((String) r.get("reason"));
            log.setChannel("INTERNAL");
            log.setShown(1);
            log.setExposedAt(LocalDateTime.now());
            recommendLogMapper.insert(log);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("segment", segment);
        data.put("ltvPredicted", ltv);
        data.put("lastDays", lastDays);
        data.put("recommendations", top);
        return Result.success("推荐 " + top.size() + " 项", data);
    }

    private double scoreCoupon(Map<String, Object> c, String segment, BigDecimal ltv, long lastDays) {
        double base = 0.3;
        // 高价值客户 + 长期未消费：高分
        if (ltv != null && ltv.compareTo(new BigDecimal("5000")) >= 0) base += 0.3;
        if (lastDays > 90) base += 0.2;
        if ("LOST".equals(segment)) base += 0.3;
        else if ("ABOUT_TO_SLEEP".equals(segment)) base += 0.2;
        else if ("CHAMPION".equals(segment)) base += 0.1;
        // 大额券更吸引高价值客户
        Object amt = c.get("discount_amount");
        if (amt != null && new BigDecimal(amt.toString()).compareTo(new BigDecimal("100")) >= 0)
            base += 0.1;
        return Math.min(1.0, base);
    }

    private double scorePackage(Map<String, Object> p, String segment, BigDecimal ltv) {
        double base = 0.4;
        if ("CHAMPION".equals(segment)) base += 0.3;
        if (ltv != null && ltv.compareTo(new BigDecimal("10000")) >= 0) base += 0.2;
        return Math.min(1.0, base);
    }

    private String reasonForCoupon(String segment, BigDecimal ltv, long lastDays) {
        if ("LOST".equals(segment)) return "流失客户召回";
        if ("ABOUT_TO_SLEEP".equals(segment)) return "即将流失唤醒";
        if (lastDays > 90) return "长期未消费";
        if (ltv != null && ltv.compareTo(new BigDecimal("5000")) >= 0) return "高价值客户回馈";
        return "通用推荐";
    }

    private String reasonForPackage(String segment, BigDecimal ltv) {
        if ("CHAMPION".equals(segment)) return "VIP 进阶套餐";
        if (ltv != null && ltv.compareTo(new BigDecimal("10000")) >= 0) return "高价值客户专属";
        return "热门套餐";
    }
}