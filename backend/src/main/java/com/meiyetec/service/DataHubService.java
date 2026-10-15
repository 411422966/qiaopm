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
import java.util.*;
import java.util.stream.Collectors;

/**
 * P5 数据中台
 *
 * 模块：
 * 1. 主数据管理（dim_table + dim_value）
 * 2. 元数据管理（meta_table + meta_column + meta_lineage）
 * 3. 指标体系（metric_definition + metric_snapshot）
 * 4. 看板（dashboard + dashboard_subscription）
 */
@Slf4j
@RestController
@RequestMapping("/datahub")
@RequiredArgsConstructor
public class DataHubService {

    private final DimTableMapper dimTableMapper;
    private final DimValueMapper dimValueMapper;
    private final MetaTableMapper metaTableMapper;
    private final MetaColumnMapper metaColumnMapper;
    private final MetaLineageMapper lineageMapper;
    private final MetricDefinitionMapper metricDefMapper;
    private final MetricSnapshotMapper metricSnapMapper;
    private final DashboardMapper dashboardMapper;
    private final DashboardSubscriptionMapper subscriptionMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T1. 主数据管理
    // ====================================================================

    @PostMapping("/dim")
    @Transactional(rollbackFor = Exception.class)
    public Result<DimTable> createDim(@RequestBody DimTable dim) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (dim.getDimCode() == null || dim.getDimName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (dim.getDimType() == null) dim.setDimType("BUSINESS");
        if (dim.getStatus() == null) dim.setStatus("ACTIVE");
        dim.setTenantId(tenantId);
        dim.setId(null);
        dimTableMapper.insert(dim);
        return Result.success("已创建", dim);
    }

    @GetMapping("/dim")
    public Result<List<DimTable>> listDim() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(dimTableMapper.selectList(new LambdaQueryWrapper<DimTable>()
                .eq(DimTable::getTenantId, tenantId)
                .orderByDesc(DimTable::getId)));
    }

    @GetMapping("/dim/{dimId}/values")
    public Result<List<DimValue>> listDimValues(@PathVariable Long dimId) {
        return Result.success(dimValueMapper.selectList(new LambdaQueryWrapper<DimValue>()
                .eq(DimValue::getDimId, dimId)
                .orderByAsc(DimValue::getSortOrder)
                .orderByAsc(DimValue::getId)));
    }

    @PostMapping("/dim/{dimId}/value")
    public Result<DimValue> addDimValue(@PathVariable Long dimId, @RequestBody DimValue v) {
        if (v.getValueCode() == null || v.getValueName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (v.getStatus() == null) v.setStatus("ACTIVE");
        v.setDimId(dimId);
        v.setId(null);
        dimValueMapper.insert(v);
        return Result.success("已添加", v);
    }

    @PostMapping("/dim/{dimId}/batch-values")
    public Result<Map<String, Object>> batchAddValues(@PathVariable Long dimId,
                                                      @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) body.get("values");
        if (values == null) return Result.error(BizCode.BAD_REQUEST, "values 必填");
        int success = 0;
        for (Map<String, Object> m : values) {
            try {
                DimValue v = new DimValue();
                v.setDimId(dimId);
                v.setValueCode((String) m.get("valueCode"));
                v.setValueName((String) m.get("valueName"));
                v.setParentId(m.get("parentId") == null ? null : ((Number) m.get("parentId")).longValue());
                v.setSortOrder(m.get("sortOrder") == null ? 0 : ((Number) m.get("sortOrder")).intValue());
                v.setStatus("ACTIVE");
                dimValueMapper.insert(v);
                success++;
            } catch (Exception e) {
                log.warn("维度值插入失败: {}", e.getMessage());
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", success);
        return Result.success("导入完成", data);
    }

    // ====================================================================
    // T2. 元数据管理
    // ====================================================================

    @PostMapping("/meta/table")
    @Transactional(rollbackFor = Exception.class)
    public Result<MetaTable> createMetaTable(@RequestBody MetaTable t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getTableCode() == null || t.getTableName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (t.getCategory() == null) t.setCategory("BUSINESS");
        if (t.getStatus() == null) t.setStatus("ACTIVE");
        t.setTenantId(tenantId);
        t.setId(null);
        metaTableMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/meta/table")
    public Result<List<MetaTable>> listMetaTable() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(metaTableMapper.selectList(new LambdaQueryWrapper<MetaTable>()
                .eq(MetaTable::getTenantId, tenantId)
                .orderByDesc(MetaTable::getId)));
    }

    @PostMapping("/meta/table/{id}/sync")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> syncMeta(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MetaTable t = metaTableMapper.selectById(id);
        if (t == null || !t.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "表不存在");

        long t0 = System.currentTimeMillis();
        try {
            // 同步行数
            Long rowCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + t.getTableCode() + " WHERE tenant_id = ?",
                    Long.class, tenantId);
            t.setRowCount(rowCount == null ? 0L : rowCount);

            // 同步字段
            List<Map<String, Object>> cols = jdbc.queryForList("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, t.getTableCode());

            // 删旧字段 + 加新
            jdbc.update("DELETE FROM meta_column WHERE meta_table_id = ?", id);
            int idx = 0;
            for (Map<String, Object> c : cols) {
                MetaColumn mc = new MetaColumn();
                mc.setMetaTableId(id);
                mc.setColumnName((String) c.get("COLUMN_NAME"));
                mc.setColumnType((String) c.get("DATA_TYPE"));
                mc.setDataType((String) c.get("DATA_TYPE"));
                mc.setIsPrimaryKey("PRI".equals(c.get("COLUMN_KEY")) ? 1 : 0);
                mc.setIsNullable("YES".equals(c.get("IS_NULLABLE")) ? 1 : 0);
                mc.setDescription((String) c.get("COLUMN_COMMENT"));
                mc.setSortOrder(idx++);
                metaColumnMapper.insert(mc);
            }

            t.setLastSyncAt(java.time.LocalDateTime.now());
            metaTableMapper.updateById(t);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rowCount", t.getRowCount());
            data.put("columnCount", cols.size());
            data.put("durationMs", System.currentTimeMillis() - t0);
            return Result.success("已同步", data);
        } catch (Exception e) {
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败: " + e.getMessage());
        }
    }

    @GetMapping("/meta/table/{id}/columns")
    public Result<List<MetaColumn>> columns(@PathVariable Long id) {
        return Result.success(metaColumnMapper.selectList(new LambdaQueryWrapper<MetaColumn>()
                .eq(MetaColumn::getMetaTableId, id)
                .orderByAsc(MetaColumn::getSortOrder)));
    }

    @PostMapping("/meta/lineage")
    public Result<MetaLineage> addLineage(@RequestBody MetaLineage l) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (l.getSourceTableCode() == null || l.getTargetTableCode() == null)
            return Result.error(BizCode.BAD_REQUEST, "源/目标表必填");
        l.setTenantId(tenantId);
        l.setId(null);
        if (l.getLineageType() == null) l.setLineageType("ETL");
        lineageMapper.insert(l);
        return Result.success("已添加血缘", l);
    }

    @GetMapping("/meta/lineage/{tableCode}")
    public Result<Map<String, Object>> lineageGraph(@PathVariable String tableCode) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<MetaLineage> upstream = lineageMapper.selectList(new LambdaQueryWrapper<MetaLineage>()
                .eq(MetaLineage::getTenantId, tenantId)
                .eq(MetaLineage::getTargetTableCode, tableCode));
        List<MetaLineage> downstream = lineageMapper.selectList(new LambdaQueryWrapper<MetaLineage>()
                .eq(MetaLineage::getTenantId, tenantId)
                .eq(MetaLineage::getSourceTableCode, tableCode));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableCode", tableCode);
        data.put("upstream", upstream);
        data.put("downstream", downstream);
        return Result.success(data);
    }

    // ====================================================================
    // T3. 指标体系
    // ====================================================================

    @PostMapping("/metric")
    @Transactional(rollbackFor = Exception.class)
    public Result<MetricDefinition> createMetric(@RequestBody MetricDefinition m) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (m.getMetricCode() == null || m.getMetricName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (m.getAggregation() == null) m.setAggregation("SUM");
        if (m.getHigherIsBetter() == null) m.setHigherIsBetter(1);
        if (m.getStatus() == null) m.setStatus("ACTIVE");
        m.setTenantId(tenantId);
        m.setId(null);
        metricDefMapper.insert(m);
        return Result.success("已创建", m);
    }

    @GetMapping("/metric")
    public Result<List<MetricDefinition>> listMetric() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(metricDefMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getTenantId, tenantId)
                .eq(MetricDefinition::getStatus, "ACTIVE")
                .orderByAsc(MetricDefinition::getMetricCategory)
                .orderByDesc(MetricDefinition::getId)));
    }

    /**
     * 计算指标（实时）
     */
    @PostMapping("/metric/{id}/compute")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeMetric(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MetricDefinition m = metricDefMapper.selectById(id);
        if (m == null || !m.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "指标不存在");

        LocalDate date = body == null || body.get("date") == null
                ? LocalDate.now().minusDays(1) : LocalDate.parse(body.get("date").toString());
        String period = body == null || body.get("period") == null ? "DAILY" : body.get("period").toString();

        long t0 = System.currentTimeMillis();
        try {
            String sql = m.getSqlTemplate();
            if (sql == null || sql.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "指标 SQL 未配置");

            // 替换占位符
            sql = sql.replace("{tenant_id}", String.valueOf(tenantId))
                    .replace("{date}", "'" + date + "'")
                    .replace("{start_date}", "'" + date.minusDays(30) + "'")
                    .replace("{end_date}", "'" + date + "'");

            BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
            if (value == null) value = BigDecimal.ZERO;

            // 写快照
            MetricSnapshot snap = new MetricSnapshot();
            snap.setTenantId(tenantId);
            snap.setMetricId(id);
            snap.setMetricCode(m.getMetricCode());
            snap.setSnapshotDate(date);
            snap.setValue(value);
            snap.setPeriod(period);
            snap.setCalcDurationMs((int) (System.currentTimeMillis() - t0));
            metricSnapMapper.insert(snap);

            // 状态评估
            String status = "NORMAL";
            if (m.getTargetValue() != null && m.getWarningValue() != null) {
                boolean good = m.getHigherIsBetter() == 1
                        ? value.compareTo(m.getTargetValue()) >= 0
                        : value.compareTo(m.getTargetValue()) <= 0;
                boolean warning = m.getHigherIsBetter() == 1
                        ? value.compareTo(m.getWarningValue()) < 0
                        : value.compareTo(m.getWarningValue()) > 0;
                status = good ? "GOOD" : warning ? "WARNING" : "NORMAL";
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", value);
            data.put("date", date);
            data.put("status", status);
            data.put("target", m.getTargetValue());
            data.put("warning", m.getWarningValue());
            data.put("calcDurationMs", System.currentTimeMillis() - t0);
            return Result.success("已计算", data);
        } catch (Exception e) {
            return Result.error(BizCode.SYSTEM_ERROR, "计算失败: " + e.getMessage());
        }
    }

    /**
     * 批量计算所有指标
     */
    @PostMapping("/metric/compute-all")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeAll(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate date = body == null || body.get("date") == null
                ? LocalDate.now().minusDays(1) : LocalDate.parse(body.get("date").toString());

        List<MetricDefinition> metrics = metricDefMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getTenantId, tenantId)
                .eq(MetricDefinition::getStatus, "ACTIVE"));

        int success = 0, fail = 0;
        for (MetricDefinition m : metrics) {
            try {
                Map<String, Object> b = new HashMap<>();
                b.put("date", date.toString());
                computeMetric(m.getId(), b);
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("指标 {} 计算失败: {}", m.getMetricCode(), e.getMessage());
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", metrics.size());
        data.put("success", success);
        data.put("fail", fail);
        return Result.success("批量完成", data);
    }

    /**
     * 指标趋势
     */
    @GetMapping("/metric/{code}/trend")
    public Result<List<Map<String, Object>>> trend(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(jdbc.queryForList("""
            SELECT snapshot_date AS date, value
            FROM metric_snapshot
            WHERE tenant_id = ? AND metric_code = ? AND period = 'DAILY'
              AND snapshot_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
            ORDER BY snapshot_date ASC
            """, tenantId, code, days));
    }

    /**
     * 一次性内置指标
     */
    @PostMapping("/metric/init-builtin")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initBuiltin() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> builtins = List.of(
                Map.of("code", "REVENUE_DAILY", "name", "日营收", "category", "BUSINESS",
                        "unit", "元", "sql", "SELECT COALESCE(SUM(pay_amount),0) FROM crm_order WHERE tenant_id = {tenant_id} AND status = 3 AND deleted = 0 AND DATE(pay_time) = {date}",
                        "target", 50000, "warning", 30000),
                Map.of("code", "ORDERS_DAILY", "name", "日订单数", "category", "BUSINESS",
                        "unit", "单", "sql", "SELECT COUNT(*) FROM crm_order WHERE tenant_id = {tenant_id} AND status = 3 AND deleted = 0 AND DATE(pay_time) = {date}",
                        "target", 100, "warning", 50),
                Map.of("code", "MEMBER_VISITS_DAILY", "name", "日访问会员数", "category", "OPERATIONS",
                        "unit", "人", "sql", "SELECT COUNT(DISTINCT member_id) FROM crm_order WHERE tenant_id = {tenant_id} AND status = 3 AND deleted = 0 AND DATE(pay_time) = {date}",
                        "target", 80, "warning", 40),
                Map.of("code", "NEW_MEMBERS_DAILY", "name", "日新增会员", "category", "MARKETING",
                        "unit", "人", "sql", "SELECT COUNT(*) FROM crm_member WHERE tenant_id = {tenant_id} AND deleted = 0 AND DATE(create_time) = {date}",
                        "target", 20, "warning", 10),
                Map.of("code", "CHURN_RATE", "name", "流失率", "category", "OPERATIONS",
                        "unit", "%", "sql", "SELECT COALESCE(SUM(CASE WHEN last_visit_date < DATE_SUB(CURDATE(), INTERVAL 90 DAY) THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 0) FROM crm_member WHERE tenant_id = {tenant_id} AND deleted = 0",
                        "target", 10, "warning", 20, "higherBetter", 0),
                Map.of("code", "PACKAGE_REMAIN_VALUE", "name", "卡项剩余价值", "category", "FINANCE",
                        "unit", "元", "sql", "SELECT COALESCE(SUM(remaining_times * member_price / GREATEST(total_times, 1)), 0) FROM crm_package_order WHERE tenant_id = {tenant_id} AND status = 1 AND remaining_times > 0",
                        "target", 0, "warning", 0),
                Map.of("code", "AI_FORECAST_ACCURACY", "name", "AI 预测准确度", "category", "AI",
                        "unit", "%", "sql", "SELECT COALESCE(AVG(CASE WHEN accuracy_pct IS NOT NULL THEN accuracy_pct ELSE NULL END), 0) FROM ai_forecast_accuracy WHERE tenant_id = {tenant_id} AND DATE(forecast_date) = {date}",
                        "target", 80, "warning", 60),
                Map.of("code", "SHARED_PACKAGE_COUNT", "name", "共享套餐总数", "category", "OPERATIONS",
                        "unit", "套", "sql", "SELECT COUNT(*) FROM shared_package WHERE tenant_id = {tenant_id} AND status = 'ACTIVE'",
                        "target", 50, "warning", 20)
        );

        int created = 0;
        for (Map<String, Object> b : builtins) {
            MetricDefinition m = new MetricDefinition();
            m.setTenantId(tenantId);
            m.setMetricCode((String) b.get("code"));
            m.setMetricName((String) b.get("name"));
            m.setMetricCategory((String) b.get("category"));
            m.setUnit((String) b.get("unit"));
            m.setSqlTemplate((String) b.get("sql"));
            m.setTargetValue(b.get("target") == null ? null : new BigDecimal(b.get("target").toString()));
            m.setWarningValue(b.get("warning") == null ? null : new BigDecimal(b.get("warning").toString()));
            m.setHigherIsBetter((Integer) b.getOrDefault("higherBetter", 1));
            m.setStatus("ACTIVE");
            m.setAggregation("SUM");
            // 跳过已存在的
            MetricDefinition exist = metricDefMapper.selectOne(new LambdaQueryWrapper<MetricDefinition>()
                    .eq(MetricDefinition::getTenantId, tenantId)
                    .eq(MetricDefinition::getMetricCode, m.getMetricCode()));
            if (exist != null) continue;
            metricDefMapper.insert(m);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("total", builtins.size());
        return Result.success("内置指标已初始化", data);
    }

    // ====================================================================
    // T4. 看板
    // ====================================================================

    @PostMapping("/dashboard")
    @Transactional(rollbackFor = Exception.class)
    public Result<Dashboard> createDashboard(@RequestBody Dashboard d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getDashboardCode() == null || d.getDashboardName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (d.getDashboardType() == null) d.setDashboardType("OPS");
        if (d.getStatus() == null) d.setStatus("ACTIVE");
        if (Integer.valueOf(1).equals(d.getIsDefault())) {
            jdbc.update("UPDATE dashboard SET is_default = 0 WHERE tenant_id = ?", tenantId);
        }
        d.setTenantId(tenantId);
        d.setId(null);
        dashboardMapper.insert(d);
        return Result.success("已创建", d);
    }

    @GetMapping("/dashboard")
    public Result<List<Dashboard>> listDashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(dashboardMapper.selectList(new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getTenantId, tenantId)
                .eq(Dashboard::getStatus, "ACTIVE")
                .orderByDesc(Dashboard::getIsDefault)
                .orderByDesc(Dashboard::getId)));
    }

    /**
     * 综合运营看板（实时）
     */
    @GetMapping("/dashboard/ops")
    public Result<Map<String, Object>> opsDashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // 1. 营收、订单、客流
        for (String[] metric : new String[][]{
                {"REVENUE_DAILY", "今日营收"},
                {"ORDERS_DAILY", "今日订单"},
                {"MEMBER_VISITS_DAILY", "今日访客"},
                {"NEW_MEMBERS_DAILY", "今日新增"}
        }) {
            Map<String, Object> snap = latestSnapshot(tenantId, metric[0], today);
            snap.put("label", metric[1]);
            data.put(metric[0], snap);
        }

        // 2. 7 天趋势
        List<Map<String, Object>> trend = jdbc.queryForList("""
            SELECT metric_code, snapshot_date, value
            FROM metric_snapshot
            WHERE tenant_id = ? AND period = 'DAILY'
              AND snapshot_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
              AND metric_code IN ('REVENUE_DAILY','ORDERS_DAILY','MEMBER_VISITS_DAILY')
            ORDER BY snapshot_date ASC
            """, tenantId);
        data.put("trend", trend);

        // 3. 套餐库存
        try {
            long activePkg = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shared_package WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Long.class, tenantId);
            data.put("activeSharedPackages", activePkg);
        } catch (Exception e) {
            data.put("activeSharedPackages", 0);
        }

        // 4. 待办（来自 R2.3 任务流）
        try {
            long pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM r2_task_flow WHERE tenant_id = ? AND status IN ('PENDING','ASSIGNED')",
                    Long.class, tenantId);
            data.put("pendingTasks", pending);
        } catch (Exception e) {
            data.put("pendingTasks", 0);
        }

        return Result.success(data);
    }

    private Map<String, Object> latestSnapshot(Long tenantId, String code, LocalDate date) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT value, snapshot_date
                FROM metric_snapshot
                WHERE tenant_id = ? AND metric_code = ? AND period = 'DAILY'
                ORDER BY snapshot_date DESC LIMIT 1
                """, tenantId, code);
            if (rows.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("value", 0);
                m.put("date", date);
                return m;
            }
            return rows.get(0);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", 0);
            return m;
        }
    }

    // ====================================================================
    // 看板订阅
    // ====================================================================

    @PostMapping("/dashboard/{id}/subscribe")
    @Transactional(rollbackFor = Exception.class)
    public Result<DashboardSubscription> subscribe(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long userId = body == null || body.get("userId") == null ? null
                : ((Number) body.get("userId")).longValue();
        if (userId == null) return Result.error(BizCode.BAD_REQUEST, "userId 必填");

        DashboardSubscription sub = new DashboardSubscription();
        sub.setTenantId(tenantId);
        sub.setDashboardId(id);
        sub.setSubscriberId(userId);
        sub.setFrequency(body == null || body.get("frequency") == null ? "DAILY" : body.get("frequency").toString());
        sub.setChannel(body == null || body.get("channel") == null ? "IN_APP" : body.get("channel").toString());
        sub.setStatus("ACTIVE");
        subscriptionMapper.insert(sub);
        return Result.success("已订阅", sub);
    }

    @GetMapping("/dashboard/{id}/subscribers")
    public Result<List<DashboardSubscription>> subscribers(@PathVariable Long id) {
        return Result.success(subscriptionMapper.selectList(new LambdaQueryWrapper<DashboardSubscription>()
                .eq(DashboardSubscription::getDashboardId, id)));
    }

    // ====================================================================
    // T5. 综合健康报告
    // ====================================================================

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        try {
            long dimCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dim_table WHERE tenant_id = ?", Long.class, tenantId);
            long metaCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM meta_table WHERE tenant_id = ?", Long.class, tenantId);
            long metricCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM metric_definition WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Long.class, tenantId);
            long snapCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM metric_snapshot WHERE tenant_id = ?",
                    Long.class, tenantId);
            long dashboardCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dashboard WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Long.class, tenantId);
            long lineageCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM meta_lineage WHERE tenant_id = ?",
                    Long.class, tenantId);

            data.put("dimensions", dimCount);
            data.put("metaTables", metaCount);
            data.put("metrics", metricCount);
            data.put("snapshots", snapCount);
            data.put("dashboards", dashboardCount);
            data.put("lineages", lineageCount);
            data.put("maturity", computeMaturity(dimCount, metaCount, metricCount, dashboardCount));
        } catch (Exception e) {
            data.put("error", e.getMessage());
        }
        return Result.success(data);
    }

    private String computeMaturity(long dim, long meta, long metric, long dash) {
        int score = 0;
        if (dim >= 3) score += 25;
        if (meta >= 5) score += 25;
        if (metric >= 5) score += 25;
        if (dash >= 1) score += 25;
        return score >= 75 ? "ADVANCED" : score >= 50 ? "INTERMEDIATE" : "BASIC";
    }
}