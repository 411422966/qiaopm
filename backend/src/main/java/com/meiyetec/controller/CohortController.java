package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CohortDefinition;
import com.meiyetec.entity.CohortResult;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CohortDefinitionMapper;
import com.meiyetec.mapper.CohortResultMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Cohort 队列分析 controller（/cohort/*）
 *
 *  - cohort/index.vue 调此命名空间（通过 src/api/cohort.ts）
 *  - 数据源 cohort_definition + cohort_result（均含 tenant_id）
 *  - 简化队列分析：按 cohortPeriod（yyyy-MM）月分组，对每期新人按 0/30/60/90/... 周期计算回访/复购率
 *  - 不接入 ML pipeline，SQL 聚合
 */
@RestController
@RequestMapping("/cohort")
@RequiredArgsConstructor
@SysOpLog(module = "Cohort 队列分析")
public class CohortController {

    private final CohortDefinitionMapper defMapper;
    private final CohortResultMapper resultMapper;
    private final PosOrderMapper orderMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    // ====================================================================
    // 队列定义 CRUD
    // ====================================================================

    @GetMapping("/definition/list")
    public Result<List<CohortDefinition>> listDefinitions() {
        Long t = tid();
        return Result.success(defMapper.selectList(new LambdaQueryWrapper<CohortDefinition>()
                .eq(CohortDefinition::getTenantId, t)
                .orderByDesc(CohortDefinition::getId)));
    }

    @GetMapping("/definition/{id}")
    public Result<CohortDefinition> getDefinition(@PathVariable Long id) {
        Long t = tid();
        CohortDefinition d = defMapper.selectById(id);
        if (d == null || !t.equals(d.getTenantId())) return Result.error(BizCode.NOT_FOUND, "队列不存在");
        return Result.success(d);
    }

    @PostMapping("/definition")
    @Transactional(rollbackFor = Exception.class)
    public Result<CohortDefinition> createDefinition(@RequestBody CohortDefinition d) {
        Long t = tid();
        if (d.getCohortName() == null || d.getCohortName().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "cohortName 必填");
        }
        d.setId(null);
        d.setTenantId(t);
        if (d.getCohortCode() == null) d.setCohortCode("CO" + System.currentTimeMillis());
        if (d.getCohortType() == null) d.setCohortType("MONTHLY");
        if (d.getMetric() == null) d.setMetric("REORDER_RATE");
        defMapper.insert(d);
        return Result.success("已创建", d);
    }

    @PostMapping("/definition/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<CohortDefinition> updateDefinition(@PathVariable Long id, @RequestBody CohortDefinition patch) {
        Long t = tid();
        CohortDefinition old = defMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "队列不存在");
        if (patch.getCohortName() != null) old.setCohortName(patch.getCohortName());
        if (patch.getCohortType() != null) old.setCohortType(patch.getCohortType());
        if (patch.getMetric() != null) old.setMetric(patch.getMetric());
        if (patch.getTargetDim() != null) old.setTargetDim(patch.getTargetDim());
        if (patch.getDescription() != null) old.setDescription(patch.getDescription());
        defMapper.updateById(old);
        return Result.success("已更新", old);
    }

    @DeleteMapping("/definition/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDefinition(@PathVariable Long id) {
        Long t = tid();
        CohortDefinition old = defMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "队列不存在");
        defMapper.deleteById(id);
        // 同时清理结果
        resultMapper.delete(new LambdaQueryWrapper<CohortResult>()
                .eq(CohortResult::getTenantId, t)
                .eq(CohortResult::getCohortId, id));
        return Result.success("已删除", null);
    }

    // ====================================================================
    // 队列分析（执行 + 结果查询）
    // ====================================================================

    /**
     * 触发队列分析。
     * 算法：
     *  - 取最近 6 个月（period = yyyy-MM）有首单支付的会员作为 cohort
     *  - 按 metric（REORDER_RATE / REVENUE / RETENTION）输出每个 cohortPeriod 在每个 periodIndex 的指标
     */
    @PostMapping("/analyze/{cohortId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> analyze(@PathVariable Long cohortId) {
        Long t = tid();
        CohortDefinition def = defMapper.selectById(cohortId);
        if (def == null || !t.equals(def.getTenantId())) return Result.error(BizCode.NOT_FOUND, "队列不存在");

        // 清旧结果
        resultMapper.delete(new LambdaQueryWrapper<CohortResult>()
                .eq(CohortResult::getTenantId, t)
                .eq(CohortResult::getCohortId, cohortId));

        // 拉所有有效订单（按 payTime 排序）
        List<PosOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PosOrder>()
                .eq(PosOrder::getTenantId, t)
                .eq(PosOrder::getStatus, 2)
                .isNotNull(PosOrder::getPayTime)
                .isNotNull(PosOrder::getMemberId)
                .orderByAsc(PosOrder::getPayTime));

        // 每个会员的首单日期（月份）作为 cohort 起始
        Map<Long, LocalDate> firstByMember = new LinkedHashMap<>();
        for (PosOrder o : orders) {
            firstByMember.putIfAbsent(o.getMemberId(), o.getPayTime().toLocalDate());
        }

        // 按 cohortPeriod（yyyy-MM）分桶
        Map<String, Set<Long>> cohortBuckets = new LinkedHashMap<>();
        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
        for (Map.Entry<Long, LocalDate> e : firstByMember.entrySet()) {
            String key = e.getValue().format(ym);
            cohortBuckets.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(e.getKey());
        }

        // 决定指标
        String metric = def.getMetric() == null ? "REORDER_RATE" : def.getMetric().toUpperCase();
        int maxPeriods = 6;
        int generated = 0;
        for (Map.Entry<String, Set<Long>> e : cohortBuckets.entrySet()) {
            String period = e.getKey();
            Set<Long> cohortMembers = e.getValue();
            int cohortSize = cohortMembers.size();
            // 计算每个 periodIndex
            LocalDate baseMonth = LocalDate.parse(period + "-01");
            for (int i = 0; i < maxPeriods; i++) {
                LocalDate windowStart = baseMonth.plusMonths(i);
                LocalDate windowEnd = baseMonth.plusMonths(i + 1);
                long activeCount = 0;
                BigDecimal totalSpend = BigDecimal.ZERO;
                for (PosOrder o : orders) {
                    if (!cohortMembers.contains(o.getMemberId())) continue;
                    LocalDate d = o.getPayTime().toLocalDate();
                    if (d.compareTo(windowStart) >= 0 && d.compareTo(windowEnd) < 0) {
                        activeCount++;
                        if (o.getFinalAmount() != null) totalSpend = totalSpend.add(o.getFinalAmount());
                    }
                }
                BigDecimal value;
                String valueType;
                if ("REVENUE".equals(metric)) {
                    value = cohortSize == 0 ? BigDecimal.ZERO
                            : totalSpend.divide(new BigDecimal(cohortSize), 2, RoundingMode.HALF_UP);
                    valueType = "AMOUNT_PER_USER";
                } else {
                    // 默认 REORDER_RATE（活跃率）
                    value = cohortSize == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(activeCount)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(new BigDecimal(cohortSize), 2, RoundingMode.HALF_UP);
                    valueType = "PERCENT";
                }
                CohortResult r = new CohortResult();
                r.setTenantId(t);
                r.setCohortId(cohortId);
                r.setCohortCode(def.getCohortCode());
                r.setCohortPeriod(period);
                r.setSize(cohortSize);
                r.setPeriodIndex(i);
                r.setValue(value);
                r.setValueType(valueType);
                resultMapper.insert(r);
                generated++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cohortId", cohortId);
        out.put("generated", generated);
        out.put("metric", metric);
        out.put("analyzedAt", LocalDate.now());
        return Result.success("队列分析完成", out);
    }

    @GetMapping("/result/{cohortId}")
    public Result<List<CohortResult>> listResults(@PathVariable Long cohortId) {
        Long t = tid();
        CohortDefinition d = defMapper.selectById(cohortId);
        if (d == null || !t.equals(d.getTenantId())) return Result.error(BizCode.NOT_FOUND, "队列不存在");
        return Result.success(resultMapper.selectList(new LambdaQueryWrapper<CohortResult>()
                .eq(CohortResult::getTenantId, t)
                .eq(CohortResult::getCohortId, cohortId)
                .orderByAsc(CohortResult::getCohortPeriod)
                .orderByAsc(CohortResult::getPeriodIndex)));
    }

    /**
     * 队列分析总览（按队列聚合）
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long t = tid();
        List<CohortDefinition> defs = defMapper.selectList(new LambdaQueryWrapper<CohortDefinition>()
                .eq(CohortDefinition::getTenantId, t));
        List<CohortResult> results = resultMapper.selectList(new LambdaQueryWrapper<CohortResult>()
                .eq(CohortResult::getTenantId, t));
        BigDecimal avgRetention = BigDecimal.ZERO;
        long high = 0;
        long low = 0;
        if (!results.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            long pctCount = 0;
            for (CohortResult r : results) {
                if ("PERCENT".equalsIgnoreCase(r.getValueType()) && r.getValue() != null) {
                    sum = sum.add(r.getValue());
                    pctCount++;
                    if (r.getValue().compareTo(new BigDecimal("50")) >= 0) high++;
                    else if (r.getValue().compareTo(new BigDecimal("20")) < 0) low++;
                }
            }
            if (pctCount > 0) avgRetention = sum.divide(new BigDecimal(pctCount), 2, RoundingMode.HALF_UP);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("definitions", defs.size());
        out.put("resultRows", results.size());
        out.put("highRetentionRows", high);
        out.put("lowRetentionRows", low);
        out.put("avgRetention", avgRetention);
        out.put("snapshotsAt", LocalDate.now());
        return Result.success(out);
    }
}