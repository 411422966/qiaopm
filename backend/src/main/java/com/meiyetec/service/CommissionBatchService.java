package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * R2.1-C
 *  - T-C11 提成规则版本化、生效日期、优先级冲突解决
 *  - T-C12 批次生成、审核、发放
 *  - T-C13 提成冲正（退款订单/撤销核销触发反向提成）
 */
@Service
@RequiredArgsConstructor
public class CommissionBatchService {

    private final CommissionRuleMapper ruleMapper;
    private final CommissionRuleVersionMapper ruleVersionMapper;
    private final CommissionRecordMapper recordMapper;
    private final CommissionSettlementMapper settlementMapper;
    private final CommissionSettlementRecordMapper settlementRecordMapper;
    private final CommissionSettlementBatchMapper batchMapper;
    private final CommissionSettlementBatchItemMapper batchItemMapper;
    private final CommissionReversalMapper reversalMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T-C11 规则版本化
    // ====================================================================

    /**
     * 增量更新规则：自动写入版本快照
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionRule> updateRule(@PathVariable Long id, @RequestBody CommissionRule p) {
        CommissionRule old = ruleMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        p.setId(id);
        p.setRuleName(p.getRuleName() == null ? old.getRuleName() : p.getRuleName());
        p.setBusinessType(p.getBusinessType() == null ? old.getBusinessType() : p.getBusinessType());
        p.setVersionNo((old.getVersionNo() == null ? 0 : old.getVersionNo()) + 1);
        ruleMapper.updateById(p);

        // 写入版本快照
        CommissionRuleVersion v = new CommissionRuleVersion();
        v.setTenantId(p.getTenantId() == null ? old.getTenantId() : p.getTenantId());
        v.setStoreId(p.getStoreId() == null ? old.getStoreId() : p.getStoreId());
        v.setRuleId(id);
        v.setVersionNo(p.getVersionNo());
        v.setRuleName(p.getRuleName());
        v.setBusinessType(p.getBusinessType());
        v.setTargetId(p.getTargetId());
        v.setCalculationMethod(p.getCalculationMethod());
        v.setRate(p.getRate());
        v.setFixedAmount(p.getFixedAmount());
        v.setPriority(p.getPriority());
        v.setEffectiveDate(p.getEffectiveDate());
        v.setExpiryDate(p.getExpiryDate());
        v.setSnapshotReason("UPDATE");
        v.setOperatorName(null);
        ruleVersionMapper.insert(v);
        return Result.success("更新成功（已记录 v" + v.getVersionNo() + "）", ruleMapper.selectById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionRule> createRule(@RequestBody CommissionRule p) {
        if (p.getRuleName() == null || p.getRuleName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "规则名称必填");
        if (p.getBusinessType() == null) return Result.error(BizCode.BAD_REQUEST, "businessType 必填");
        if (p.getEffectiveDate() == null) p.setEffectiveDate(LocalDate.now());
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getPriority() == null) p.setPriority(0);
        if (p.getVersionNo() == null) p.setVersionNo(1);
        if (p.getIsActive() == null) p.setIsActive(1);
        if (p.getTenantId() == null) p.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (p.getStoreId() == null) p.setStoreId(1L);
        ruleMapper.insert(p);

        CommissionRuleVersion v = new CommissionRuleVersion();
        v.setTenantId(p.getTenantId());
        v.setStoreId(p.getStoreId());
        v.setRuleId(p.getId());
        v.setVersionNo(1);
        v.setRuleName(p.getRuleName());
        v.setBusinessType(p.getBusinessType());
        v.setTargetId(p.getTargetId());
        v.setCalculationMethod(p.getCalculationMethod());
        v.setRate(p.getRate());
        v.setFixedAmount(p.getFixedAmount());
        v.setPriority(p.getPriority());
        v.setEffectiveDate(p.getEffectiveDate());
        v.setExpiryDate(p.getExpiryDate());
        v.setSnapshotReason("CREATE");
        v.setOperatorName(null);
        ruleVersionMapper.insert(v);
        return Result.success("创建成功", p);
    }

    public Result<List<CommissionRuleVersion>> ruleVersions(@PathVariable Long id) {
        return Result.success(ruleVersionMapper.selectList(
                new LambdaQueryWrapper<CommissionRuleVersion>()
                        .eq(CommissionRuleVersion::getRuleId, id)
                        .orderByDesc(CommissionRuleVersion::getVersionNo)));
    }

    /**
     * 优先级冲突预览：同 businessType + targetId + 生效期重叠
     */
    public Result<List<Map<String, Object>>> ruleConflictCheck(@RequestParam Long excludeId) {
        String sql =
            "SELECT id, rule_name, business_type, target_id, priority, effective_date, expiry_date " +
            "FROM commission_rule " +
            "WHERE deleted = 0 AND id <> ? AND status = 1 AND is_active = 1 " +
            "ORDER BY business_type, target_id, priority DESC";
        List<Map<String, Object>> all = jdbc.queryForList(sql, excludeId);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Map<String, Object> a = all.get(i);
                Map<String, Object> b = all.get(j);
                if (!Objects.equals(a.get("business_type"), b.get("business_type"))) continue;
                if (!Objects.equals(a.get("target_id"), b.get("target_id"))) continue;
                LocalDate aStart = toDate(a.get("effective_date"));
                LocalDate aEnd = toDate(a.get("expiry_date"));
                LocalDate bStart = toDate(b.get("effective_date"));
                LocalDate bEnd = toDate(b.get("expiry_date"));
                if (aEnd == null) aEnd = LocalDate.of(9999, 12, 31);
                if (bEnd == null) bEnd = LocalDate.of(9999, 12, 31);
                if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("ruleA", a);
                    c.put("ruleB", b);
                    c.put("effectStart", aStart.isAfter(bStart) ? aStart : bStart);
                    c.put("effectEnd", aEnd.isBefore(bEnd) ? aEnd : bEnd);
                    conflicts.add(c);
                }
            }
        }
        return Result.success(conflicts);
    }

    private LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date) return ((java.sql.Date) o).toLocalDate();
        if (o instanceof LocalDate) return (LocalDate) o;
        return LocalDate.parse(o.toString());
    }

    // ====================================================================
    // T-C12 结算批次
    // ====================================================================

    /**
     * 生成批次：扫描 [periodStart, periodEnd] 内的待结算 record，
     * 按员工聚合生成 batch_item。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlementBatch> generateBatch(@RequestBody Map<String, Object> body) {
        LocalDate periodStart = LocalDate.parse(body.get("periodStart").toString());
        LocalDate periodEnd = LocalDate.parse(body.get("periodEnd").toString());
        String salaryMonth = body.get("salaryMonth") == null
                ? periodStart.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : body.get("salaryMonth").toString();
        String batchType = (String) body.getOrDefault("batchType", "MONTHLY");
        String remark = (String) body.get("remark");
        if (periodEnd.isBefore(periodStart))
            return Result.error(BizCode.BAD_REQUEST, "结束日期不能早于开始日期");

        // 防止重复
        Long dup = batchMapper.selectCount(new LambdaQueryWrapper<CommissionSettlementBatch>()
                .eq(CommissionSettlementBatch::getSalaryMonth, salaryMonth)
                .eq(CommissionSettlementBatch::getPeriodStart, periodStart)
                .eq(CommissionSettlementBatch::getPeriodEnd, periodEnd)
                .eq(CommissionSettlementBatch::getStatus, 1));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "已存在草稿批次");

        // 聚合
        String sql =
            "SELECT employee_id, ANY_VALUE(employee_name) AS employee_name, " +
            "       COUNT(*) AS record_count, " +
            "       COALESCE(SUM(commission_amount), 0) AS total_commission " +
            "FROM commission_record " +
            "WHERE deleted = 0 AND status = 1 AND reversal_status = 0 " +
            "  AND business_time BETWEEN ? AND ? " +
            "GROUP BY employee_id";
        List<Map<String, Object>> groups = jdbc.queryForList(sql,
                periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59));

        // 写批次
        CommissionSettlementBatch batch = new CommissionSettlementBatch();
        batch.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        batch.setStoreId(1L);
        batch.setBatchNo("B" + System.currentTimeMillis());
        batch.setBatchType(batchType);
        batch.setPeriodStart(periodStart);
        batch.setPeriodEnd(periodEnd);
        batch.setSalaryMonth(salaryMonth);
        batch.setEmployeeCount(groups.size());
        batch.setRecordCount(0);
        batch.setTotalCommission(BigDecimal.ZERO);
        batch.setTotalAdjustment(BigDecimal.ZERO);
        batch.setTotalPayable(BigDecimal.ZERO);
        batch.setStatus(1);
        batch.setRemark(remark);
        batch.setOperatorId(1L);
        batch.setOperatorName("admin");
        batchMapper.insert(batch);

        BigDecimal totalCommission = BigDecimal.ZERO;
        int totalCount = 0;
        for (Map<String, Object> g : groups) {
            BigDecimal amt = (BigDecimal) g.get("total_commission");
            CommissionSettlementBatchItem item = new CommissionSettlementBatchItem();
            item.setTenantId(batch.getTenantId());
            item.setBatchId(batch.getId());
            item.setEmployeeId(((Number) g.get("employee_id")).longValue());
            item.setEmployeeName((String) g.get("employee_name"));
            item.setBaseSalary(BigDecimal.ZERO);
            item.setCommissionAmount(amt);
            item.setAdjustmentAmount(BigDecimal.ZERO);
            item.setReversalAmount(BigDecimal.ZERO);
            item.setPayableAmount(amt);
            item.setRecordCount(((Number) g.get("record_count")).intValue());
            batchItemMapper.insert(item);

            totalCommission = totalCommission.add(amt);
            totalCount += item.getRecordCount();

            // 把这些 record 标记为"已确认待发放"（占位 = 待发放）
            jdbc.update(
                "UPDATE commission_record SET status = 2 " +
                "WHERE deleted = 0 AND status = 1 AND employee_id = ? " +
                "  AND business_time BETWEEN ? AND ?",
                item.getEmployeeId(), periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59));
        }

        jdbc.update(
            "UPDATE commission_settlement_batch SET record_count = ?, total_commission = ?, total_payable = ? " +
            "WHERE id = ?",
            totalCount, totalCommission, totalCommission, batch.getId());
        batch.setRecordCount(totalCount);
        batch.setTotalCommission(totalCommission);
        batch.setTotalPayable(totalCommission);
        return Result.success("批次已生成", batch);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> batchPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, batch_no, batch_type, period_start, period_end, salary_month, " +
            "employee_count, record_count, total_commission, total_adjustment, total_payable, " +
            "status, confirm_time, confirm_by_name, pay_time, pay_by_name, operator_name, remark, create_time " +
            "FROM commission_settlement_batch WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (salaryMonth != null && !salaryMonth.isEmpty()) { sql.append("AND salary_month = ? "); args.add(salaryMonth); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (batch_no LIKE ? OR remark LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM commission_settlement_batch WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (salaryMonth != null && !salaryMonth.isEmpty()) { csql.append("AND salary_month = ? "); cargs.add(salaryMonth); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (batch_no LIKE ? OR remark LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<Map<String, Object>> batchDetail(@PathVariable Long id) {
        CommissionSettlementBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, employee_id, employee_name, base_salary, commission_amount, " +
            "adjustment_amount, reversal_amount, payable_amount, record_count, remark " +
            "FROM commission_settlement_batch_item WHERE deleted = 0 AND batch_id = ? ORDER BY employee_id",
            id);
        Map<String, Object> data = new HashMap<>();
        data.put("batch", b);
        data.put("items", Keys.camel(items));
        return Result.success(data);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlementBatch> confirmBatch(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CommissionSettlementBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() != null && b.getStatus() != 1) return Result.error(BizCode.CONFLICT, "当前状态不允许确认");
        jdbc.update(
            "UPDATE commission_settlement_batch SET status = 2, confirm_time = NOW(), confirm_by = 1, confirm_by_name = ? " +
            "WHERE id = ? AND status = 1",
            "admin", id);
        b.setStatus(2);
        b.setConfirmTime(LocalDateTime.now());
        b.setConfirmByName("admin");
        return Result.success("已确认", b);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlementBatch> payBatch(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CommissionSettlementBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() == null || b.getStatus() != 2) return Result.error(BizCode.CONFLICT, "请先确认后再发放");
        jdbc.update(
            "UPDATE commission_settlement_batch SET status = 3, pay_time = NOW(), pay_by = 1, pay_by_name = ? " +
            "WHERE id = ? AND status = 2",
            "admin", id);
        // record 标记为已发放
        jdbc.update(
            "UPDATE commission_record SET status = 3 " +
            "WHERE deleted = 0 AND status = 2 " +
            "  AND business_time BETWEEN ? AND ?",
            b.getPeriodStart().atStartOfDay(), b.getPeriodEnd().atTime(23, 59, 59));
        b.setStatus(3);
        b.setPayTime(LocalDateTime.now());
        b.setPayByName("admin");
        return Result.success("已发放", b);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionSettlementBatch> cancelBatch(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CommissionSettlementBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() != null && b.getStatus() == 3) return Result.error(BizCode.CONFLICT, "已发放，不可取消");
        jdbc.update("UPDATE commission_settlement_batch SET status = 4 WHERE id = ?", id);
        // 释放 record
        jdbc.update(
            "UPDATE commission_record SET status = 1 " +
            "WHERE deleted = 0 AND status = 2 " +
            "  AND business_time BETWEEN ? AND ?",
            b.getPeriodStart().atStartOfDay(), b.getPeriodEnd().atTime(23, 59, 59));
        b.setStatus(4);
        return Result.success("已取消", b);
    }

    public Result<Map<String, Object>> adjustItem(@PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        BigDecimal adj = body.get("adjustmentAmount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("adjustmentAmount").toString());
        CommissionSettlementBatchItem it = batchItemMapper.selectById(itemId);
        if (it == null) return Result.error(BizCode.NOT_FOUND, "明细不存在");
        it.setAdjustmentAmount(adj);
        it.setPayableAmount(it.getCommissionAmount().add(adj).subtract(it.getReversalAmount() == null ? BigDecimal.ZERO : it.getReversalAmount()));
        batchItemMapper.updateById(it);

        // 重算批次总额
        recalcBatchTotals(it.getBatchId());
        Map<String, Object> data = new HashMap<>();
        data.put("item", batchItemMapper.selectById(itemId));
        return Result.success("已调整", data);
    }

    private void recalcBatchTotals(Long batchId) {
        Map<String, Object> sum = jdbc.queryForMap(
            "SELECT COUNT(*) AS emp_count, COALESCE(SUM(commission_amount), 0) AS total_commission, " +
            "       COALESCE(SUM(adjustment_amount), 0) AS total_adjustment, " +
            "       COALESCE(SUM(reversal_amount), 0) AS total_reversal, " +
            "       COALESCE(SUM(payable_amount), 0) AS total_payable, " +
            "       COALESCE(SUM(record_count), 0) AS record_count " +
            "FROM commission_settlement_batch_item WHERE deleted = 0 AND batch_id = ?",
            batchId);
        jdbc.update(
            "UPDATE commission_settlement_batch SET employee_count = ?, record_count = ?, " +
            "total_commission = ?, total_adjustment = ?, total_payable = ? WHERE id = ?",
            ((Number) sum.get("emp_count")).intValue(),
            ((Number) sum.get("record_count")).intValue(),
            sum.get("total_commission"),
            sum.get("total_adjustment"),
            ((BigDecimal) sum.get("total_payable")).subtract(
                (BigDecimal) sum.getOrDefault("total_reversal", BigDecimal.ZERO)),
            batchId);
    }

    // ====================================================================
    // T-C13 提成冲正
    // ====================================================================

    /**
     * 触发冲正：退款订单/撤销核销 → 反向提成
     * 输入：sourceType + sourceId + sourceItemId + reason
     *  - 找到 commission_record 中对应 sourceItemId 的记录
     *  - 写 commission_reversal（独立记录）
     *  - commission_record.reversal_status = 1, reversal_id = newId
     *  - 后续批次生成时自动排除 reversal_status = 1 的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionReversal> createReversal(@RequestBody Map<String, Object> body) {
        String sourceType = (String) body.get("sourceType");
        Long sourceId = body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue();
        Long sourceItemId = body.get("sourceItemId") == null ? null : ((Number) body.get("sourceItemId")).longValue();
        String reason = (String) body.get("reason");
        String reversalType = (String) body.getOrDefault("reversalType", "REFUND");
        if (sourceType == null || sourceId == null)
            return Result.error(BizCode.BAD_REQUEST, "sourceType + sourceId 必填");

        // 找原始记录
        String finder = "deleted = 0 AND source_type = ? AND source_id = ?";
        List<Object> args = new ArrayList<>();
        args.add(sourceType);
        args.add(sourceId);
        if (sourceItemId != null) { finder += " AND source_item_id = ?"; args.add(sourceItemId); }

        List<Map<String, Object>> originals = jdbc.queryForList(
            "SELECT id, commission_amount, employee_id, employee_name, reversal_status FROM commission_record WHERE " + finder,
            args.toArray());
        if (originals.isEmpty()) return Result.error(BizCode.NOT_FOUND, "未找到原始提成记录");
        Map<String, Object> orig = originals.get(0);

        if (Integer.parseInt(orig.get("reversal_status").toString()) == 1) {
            return Result.error(BizCode.CONFLICT, "该记录已冲正");
        }

        BigDecimal amt = (BigDecimal) orig.get("commission_amount");
        if (amt == null || amt.signum() == 0) return Result.error(BizCode.CONFLICT, "原金额为 0，无需冲正");

        CommissionReversal r = new CommissionReversal();
        r.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        r.setStoreId(1L);
        r.setReversalNo("RV" + System.currentTimeMillis());
        r.setReversalType(reversalType);
        r.setSourceType(sourceType);
        r.setSourceId(sourceId);
        r.setSourceItemId(sourceItemId);
        r.setOriginalRecordId(((Number) orig.get("id")).longValue());
        r.setEmployeeId(((Number) orig.get("employee_id")).longValue());
        r.setEmployeeName((String) orig.get("employee_name"));
        r.setAmount(amt);
        r.setReason(reason);
        r.setStatus(1); // 待审核
        r.setOperatorName("admin");
        reversalMapper.insert(r);

        // 标记原始记录等待审核
        jdbc.update("UPDATE commission_record SET reversal_status = 1, reversal_id = ? WHERE id = ?",
                r.getId(), orig.get("id"));
        return Result.success("冲正已提交（待审核）", r);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionReversal> approveReversal(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CommissionReversal r = reversalMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "冲正记录不存在");
        if (r.getStatus() != null && r.getStatus() != 1) return Result.error(BizCode.CONFLICT, "已审核");
        jdbc.update(
            "UPDATE commission_reversal SET status = 2, approve_time = NOW(), approve_by = 1, approve_by_name = ? WHERE id = ?",
            "admin", id);
        r.setStatus(2);
        r.setApproveTime(LocalDateTime.now());
        r.setApproveByName("admin");
        return Result.success("已生效", r);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<CommissionReversal> rejectReversal(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        CommissionReversal r = reversalMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "冲正记录不存在");
        if (r.getStatus() != null && r.getStatus() != 1) return Result.error(BizCode.CONFLICT, "已审核");
        // 还原原始记录
        jdbc.update("UPDATE commission_record SET reversal_status = 0, reversal_id = NULL WHERE reversal_id = ?", id);
        jdbc.update(
            "UPDATE commission_reversal SET status = 3, approve_time = NOW(), approve_by = 1, approve_by_name = ?, reason = CONCAT(IFNULL(reason, ''), ' | REJECT') " +
            "WHERE id = ?",
            "admin", id);
        r.setStatus(3);
        return Result.success("已拒绝", r);
    }

    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> reversalPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, reversal_no, reversal_type, source_type, source_id, source_item_id, original_record_id, " +
            "employee_id, employee_name, amount, reason, status, approve_time, approve_by_name, batch_id, " +
            "operator_name, create_time " +
            "FROM commission_reversal WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (reversal_no LIKE ? OR employee_name LIKE ? OR reason LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM commission_reversal WHERE deleted = 0 ");
        List<Object> cargs = new ArrayList<>();
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (reversal_no LIKE ? OR employee_name LIKE ? OR reason LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<Map<String, Object>> reversalStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("pending", safeLong("SELECT COUNT(*) FROM commission_reversal WHERE deleted=0 AND status=1"));
        data.put("approved", safeLong("SELECT COUNT(*) FROM commission_reversal WHERE deleted=0 AND status=2"));
        data.put("rejected", safeLong("SELECT COUNT(*) FROM commission_reversal WHERE deleted=0 AND status=3"));
        data.put("totalAmount", safeLong("SELECT COALESCE(SUM(amount), 0) FROM commission_reversal WHERE deleted=0 AND status=2"));
        return Result.success(data);
    }

    private long safeLong(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (Exception e) { return 0; }
    }

    // ====================================================================
    // 导出
    // ====================================================================

    public ResponseEntity<byte[]> exportBatch(
            @RequestParam(required = false) String salaryMonth,
            @RequestParam(required = false) Integer status) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT batch_no, batch_type, period_start, period_end, salary_month, " +
            "employee_count, record_count, total_commission, total_adjustment, total_payable, " +
            "status, confirm_by_name, pay_by_name, operator_name, remark, create_time " +
            "FROM commission_settlement_batch WHERE deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (salaryMonth != null && !salaryMonth.isEmpty()) { sql.append("AND salary_month = ? "); args.add(salaryMonth); }
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        sql.append("ORDER BY id DESC");
        return csvResponse("提成结算批次", new String[]{
            "批次号", "类型", "期间起", "期间止", "工资月", "员工数", "记录数",
            "总提成", "调整额", "应付额", "状态", "确认人", "发放人", "操作人", "备注", "创建时间"
        }, sql.toString(), args.toArray(), new String[]{
            "batch_no", "batch_type", "period_start", "period_end", "salary_month",
            "employee_count", "record_count", "total_commission", "total_adjustment", "total_payable",
            "status", "confirm_by_name", "pay_by_name", "operator_name", "remark", "create_time"
        });
    }

    public ResponseEntity<byte[]> exportBatchItems(@PathVariable Long id) throws Exception {
        return csvResponse("批次明细", new String[]{
            "员工ID", "员工", "基本工资", "提成", "调整", "冲正", "应付", "记录数", "备注"
        },
        "SELECT employee_id, employee_name, base_salary, commission_amount, adjustment_amount, " +
        "reversal_amount, payable_amount, record_count, remark " +
        "FROM commission_settlement_batch_item WHERE deleted = 0 AND batch_id = ? ORDER BY employee_id",
        new Object[]{id}, new String[]{
            "employee_id", "employee_name", "base_salary", "commission_amount",
            "adjustment_amount", "reversal_amount", "payable_amount", "record_count", "remark"
        });
    }

    public ResponseEntity<byte[]> exportReversal(@RequestParam(required = false) Integer status) throws Exception {
        String sql =
            "SELECT reversal_no, reversal_type, source_type, source_id, source_item_id, " +
            "employee_id, employee_name, amount, reason, status, approve_by_name, operator_name, create_time " +
            "FROM commission_reversal WHERE deleted = 0 " +
            (status == null ? "" : "AND status = ? ") +
            "ORDER BY id DESC";
        List<Object> args = status == null ? new ArrayList<>() : new ArrayList<>(List.of(status));
        return csvResponse("提成冲正", new String[]{
            "冲正号", "类型", "来源", "来源ID", "明细ID", "员工ID", "员工", "金额",
            "原因", "状态", "审核人", "操作人", "创建时间"
        }, sql, args.toArray(), new String[]{
            "reversal_no", "reversal_type", "source_type", "source_id", "source_item_id",
            "employee_id", "employee_name", "amount", "reason", "status", "approve_by_name",
            "operator_name", "create_time"
        });
    }

    private ResponseEntity<byte[]> csvResponse(
            String title, String[] headers, String sql, Object[] args, String[] cols) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        String tenantName = resolveTenantName();
        w.write("# " + title + " · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用\n");
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) head.append(',');
            head.append(headers[i]);
        }
        head.append('\n');
        w.write(head.toString());
        for (Map<String, Object> r : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) line.append(',');
                line.append(csv(r.get(cols[i])));
            }
            line.append('\n');
            w.write(line.toString());
        }
        w.flush();

        String filename = title + "_" + LocalDate.now() + ".csv";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        httpHeaders.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        httpHeaders.add("X-Tenant-Name", new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    private String resolveTenantName() {
        try {
            Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) return String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}
        return "默认租户";
    }

    private static String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
