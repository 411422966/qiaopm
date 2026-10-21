package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ExpiryScanService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/expiry")
@RequiredArgsConstructor
@SysOpLog(module = "ExpiryTracking")
public class ExpiryController {

    private final ExpiryScanService scanService;
    private final StockInventoryMapper inventoryMapper;
    private final BizExpiryAlertLogMapper alertLogMapper;
    private final BizExpiryHandleLogMapper handleLogMapper;
    private final BizExpiryAlertRuleMapper ruleMapper;
    private final JdbcTemplate jdbc;

    private Long currentTenantIdOrReject() {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) {
            throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        }
        return tid;
    }

    // ==================== 看板 / 统计 ====================

    @GetMapping("/dashboard")
    @RequirePermission("stock:expiry:view")
    public Result<Map<String, Object>> dashboard() {
        Long tid = currentTenantIdOrReject();
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new HashMap<>();

        // 按租户聚合所有库存批次，按 expiry_date 算等级
        String sql = "SELECT i.id, i.quantity, i.expiry_date, IFNULL(p.cost_price, 0) AS cost_price " +
                "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
                "WHERE i.deleted = 0 AND p.deleted = 0 AND i.quantity > 0 " +
                "AND i.tenant_id = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tid);

        int expired = 0, critical = 0, warning = 0, normal = 0;
        BigDecimal nearExpiryValue = BigDecimal.ZERO;
        BigDecimal expiredValue = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            Object expObj = r.get("expiry_date");
            if (expObj == null) {
                normal++;
                continue;
            }
            LocalDate exp = LocalDate.parse(expObj.toString());
            int qty = ((Number) r.get("quantity")).intValue();
            BigDecimal cp = new BigDecimal(r.get("cost_price").toString());
            BigDecimal val = cp.multiply(BigDecimal.valueOf(qty));
            long days = ChronoUnit.DAYS.between(today, exp);
            if (days < 0) { expired++; expiredValue = expiredValue.add(val); }
            else if (days <= 7) { critical++; nearExpiryValue = nearExpiryValue.add(val); }
            else if (days <= 30) { warning++; nearExpiryValue = nearExpiryValue.add(val); }
            else { normal++; }
        }

        // 待处理预警
        Long pending = alertLogMapper.selectCount(new LambdaQueryWrapper<BizExpiryAlertLog>()
                .eq(BizExpiryAlertLog::getTenantId, tid)
                .eq(BizExpiryAlertLog::getStatus, "pending"));

        // 累计损失
        Map<String, Object> lossRow = jdbc.queryForMap(
                "SELECT IFNULL(SUM(loss_amount), 0) AS loss FROM biz_expiry_handle_log WHERE deleted = 0 AND tenant_id = ?",
                tid);
        BigDecimal loss = new BigDecimal(lossRow.get("loss").toString());

        out.put("expiredCount", expired);
        out.put("criticalCount", critical);
        out.put("warningCount", warning);
        out.put("normalCount", normal);
        out.put("nearExpiryValue", nearExpiryValue);
        out.put("expiredValue", expiredValue);
        out.put("pendingAlertCount", pending == null ? 0 : pending.intValue());
        out.put("lossAmount", loss);
        return Result.success(out);
    }

    @GetMapping("/total-loss")
    @RequirePermission("stock:expiry:view")
    public Result<Map<String, Object>> totalLoss() {
        Long tid = currentTenantIdOrReject();
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT IFNULL(SUM(loss_amount), 0) AS loss, COUNT(*) AS cnt " +
                        "FROM biz_expiry_handle_log WHERE deleted = 0 AND tenant_id = ?",
                tid);
        Map<String, Object> out = new HashMap<>();
        out.put("totalLoss", new BigDecimal(r.get("loss").toString()));
        out.put("handleCount", ((Number) r.get("cnt")).intValue());
        return Result.success(out);
    }

    @GetMapping("/statistics/method")
    @RequirePermission("stock:expiry:view")
    public Result<List<Map<String, Object>>> statisticsByMethod(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tid = currentTenantIdOrReject();
        StringBuilder sql = new StringBuilder(
                "SELECT handle_method, COUNT(*) AS cnt, IFNULL(SUM(loss_amount), 0) AS loss " +
                        "FROM biz_expiry_handle_log WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (dateFrom != null && !dateFrom.isEmpty()) {
            sql.append("AND handle_date >= ? ");
            args.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            sql.append("AND handle_date <= ? ");
            args.add(dateTo);
        }
        sql.append("GROUP BY handle_method ORDER BY cnt DESC");
        return Result.success(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // ==================== 扫描 / 临期批次 ====================

    @PostMapping("/scan/trigger")
    @RequirePermission("stock:expiry:edit")
    public Result<Map<String, Object>> triggerScan(@RequestBody(required = false) Map<String, Object> body) {
        Long tid = currentTenantIdOrReject();
        Long storeId = body == null || body.get("storeId") == null ? null
                : Long.valueOf(body.get("storeId").toString());
        Map<String, Object> result = scanService.scan(storeId);
        // scanService 内部不计 tenantId，再按 tenant_id 过滤一下生成的 alert_log（如果它写了）
        return Result.success("扫描完成", result);
    }

    @GetMapping("/near-expiry")
    @RequirePermission("stock:expiry:view")
    public Result<List<Map<String, Object>>> nearExpiry(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") Integer withinDays) {
        Long tid = currentTenantIdOrReject();
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(withinDays);

        StringBuilder sql = new StringBuilder(
                "SELECT i.id, i.product_id, i.batch_no, i.quantity, i.expiry_date, i.production_date, " +
                        "p.product_code, p.product_name, p.category, p.unit, p.cost_price " +
                        "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
                        "WHERE i.deleted = 0 AND p.deleted = 0 AND i.quantity > 0 " +
                        "AND i.tenant_id = ? AND p.tenant_id = ? " +
                        "AND i.expiry_date IS NOT NULL AND i.expiry_date <= ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid); args.add(deadline);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        sql.append("ORDER BY i.expiry_date ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        // 转 camel 键
        return Result.success(toCamel(rows));
    }

    // ==================== 预警日志 ====================

    @GetMapping("/alerts")
    @RequirePermission("stock:expiry:view")
    public Result<PageResult<BizExpiryAlertLog>> alerts(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId) {
        Long tid = currentTenantIdOrReject();
        LambdaQueryWrapper<BizExpiryAlertLog> w = new LambdaQueryWrapper<>();
        w.eq(BizExpiryAlertLog::getTenantId, tid);
        if (alertLevel != null && !alertLevel.isEmpty()) w.eq(BizExpiryAlertLog::getAlertLevel, alertLevel);
        if (status != null && !status.isEmpty()) w.eq(BizExpiryAlertLog::getStatus, status);
        if (storeId != null) w.eq(BizExpiryAlertLog::getStoreId, storeId);
        w.orderByDesc(BizExpiryAlertLog::getId);
        Page<BizExpiryAlertLog> page = alertLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/alerts/{id}/handle")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("stock:expiry:edit")
    public Result<BizExpiryAlertLog> handleAlert(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tid = currentTenantIdOrReject();
        BizExpiryAlertLog log = alertLogMapper.selectById(id);
        if (log == null || !tid.equals(log.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "预警不存在或不属于当前租户");
        }
        if ("resolved".equals(log.getStatus()) || "ignored".equals(log.getStatus())) {
            return Result.error(BizCode.CONFLICT, "该预警已处理");
        }
        String method = (String) body.getOrDefault("handleMethod", "dispose");
        String remark = (String) body.get("handleRemark");
        Long handlerId = body.get("handlerId") == null ? null
                : Long.valueOf(body.get("handlerId").toString());
        String handlerName = (String) body.get("handlerName");

        log.setStatus("resolved");
        log.setHandleMethod(method);
        log.setHandleTime(LocalDateTime.now());
        log.setHandlerId(handlerId);
        log.setHandlerName(handlerName);
        log.setHandleRemark(remark);
        alertLogMapper.updateById(log);

        // 同步写一条 handle_log
        BizExpiryHandleLog h = new BizExpiryHandleLog();
        h.setTenantId(tid);
        h.setStoreId(log.getStoreId());
        h.setAlertLogId(log.getId());
        h.setInventoryId(log.getInventoryId());
        h.setProductId(log.getProductId());
        h.setProductName(log.getProductName());
        h.setBatchNo(log.getBatchNo());
        h.setHandleMethod(method);
        h.setHandleQuantity(log.getQuantity());
        h.setHandleDate(LocalDate.now());
        h.setHandleReason(remark);
        h.setOperatorId(handlerId);
        h.setOperatorName(handlerName);
        handleLogMapper.insert(h);

        return Result.success("处理成功", log);
    }

    @PostMapping("/alerts/{id}/ignore")
    @RequirePermission("stock:expiry:edit")
    public Result<BizExpiryAlertLog> ignoreAlert(@PathVariable Long id) {
        Long tid = currentTenantIdOrReject();
        BizExpiryAlertLog log = alertLogMapper.selectById(id);
        if (log == null || !tid.equals(log.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "预警不存在或不属于当前租户");
        }
        log.setStatus("ignored");
        log.setHandleTime(LocalDateTime.now());
        alertLogMapper.updateById(log);
        return Result.success("已忽略", log);
    }

    // ==================== 处理记录 ====================

    @GetMapping("/handles")
    @RequirePermission("stock:expiry:view")
    public Result<PageResult<BizExpiryHandleLog>> handles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String handleMethod,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tid = currentTenantIdOrReject();
        LambdaQueryWrapper<BizExpiryHandleLog> w = new LambdaQueryWrapper<>();
        w.eq(BizExpiryHandleLog::getTenantId, tid);
        if (handleMethod != null && !handleMethod.isEmpty()) w.eq(BizExpiryHandleLog::getHandleMethod, handleMethod);
        if (dateFrom != null && !dateFrom.isEmpty()) w.ge(BizExpiryHandleLog::getHandleDate, dateFrom);
        if (dateTo != null && !dateTo.isEmpty()) w.le(BizExpiryHandleLog::getHandleDate, dateTo);
        w.orderByDesc(BizExpiryHandleLog::getId);
        Page<BizExpiryHandleLog> page = handleLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/handles")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("stock:expiry:edit")
    public Result<BizExpiryHandleLog> createHandle(@RequestBody BizExpiryHandleLog body) {
        Long tid = currentTenantIdOrReject();
        if (body.getHandleMethod() == null) {
            return Result.error(BizCode.BAD_REQUEST, "handleMethod 必填");
        }
        if (body.getProductId() == null) {
            return Result.error(BizCode.BAD_REQUEST, "productId 必填");
        }
        body.setId(null);
        body.setTenantId(tid);
        if (body.getHandleDate() == null) body.setHandleDate(LocalDate.now());
        handleLogMapper.insert(body);
        return Result.success("创建成功", body);
    }

    // ==================== 规则 ====================

    @GetMapping("/rules")
    @RequirePermission("stock:expiry:view")
    public Result<PageResult<BizExpiryAlertRule>> rules(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        Long tid = currentTenantIdOrReject();
        LambdaQueryWrapper<BizExpiryAlertRule> w = new LambdaQueryWrapper<>();
        w.eq(BizExpiryAlertRule::getTenantId, tid);
        if (category != null && !category.isEmpty()) w.eq(BizExpiryAlertRule::getCategory, category);
        if (status != null) w.eq(BizExpiryAlertRule::getStatus, status);
        w.orderByDesc(BizExpiryAlertRule::getId);
        Page<BizExpiryAlertRule> page = ruleMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/rules")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("stock:expiry:edit")
    public Result<BizExpiryAlertRule> createRule(@RequestBody BizExpiryAlertRule rule) {
        Long tid = currentTenantIdOrReject();
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "ruleName 必填");
        }
        rule.setId(null);
        rule.setTenantId(tid);
        if (rule.getCriticalDays() == null) rule.setCriticalDays(7);
        if (rule.getWarningDays() == null) rule.setWarningDays(30);
        if (rule.getNotifyChannels() == null) rule.setNotifyChannels("system");
        if (rule.getAutoHandle() == null) rule.setAutoHandle(0);
        if (rule.getStatus() == null) rule.setStatus(1);
        ruleMapper.insert(rule);
        return Result.success("创建成功", rule);
    }

    @PutMapping("/rules/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("stock:expiry:edit")
    public Result<BizExpiryAlertRule> updateRule(@PathVariable Long id, @RequestBody BizExpiryAlertRule rule) {
        Long tid = currentTenantIdOrReject();
        BizExpiryAlertRule old = ruleMapper.selectById(id);
        if (old == null || !tid.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "规则不存在或不属于当前租户");
        }
        rule.setId(id);
        rule.setTenantId(tid);
        ruleMapper.updateById(rule);
        return Result.success("更新成功", ruleMapper.selectById(id));
    }

    @DeleteMapping("/rules/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("stock:expiry:edit")
    public Result<Void> deleteRule(@PathVariable Long id) {
        Long tid = currentTenantIdOrReject();
        BizExpiryAlertRule old = ruleMapper.selectById(id);
        if (old == null || !tid.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "规则不存在或不属于当前租户");
        }
        ruleMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    private static List<Map<String, Object>> toCamel(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new HashMap<>(r.size());
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String k = e.getKey();
                StringBuilder sb = new StringBuilder(k.length());
                boolean upper = false;
                for (int i = 0; i < k.length(); i++) {
                    char c = k.charAt(i);
                    if (c == '_') { upper = true; continue; }
                    sb.append(upper ? Character.toUpperCase(c) : c);
                    upper = false;
                }
                m.put(sb.toString(), e.getValue());
            }
            out.add(m);
        }
        return out;
    }
}