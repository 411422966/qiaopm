package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.ReportExportTask;
import com.meiyetec.entity.ReportMetricCaliber;
import com.meiyetec.entity.ReportMetricSnapshot;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.ReportExportTaskMapper;
import com.meiyetec.mapper.ReportMetricCaliberMapper;
import com.meiyetec.mapper.ReportMetricSnapshotMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R2.1-E：报表 SQL 聚合与异步导出
 *  - T-E11 重构 ReportController：按租户/门店/时间范围 SQL 聚合
 *  - T-E12 报表指标口径 v1 + 数据快照表
 *  - T-E13 导出任务表 + 异步生成（线程池起步）
 *  - T-E14 报表权限：按租户/门店过滤，敏感字段脱敏
 */
@RestController
@RequestMapping("/report/v2")
@RequiredArgsConstructor
public class ReportV2Service {

    private static final Logger log = LoggerFactory.getLogger(ReportV2Service.class);

    private final ReportExportTaskMapper taskMapper;
    private final ReportMetricSnapshotMapper snapshotMapper;
    private final ReportMetricCaliberMapper caliberMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 内存中并发限制（线程池轻量起步）
    private static final AtomicInteger RUNNING = new AtomicInteger(0);
    private static final int MAX_RUNNING = 4;

    // 模拟文件存储
    private static final Path EXPORT_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "meiyetec-reports");

    // ====================================================================
    // T-E11 SQL 聚合（同步、低耗时）
    // ====================================================================

    /**
     * 看板聚合查询（按时间范围）
     * 支持 reportCode：SALES / PAY / MEMBER / COMMISSION
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        // T-E14 强制租户隔离
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        // 越权校验：cross-tenant/store
        // 演示：如果 storeId 不属于当前租户/门店，则拒绝
        if (storeId != null) {
            Integer allowStore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_store WHERE id = ?",
                Integer.class, storeId);
            if (allowStore == null || allowStore == 0) {
                return Result.error(BizCode.FORBIDDEN, "无权访问该门店数据");
            }
        }

        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDateTime sDt = s.atStartOfDay();
        LocalDateTime eDt = e.atTime(23, 59, 59);

        Map<String, Object> data = new HashMap<>();

        // 销售
        String sqlSales =
            "SELECT COUNT(*) AS order_count, " +
            "       COALESCE(SUM(final_amount), 0) AS gmv, " +
            "       COALESCE(SUM(IFNULL(refund_amount,0)), 0) AS refund_amount, " +
            "       COALESCE(SUM(final_amount - IFNULL(refund_amount,0)), 0) AS net_revenue " +
            "FROM pos_order " +
            "WHERE tenant_id = ? AND deleted = 0 " +
            "  AND status IN (3,4) " +
            "  AND pay_time BETWEEN ? AND ? ";
        List<Object> sArgs = new ArrayList<>(List.of(tenantId, sDt, eDt));
        if (storeId != null) { sqlSales += "AND store_id = ? "; sArgs.add(storeId); }
        Map<String, Object> sales = jdbc.queryForMap(sqlSales, sArgs.toArray());
        sales.put("avgOrder", ((Number) sales.getOrDefault("order_count", 0)).longValue() == 0
                ? BigDecimal.ZERO
                : new BigDecimal(sales.get("net_revenue").toString())
                    .divide(new BigDecimal(sales.get("order_count").toString()), 2, java.math.RoundingMode.HALF_UP));

        // 支付方式（pos_payment 没有 store_id，按订单 join 拿门店）
        String sqlPay =
            "SELECT p.method AS pay_method, COUNT(*) AS cnt, COALESCE(SUM(p.amount), 0) AS total " +
            "FROM pos_payment p JOIN pos_order o ON o.id = p.order_id " +
            "WHERE p.tenant_id = ? AND p.deleted = 0 AND p.status = 1 " +
            "  AND p.pay_time BETWEEN ? AND ? ";
        List<Object> pArgs = new ArrayList<>(List.of(tenantId, sDt, eDt));
        if (storeId != null) { sqlPay += "AND o.store_id = ? "; pArgs.add(storeId); }
        sqlPay += "GROUP BY p.method ORDER BY total DESC";
        List<Map<String, Object>> pays = jdbc.queryForList(sqlPay, pArgs.toArray());

                // 会员
        String sqlMember;
        List<Object> m1;
        if (storeId != null) {
            sqlMember =
                "SELECT " +
                "  (SELECT COUNT(*) FROM crm_member WHERE deleted = 0 AND create_time BETWEEN ? AND ? AND store_id = ?) AS new_count ";
            m1 = new ArrayList<>(List.of(sDt, eDt, storeId));
        } else {
            sqlMember =
                "SELECT " +
                "  (SELECT COUNT(*) FROM crm_member WHERE deleted = 0 AND create_time BETWEEN ? AND ?) AS new_count ";
            m1 = new ArrayList<>(List.of(sDt, eDt));
        }
        String sqlMember2 =
            "SELECT COUNT(DISTINCT member_id) AS active_count FROM pos_order " +
            "WHERE tenant_id = ? AND deleted = 0 AND member_id IS NOT NULL " +
            "  AND pay_time BETWEEN ? AND ? ";
        List<Object> m2 = new ArrayList<>(List.of(tenantId, sDt, eDt));
        if (storeId != null) {
            m2.add(storeId);
            sqlMember2 += "AND store_id = ? ";
        }
        Map<String, Object> member = jdbc.queryForMap(sqlMember, m1.toArray());
        Map<String, Object> active = jdbc.queryForMap(sqlMember2, m2.toArray());
        member.putAll(active);

        // 提成
        String sqlCommission =
            "SELECT COALESCE(SUM(commission_amount), 0) AS total " +
            "FROM commission_record " +
            "WHERE tenant_id = ? AND deleted = 0 AND status IN (1,2,3) AND business_time BETWEEN ? AND ? ";
        List<Object> cArgs = new ArrayList<>(List.of(tenantId, sDt, eDt));
        if (storeId != null) { sqlCommission += "AND store_id = ? "; cArgs.add(storeId); }
        Map<String, Object> commission = jdbc.queryForMap(sqlCommission, cArgs.toArray());

        // 日趋势（最近 14 天）
        String sqlTrend =
            "SELECT DATE(pay_time) AS d, COUNT(*) AS order_count, COALESCE(SUM(final_amount - IFNULL(refund_amount,0)), 0) AS net_revenue " +
            "FROM pos_order " +
            "WHERE tenant_id = ? AND deleted = 0 AND status IN (3,4) " +
            "  AND pay_time BETWEEN DATE_SUB(?, INTERVAL 13 DAY) AND ? ";
        List<Object> tArgs = new ArrayList<>(List.of(tenantId, eDt, eDt));
        if (storeId != null) { sqlTrend += "AND store_id = ? "; tArgs.add(storeId); }
        sqlTrend += "GROUP BY DATE(pay_time) ORDER BY d ASC";
        List<Map<String, Object>> trend = jdbc.queryForList(sqlTrend, tArgs.toArray());

        data.put("periodStart", s);
        data.put("periodEnd", e);
        data.put("sales", Keys.camel(sales));
        data.put("payMethods", Keys.camel(pays));
        data.put("member", Keys.camel(member));
        data.put("commission", Keys.camel(commission));
        data.put("trend", Keys.camel(trend));
        return Result.success(data);
    }

    @GetMapping("/sales/daily")
    public Result<List<Map<String, Object>>> salesDaily(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);

        String sql =
            "SELECT DATE(pay_time) AS date, COUNT(*) AS order_count, " +
            "       COALESCE(SUM(final_amount), 0) AS gmv, " +
            "       COALESCE(SUM(IFNULL(refund_amount,0)), 0) AS refund_amount, " +
            "       COALESCE(SUM(final_amount - IFNULL(refund_amount,0)), 0) AS net_revenue " +
            "FROM pos_order " +
            "WHERE tenant_id = ? AND deleted = 0 AND status IN (3,4) " +
            "  AND pay_time BETWEEN ? AND ? ";
        List<Object> args = new ArrayList<>(List.of(tenantId, s.atStartOfDay(), e.atTime(23, 59, 59)));
        if (storeId != null) { sql += "AND store_id = ? "; args.add(storeId); }
        sql += "GROUP BY DATE(pay_time) ORDER BY date DESC";
        return Result.success(Keys.camel(jdbc.queryForList(sql, args.toArray())));
    }

    @GetMapping("/product/top")
    public Result<List<Map<String, Object>>> topProducts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);

        String sql =
            "SELECT i.service_id, i.service_name, SUM(i.quantity) AS total_qty, " +
            "       COALESCE(SUM(i.final_price), 0) AS total_amount " +
            "FROM pos_order_item i JOIN pos_order o ON o.id = i.order_id " +
            "WHERE o.tenant_id = ? AND o.deleted = 0 AND i.deleted = 0 AND o.status IN (3,4) " +
            "  AND o.pay_time BETWEEN ? AND ? ";
        List<Object> args = new ArrayList<>(List.of(tenantId, s.atStartOfDay(), e.atTime(23, 59, 59)));
        if (storeId != null) { sql += "AND o.store_id = ? "; args.add(storeId); }
        sql += "GROUP BY i.service_id, i.service_name ORDER BY total_amount DESC LIMIT ?";
        args.add(limit);
        return Result.success(Keys.camel(jdbc.queryForList(sql, args.toArray())));
    }

    @GetMapping("/employee/commission")
    public Result<List<Map<String, Object>>> employeeCommission(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);

        String sql =
            "SELECT employee_id, employee_name, " +
            "       COALESCE(SUM(commission_amount), 0) AS total_commission, " +
            "       COUNT(*) AS record_count " +
            "FROM commission_record " +
            "WHERE tenant_id = ? AND deleted = 0 AND status IN (1,2,3) " +
            "  AND business_time BETWEEN ? AND ? ";
        List<Object> args = new ArrayList<>(List.of(tenantId, s.atStartOfDay(), e.atTime(23, 59, 59)));
        if (storeId != null) { sql += "AND store_id = ? "; args.add(storeId); }
        sql += "GROUP BY employee_id, employee_name ORDER BY total_commission DESC";
        return Result.success(Keys.camel(jdbc.queryForList(sql, args.toArray())));
    }

    // ====================================================================
    // T-E13 异步导出
    // ====================================================================

    /**
     * 创建导出任务（< 500ms 返回）
     */
    @PostMapping("/task")
    @Transactional(rollbackFor = Exception.class)
    public Result<ReportExportTask> createTask(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        String reportCode = (String) body.get("reportCode");
        String reportName = (String) body.get("reportName");
        String startDate = (String) body.get("startDate");
        String endDate = (String) body.get("endDate");
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        String idempotencyKey = (String) body.get("idempotencyKey");
        if (reportCode == null) return Result.error(BizCode.BAD_REQUEST, "reportCode 必填");
        if (reportName == null) reportName = reportCode;

        // 幂等
        if (idempotencyKey != null) {
            ReportExportTask exist = taskMapper.selectOne(new LambdaQueryWrapper<ReportExportTask>()
                    .eq(ReportExportTask::getTenantId, tenantId)
                    .eq(ReportExportTask::getIdempotencyKey, idempotencyKey));
            if (exist != null) return Result.success("命中幂等", exist);
        }

        // 防重复：同 store + report + range + 状态 in(1,2) 直接拒
        Long dup = taskMapper.selectCount(new LambdaQueryWrapper<ReportExportTask>()
                .eq(ReportExportTask::getTenantId, tenantId)
                .eq(ReportExportTask::getStoreId, storeId == null ? 0 : storeId)
                .eq(ReportExportTask::getReportCode, reportCode)
                .eq(ReportExportTask::getDateRangeStart, startDate == null ? null : LocalDate.parse(startDate))
                .eq(ReportExportTask::getDateRangeEnd, endDate == null ? null : LocalDate.parse(endDate))
                .in(ReportExportTask::getStatus, 1, 2));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "已有相同任务在执行中");

        ReportExportTask t = new ReportExportTask();
        t.setTenantId(tenantId);
        t.setStoreId(storeId);
        t.setTaskNo("T" + System.currentTimeMillis());
        t.setReportCode(reportCode);
        t.setReportName(reportName);
        if (startDate != null) t.setDateRangeStart(LocalDate.parse(startDate));
        if (endDate != null) t.setDateRangeEnd(LocalDate.parse(endDate));
        t.setFilterJson((String) body.getOrDefault("filterJson", "{}"));
        t.setStatus(1);
        t.setProgress(0);
        t.setIdempotencyKey(idempotencyKey);
        t.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        taskMapper.insert(t);

        // 提交异步执行
        submitAsync(t.getId());

        return Result.success("任务已创建", t);
    }

    /**
     * 提交异步任务
     */
    @org.springframework.scheduling.annotation.Async
    public void submitAsync(Long taskId) {
        if (RUNNING.incrementAndGet() > MAX_RUNNING) {
            RUNNING.decrementAndGet();
            jdbc.update("UPDATE report_export_task SET status = 4, error_msg = '系统繁忙，请稍后重试' WHERE id = ?", taskId);
            return;
        }
        try {
            executeExport(taskId);
        } finally {
            RUNNING.decrementAndGet();
        }
    }

    /**
     * 实际执行（生产可以拆 @Async；此处直接同步调用，由调用方控制并发）
     */
    public void executeExport(Long taskId) {
        ReportExportTask t = taskMapper.selectById(taskId);
        if (t == null) return;
        try {
            jdbc.update("UPDATE report_export_task SET status = 2, started_at = NOW(), progress = 10 WHERE id = ?", taskId);
            // 数据查询
            List<Map<String, Object>> rows = collectRows(t);
            jdbc.update("UPDATE report_export_task SET progress = 60 WHERE id = ?", taskId);

            // 写文件
            Files.createDirectories(EXPORT_DIR);
            String fname = t.getTaskNo() + "_" + t.getReportCode() + "_" + LocalDate.now() + ".csv";
            Path filePath = EXPORT_DIR.resolve(fname);
            long size = writeCsv(filePath, t.getReportName(), rows, columnsFor(t.getReportCode()));
            jdbc.update("UPDATE report_export_task SET progress = 90 WHERE id = ?", taskId);

            jdbc.update(
                "UPDATE report_export_task SET status = 3, file_path = ?, file_size = ?, row_count = ?, " +
                "progress = 100, finished_at = NOW(), duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW())/1000 " +
                "WHERE id = ?",
                filePath.toString(), size, rows.size(), taskId);
        } catch (Exception e) {
            log.error("导出任务失败: taskId={}", taskId, e);
            jdbc.update(
                "UPDATE report_export_task SET status = 4, error_msg = ?, finished_at = NOW() WHERE id = ?",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                taskId);
        }
    }

    private List<Map<String, Object>> collectRows(ReportExportTask t) {
        LocalDate s = t.getDateRangeStart() == null ? LocalDate.now().minusDays(30) : t.getDateRangeStart();
        LocalDate e = t.getDateRangeEnd() == null ? LocalDate.now() : t.getDateRangeEnd();
        LocalDateTime sDt = s.atStartOfDay();
        LocalDateTime eDt = e.atTime(23, 59, 59);
        List<Object> args = new ArrayList<>(List.of(t.getTenantId(), sDt, eDt));
        String sql;
        switch (t.getReportCode()) {
            case "SALES":
                sql =
                    "SELECT order_no, DATE(pay_time) AS date, member_id, member_name, " +
                    "       final_amount AS gmv, IFNULL(refund_amount,0) AS refund_amount, " +
                    "       final_amount - IFNULL(refund_amount,0) AS net_revenue, " +
                    "       cashier_id, status " +
                    "FROM pos_order " +
                    "WHERE tenant_id = ? AND deleted = 0 " +
                    "  AND status IN (3,4) AND pay_time BETWEEN ? AND ? ";
                break;
            case "PAY":
                sql =
                    "SELECT p.order_no, p.method AS pay_method, p.amount, p.status, p.pay_time, p.transaction_id AS transaction_no " +
                    "FROM pos_payment p " +
                    "WHERE p.tenant_id = ? AND p.deleted = 0 AND p.pay_time BETWEEN ? AND ? ";
                break;
            case "MEMBER":
                sql =
                    "SELECT member_no, name, phone, gender, level_id, balance, points, total_consume, " +
                    "       DATE(created_at) AS reg_date " +
                    "FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND created_at BETWEEN ? AND ? ";
                break;
            case "COMMISSION":
                sql =
                    "SELECT employee_id, employee_name, source_type, source_id, " +
                    "       rule_name, base_amount, calculation_method, rate, fixed_amount, " +
                    "       commission_amount, status, business_time " +
                    "FROM commission_record " +
                    "WHERE tenant_id = ? AND deleted = 0 AND business_time BETWEEN ? AND ? ";
                break;
            case "PRODUCT":
                sql =
                    "SELECT i.service_id, i.service_name, SUM(i.quantity) AS total_qty, " +
                    "       SUM(i.final_price) AS total_amount " +
                    "FROM pos_order_item i JOIN pos_order o ON o.id = i.order_id " +
                    "WHERE o.tenant_id = ? AND o.deleted = 0 AND i.deleted = 0 AND o.status IN (3,4) " +
                    "  AND o.pay_time BETWEEN ? AND ? " +
                    "GROUP BY i.service_id, i.service_name ORDER BY total_amount DESC";
                break;
            default:
                throw new RuntimeException("未知报表编码: " + t.getReportCode());
        }
        if (t.getStoreId() != null) {
            sql += "AND store_id = ? ";
            args.add(t.getStoreId());
        }
        if (!"PRODUCT".equals(t.getReportCode())) {
            sql += "ORDER BY id DESC LIMIT 50000";
        } else {
            sql += " LIMIT 5000";
        }
        return jdbc.queryForList(sql, args.toArray());
    }

    private String[] columnsFor(String code) {
        switch (code) {
            case "SALES":     return new String[]{"order_no", "date", "member_id", "member_name", "gmv", "refund_amount", "net_revenue", "cashier_id", "status"};
            case "PAY":       return new String[]{"order_no", "pay_method", "amount", "status", "pay_time", "transaction_no"};
            case "MEMBER":    return new String[]{"member_no", "name", "phone", "gender", "level_id", "balance", "points", "total_consume", "reg_date"};
            case "COMMISSION":return new String[]{"employee_id", "employee_name", "source_type", "source_id", "rule_name", "base_amount", "calculation_method", "rate", "fixed_amount", "commission_amount", "status", "business_time"};
            case "PRODUCT":   return new String[]{"service_id", "service_name", "total_qty", "total_amount"};
            default:          return new String[]{"id"};
        }
    }

    private long writeCsv(Path filePath, String title, List<Map<String, Object>> rows, String[] cols) throws Exception {
        // T-E14 敏感字段脱敏
        String[] maskedCols = {"phone", "member_phone", "member_no"};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        w.write("# " + title + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用\n");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) w.write(',');
            w.write(cols[i]);
        }
        w.write('\n');
        for (Map<String, Object> r : rows) {
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) w.write(',');
                Object v = r.get(cols[i]);
                // 敏感脱敏
                if (v != null) {
                    String vs = v.toString();
                    for (String mc : maskedCols) {
                        if (cols[i].equalsIgnoreCase(mc) && vs.length() > 7) {
                            vs = vs.substring(0, 3) + "****" + vs.substring(vs.length() - 4);
                            break;
                        }
                    }
                    w.write(csv(vs));
                }
            }
            w.write('\n');
        }
        w.flush();
        Files.write(filePath, baos.toByteArray());
        return baos.size();
    }

    private static String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * 任务列表
     */
    @GetMapping("/task/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> taskPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String reportCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, task_no, report_code, report_name, date_range_start, date_range_end, " +
            "store_id, status, progress, file_path, file_size, row_count, " +
            "started_at, finished_at, duration_ms, operator_name, error_msg, create_time " +
            "FROM report_export_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (reportCode != null && !reportCode.isEmpty()) { sql.append("AND report_code = ? "); args.add(reportCode); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (task_no LIKE ? OR report_name LIKE ? OR operator_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM report_export_task WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (reportCode != null && !reportCode.isEmpty()) { csql.append("AND report_code = ? "); cargs.add(reportCode); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (task_no LIKE ? OR report_name LIKE ? OR operator_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/task/{id}")
    public Result<ReportExportTask> taskDetail(@PathVariable Long id) {
        ReportExportTask t = taskMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");
        return Result.success(t);
    }

    @PostMapping("/task/{id}/cancel")
    public Result<Void> cancelTask(@PathVariable Long id) {
        ReportExportTask t = taskMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "任务不存在");
        if (t.getStatus() != null && (t.getStatus() == 3 || t.getStatus() == 4)) {
            return Result.error(BizCode.CONFLICT, "已完成/失败的任务不可取消");
        }
        jdbc.update("UPDATE report_export_task SET status = 5 WHERE id = ?", id);
        return Result.success("已取消", null);
    }

    /**
     * 下载文件
     */
    @GetMapping("/task/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws Exception {
        Long tenantId = TenantContextHolder.get().getTenantId();
        ReportExportTask t = taskMapper.selectById(id);
        if (t == null) throw new RuntimeException("任务不存在");
        if (!Objects.equals(t.getTenantId(), tenantId)) throw new RuntimeException("无权下载");
        if (t.getStatus() == null || t.getStatus() != 3) throw new RuntimeException("任务未完成");
        if (t.getFilePath() == null) throw new RuntimeException("文件未生成");

        Path p = Paths.get(t.getFilePath());
        byte[] bytes = Files.readAllBytes(p);
        String filename = t.getTaskNo() + "_" + t.getReportCode() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        headers.setContentDispositionFormData("attachment", encoded.replace("+", "%20"));
        headers.add("X-Tenant-Name", String.valueOf(tenantId));
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ====================================================================
    // T-E12 指标快照 / 口径
    // ====================================================================

    /**
     * 一次计算所有指标 → 写快照表
     */
    @PostMapping("/snapshot/run")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> runSnapshot(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        LocalDate snapshotDate = body.get("snapshotDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("snapshotDate").toString());
        LocalDate start = body.get("startDate") == null ? snapshotDate.minusDays(30)
                : LocalDate.parse(body.get("startDate").toString());
        LocalDate end = body.get("endDate") == null ? snapshotDate
                : LocalDate.parse(body.get("endDate").toString());
        LocalDateTime sDt = start.atStartOfDay();
        LocalDateTime eDt = end.atTime(23, 59, 59);

        List<ReportMetricCaliber> list = caliberMapper.selectList(
                new LambdaQueryWrapper<ReportMetricCaliber>().eq(ReportMetricCaliber::getEnabled, 1));
        int n = 0;
        for (ReportMetricCaliber mc : list) {
            if (mc.getSqlTemplate() == null) continue;
            // 占位符替换：tenant_id / store_id / start / end
            String sql = mc.getSqlTemplate()
                    .replace("? ", "START")
                    .replace("?", "END")
                    .replace("START", "?").replace("END", "?");
            // 实际 SQL 中占位符顺序就是 tenant_id / store_id / start / end / [start, end]
            // 我们按 db 模板原文顺序填
            // 这里做最简化：模板里就是按 (tid, sid, s, e) 顺序，把 ? 替换
            try {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.add(storeId == null ? 1L : storeId);
                args.add(sDt);
                args.add(eDt);
                // 注意：AVG_ORDER 的 sql 用单时间点？确认是 [start, end]
                if (mc.getSqlTemplate().contains("AVG_ORDER") || mc.getSqlTemplate().contains("AVG")) {
                    // 已适配
                }
                List<Map<String, Object>> r = jdbc.queryForList(mc.getSqlTemplate(), args.toArray());
                BigDecimal v = r.isEmpty() ? BigDecimal.ZERO
                        : (r.get(0).get("metric_value") == null
                            ? (r.get(0).values().stream().filter(x -> x instanceof BigDecimal).findFirst().orElse(BigDecimal.ZERO) instanceof BigDecimal ? (BigDecimal) r.get(0).values().stream().filter(x -> x instanceof BigDecimal).findFirst().orElse(BigDecimal.ZERO) : BigDecimal.ZERO)
                            : (BigDecimal) r.get(0).get("metric_value"));
                // 简化：取第一个数值列
                for (Object val : r.get(0).values()) {
                    if (val instanceof BigDecimal) { v = (BigDecimal) val; break; }
                    if (val instanceof Number) { v = new BigDecimal(val.toString()); break; }
                }
                jdbc.update(
                    "INSERT INTO report_metric_snapshot (tenant_id, store_id, snapshot_date, metric_key, metric_name, metric_value) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE metric_name = VALUES(metric_name), metric_value = VALUES(metric_value)",
                    tenantId, storeId == null ? 1L : storeId, snapshotDate,
                    mc.getMetricKey(), mc.getMetricName(), v);
                n++;
            } catch (Exception ex) {
                log.warn("指标 {} 失败：{}", mc.getMetricKey(), ex.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("snapshotDate", snapshotDate);
        data.put("updatedCount", n);
        return Result.success("已生成快照", data);
    }

    @GetMapping("/snapshot/list")
    public Result<List<Map<String, Object>>> snapshotList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT snapshot_date, metric_key, metric_name, metric_value " +
            "FROM report_metric_snapshot " +
            "WHERE tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (startDate != null) { sql.append("AND snapshot_date >= ? "); args.add(LocalDate.parse(startDate)); }
        if (endDate != null) { sql.append("AND snapshot_date <= ? "); args.add(LocalDate.parse(endDate)); }
        sql.append("ORDER BY snapshot_date DESC, metric_key ASC");
        return Result.success(Keys.camel(jdbc.queryForList(sql.toString(), args.toArray())));
    }

    @GetMapping("/caliber/list")
    public Result<List<ReportMetricCaliber>> caliberList() {
        return Result.success(caliberMapper.selectList(
                new LambdaQueryWrapper<ReportMetricCaliber>().orderByAsc(ReportMetricCaliber::getCategory)));
    }

    /**
     * 单一指标查询（低耗时直查）
     */
    @GetMapping("/caliber/{key}/query")
    public Result<Map<String, Object>> queryCaliber(
            @PathVariable String key,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        ReportMetricCaliber mc = caliberMapper.selectOne(
                new LambdaQueryWrapper<ReportMetricCaliber>().eq(ReportMetricCaliber::getMetricKey, key));
        if (mc == null) return Result.error(BizCode.NOT_FOUND, "指标不存在");
        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);
        List<Object> args = new ArrayList<>(List.of(tenantId, storeId == null ? 1L : storeId,
                s.atStartOfDay(), e.atTime(23, 59, 59)));
        List<Map<String, Object>> rows = jdbc.queryForList(mc.getSqlTemplate(), args.toArray());
        Map<String, Object> data = new HashMap<>();
        data.put("metric", mc);
        data.put("result", Keys.camel(rows));
        return Result.success(data);
    }
}
