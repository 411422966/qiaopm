package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CohortDefinition;
import com.meiyetec.entity.CohortResult;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CohortDefinitionMapper;
import com.meiyetec.mapper.CohortResultMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Cohort 队列分析（FEATURE_COMPARISON_MATRIX.md 253-264 P1-14）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CohortService {

    private final CohortDefinitionMapper defMapper;
    private final CohortResultMapper resultMapper;

    @Autowired
    private JdbcTemplate jdbc;

    public Result<CohortDefinition> createDef(@RequestBody CohortDefinition d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getCohortCode() == null || d.getCohortName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (d.getCohortType() == null) d.setCohortType("MONTHLY");
        d.setTenantId(tenantId);
        d.setId(null);
        defMapper.insert(d);
        return Result.success("已创建", d);
    }

    public Result<List<CohortDefinition>> listDefs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(defMapper.selectList(new LambdaQueryWrapper<CohortDefinition>()
                .eq(CohortDefinition::getTenantId, tenantId)
                .orderByDesc(CohortDefinition::getId)));
    }

    public Result<CohortDefinition> updateDef(@PathVariable Long id, @RequestBody CohortDefinition d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        d.setId(id);
        d.setTenantId(tenantId);
        defMapper.updateById(d);
        return Result.success("已更新", defMapper.selectById(id));
    }

    /**
     * 触发分析：按月分桶统计留存
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> analyze(@PathVariable Long cohortId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CohortDefinition def = defMapper.selectById(cohortId);
        if (def == null) return Result.error(BizCode.NOT_FOUND, "队列不存在");

        String startDate = "2024-01-01";
        String endDate = LocalDate.now().toString();
        try {
            Object sd = def.getClass().getMethod("getStartDate").invoke(def);
            Object ed = def.getClass().getMethod("getEndDate").invoke(def);
            if (sd != null) startDate = sd.toString();
            if (ed != null) endDate = ed.toString();
        } catch (Exception ignore) {}

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATE_FORMAT(o.pay_time, '%Y-%m') AS cohort_month, " +
                "       COUNT(DISTINCT o.member_id) AS size, " +
                "       SUM(o.pay_amount) AS total_amount " +
                "FROM crm_order o " +
                "WHERE o.tenant_id = ? AND o.status = 3 AND o.deleted = 0 " +
                "  AND o.pay_time BETWEEN ? AND ? " +
                "GROUP BY cohort_month",
                tenantId, startDate, endDate);

        int total = 0;
        int idx = 0;
        for (Map<String, Object> r : rows) {
            CohortResult cr = new CohortResult();
            cr.setTenantId(tenantId);
            cr.setCohortId(cohortId);
            cr.setCohortCode(def.getCohortCode());
            cr.setCohortPeriod((String) r.get("cohort_month"));
            cr.setSize(((Number) r.get("size")).intValue());
            cr.setPeriodIndex(idx++);
            cr.setValue(r.get("total_amount") == null ? null : new java.math.BigDecimal(r.get("total_amount").toString()));
            cr.setValueType("REVENUE");
            resultMapper.insert(cr);
            total++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("computed", total);
        return Result.success("已分析", data);
    }

    public Result<List<CohortResult>> listResults(@PathVariable Long cohortId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(resultMapper.selectList(new LambdaQueryWrapper<CohortResult>()
                .eq(CohortResult::getTenantId, tenantId)
                .eq(CohortResult::getCohortId, cohortId)
                .orderByAsc(CohortResult::getCohortPeriod)));
    }

    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalCohorts", defMapper.selectCount(new LambdaQueryWrapper<CohortDefinition>()
                    .eq(CohortDefinition::getTenantId, tenantId)));
            data.put("totalResults", resultMapper.selectCount(new LambdaQueryWrapper<CohortResult>()
                    .eq(CohortResult::getTenantId, tenantId)));
        } catch (Exception ignore) {}
        return Result.success(data);
    }
}