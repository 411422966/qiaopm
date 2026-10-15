package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.MultiAccountSplitRecord;
import com.meiyetec.entity.MultiAccountSplitRecordDetail;
import com.meiyetec.entity.MultiAccountSplitRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MultiAccountSplitRecordDetailMapper;
import com.meiyetec.mapper.MultiAccountSplitRecordMapper;
import com.meiyetec.mapper.MultiAccountSplitRuleMapper;
import com.meiyetec.service.impl.MultiAccountSplitServiceImpl;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * M02-04 多账户分账 Controller
 * <p>端点：
 * <ul>
 *   <li>规则：CRUD（POST/GET/PUT/DELETE /m02/split/rule）</li>
 *   <li>执行：手动触发（POST /m02/split/execute/{orderId}）</li>
 *   <li>记录：分账记录 + 明细查询（GET /m02/split/record）</li>
 *   <li>看板：统计（GET /m02/split/stats）</li>
 * </ul>
 *
 * <p>自动触发：OrderPaidEventListener 调用 splitOrderAsync，
 * 在事务 AFTER_COMMIT 后写分账记录（幂等，不重复）。
 */
@RestController
@RequestMapping("/m02/split")
@RequiredArgsConstructor
public class MultiAccountSplitController {

    private final MultiAccountSplitRuleMapper ruleMapper;
    private final MultiAccountSplitRecordMapper recordMapper;
    private final MultiAccountSplitRecordDetailMapper detailMapper;
    private final MultiAccountSplitServiceImpl splitService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    // ==================== 分账规则 CRUD ====================

    @PostMapping("/rule")
    public Result<MultiAccountSplitRule> createRule(@RequestBody MultiAccountSplitRule rule) {
        Long t = tid();
        rule.setTenantId(t);
        rule.setDeleted(0);
        if (rule.getCreateBy() == null) rule.setCreateBy(TenantContextHolder.get().getUserId());
        validateRule(rule);
        ruleMapper.insert(rule);
        return Result.success("分账规则创建成功", rule);
    }

    @GetMapping("/rule/list")
    public Result<List<MultiAccountSplitRule>> listRules(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<MultiAccountSplitRule> q = new LambdaQueryWrapper<>();
        q.eq(MultiAccountSplitRule::getTenantId, t);
        if (storeId != null) q.eq(MultiAccountSplitRule::getStoreId, storeId);
        if (status != null) q.eq(MultiAccountSplitRule::getStatus, status);
        q.orderByDesc(MultiAccountSplitRule::getPriority)
         .orderByDesc(MultiAccountSplitRule::getId);
        return Result.success(ruleMapper.selectList(q));
    }

    @PutMapping("/rule/{id}")
    public Result<MultiAccountSplitRule> updateRule(@PathVariable Long id, @RequestBody MultiAccountSplitRule rule) {
        Long t = tid();
        MultiAccountSplitRule existing = ruleMapper.selectById(id);
        if (existing == null || !t.equals(existing.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "规则不存在");
        }
        validateRule(rule);
        existing.setRuleName(rule.getRuleName());
        existing.setStoreId(rule.getStoreId());
        existing.setRuleScope(rule.getRuleScope());
        existing.setServiceId(rule.getServiceId());
        existing.setProviderWalletId(rule.getProviderWalletId());
        existing.setProviderRatio(rule.getProviderRatio());
        existing.setBranchWalletId(rule.getBranchWalletId());
        existing.setBranchRatio(rule.getBranchRatio());
        existing.setEmployeeWalletId(rule.getEmployeeWalletId());
        existing.setEmployeeRatio(rule.getEmployeeRatio());
        existing.setPlatformFeeRatio(rule.getPlatformFeeRatio());
        existing.setEffectiveDate(rule.getEffectiveDate());
        existing.setExpiryDate(rule.getExpiryDate());
        existing.setPriority(rule.getPriority() == null ? 0 : rule.getPriority());
        existing.setStatus(rule.getStatus() == null ? 1 : rule.getStatus());
        existing.setRemark(rule.getRemark());
        if (rule.getUpdateBy() == null) existing.setUpdateBy(TenantContextHolder.get().getUserId());
        ruleMapper.updateById(existing);
        return Result.success("分账规则更新成功", existing);
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        Long t = tid();
        MultiAccountSplitRule existing = ruleMapper.selectById(id);
        if (existing == null || !t.equals(existing.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "规则不存在");
        }
        ruleMapper.deleteById(id);
        return Result.success("分账规则删除成功", null);
    }

    // ==================== 手动执行分账 ====================

    /**
     * 手动触发分账（先记录，再执行划拨）
     */
    @PostMapping("/execute/{orderId:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> executeSplit(@PathVariable Long orderId) {
        Long t = tid();
        Long userId = TenantContextHolder.get().getUserId();
        String userName = TenantContextHolder.get().getUsername();
        Long opId = userId == null ? 0L : userId;
        String opName = userName == null ? "SYSTEM" : userName;
        Map<String, Object> result = splitService.splitOrderSync(
                new com.meiyetec.entity.PosOrder() {{ setId(orderId); setTenantId(t); }},
                opId, opName);
        return Result.success("分账执行完成", result);
    }

    // ==================== 分账记录查询 ====================

    @GetMapping("/record/page")
    public Result<Page<Map<String, Object>>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long t = tid();
        LambdaQueryWrapper<MultiAccountSplitRecord> q = new LambdaQueryWrapper<>();
        q.eq(MultiAccountSplitRecord::getTenantId, t);
        if (storeId != null) q.eq(MultiAccountSplitRecord::getStoreId, storeId);
        if (orderId != null) q.eq(MultiAccountSplitRecord::getOrderId, orderId);
        if (status != null) q.eq(MultiAccountSplitRecord::getStatus, status);
        q.orderByDesc(MultiAccountSplitRecord::getSplitTime);
        Page<MultiAccountSplitRecord> pg = new Page<>(pageNum, pageSize);
        Page<MultiAccountSplitRecord> result = recordMapper.selectPage(pg, q);
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(this::toMap).toList());
        return Result.success(mapPage);
    }

    @GetMapping("/record/{id}/detail")
    public Result<List<MultiAccountSplitRecordDetail>> getDetails(@PathVariable Long id) {
        Long t = tid();
        MultiAccountSplitRecord r = recordMapper.selectById(id);
        if (r == null || !t.equals(r.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "分账记录不存在");
        }
        List<MultiAccountSplitRecordDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<MultiAccountSplitRecordDetail>()
                        .eq(MultiAccountSplitRecordDetail::getSplitRecordId, id)
                        .orderByAsc(MultiAccountSplitRecordDetail::getId));
        return Result.success(details);
    }

    // ==================== 看板统计 ====================

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String month) {
        Long t = tid();
        LambdaQueryWrapper<MultiAccountSplitRecord> q = new LambdaQueryWrapper<>();
        q.eq(MultiAccountSplitRecord::getTenantId, t);
        if (storeId != null) q.eq(MultiAccountSplitRecord::getStoreId, storeId);
        if (month != null && !month.isBlank()) {
            q.apply("DATE_FORMAT(split_time, '%Y-%m') = {0}", month);
        }
        List<MultiAccountSplitRecord> records = recordMapper.selectList(q);
        long total = records.size();
        long success = records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 2).count();
        long partial = records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 4).count();
        long failed = records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 3).count();
        BigDecimal totalAmount = records.stream()
                .map(r -> r.getOrderAmount() == null ? BigDecimal.ZERO : r.getOrderAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFee = records.stream()
                .map(r -> r.getFeeAmount() == null ? BigDecimal.ZERO : r.getFeeAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSplit = records.stream()
                .map(r -> r.getSplitTotal() == null ? BigDecimal.ZERO : r.getSplitTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Result.success(Map.of(
                "totalRecords", total,
                "successRecords", success,
                "partialRecords", partial,
                "failedRecords", failed,
                "totalOrderAmount", totalAmount,
                "totalFee", totalFee,
                "totalSplitAmount", totalSplit
        ));
    }

    // ==================== 内部辅助 ====================

    private void validateRule(MultiAccountSplitRule r) {
        if (r.getRuleName() == null || r.getRuleName().isBlank()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "规则名称不能为空");
        }
        if (r.getRuleScope() == null) r.setRuleScope("ALL");
        if (!"ALL".equals(r.getRuleScope()) && !"SERVICE".equals(r.getRuleScope())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "ruleScope 只支持 ALL 或 SERVICE");
        }
        if ("SERVICE".equals(r.getRuleScope()) && r.getServiceId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "ruleScope=SERVICE 时 serviceId 必填");
        }
        // 至少要配置一个接收方
        boolean hasProvider = r.getProviderWalletId() != null && nz(r.getProviderRatio()).compareTo(BigDecimal.ZERO) > 0;
        boolean hasBranch = r.getBranchWalletId() != null && nz(r.getBranchRatio()).compareTo(BigDecimal.ZERO) > 0;
        boolean hasEmployee = r.getEmployeeWalletId() != null && nz(r.getEmployeeRatio()).compareTo(BigDecimal.ZERO) > 0;
        if (!hasProvider && !hasBranch && !hasEmployee) {
            throw new BusinessException(BizCode.BAD_REQUEST, "至少需要配置一个接收方（服务商/分店/员工）");
        }
        BigDecimal sum = nz(r.getProviderRatio()).add(nz(r.getBranchRatio()))
                .add(nz(r.getEmployeeRatio())).add(nz(r.getPlatformFeeRatio()));
        if (sum.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "分账比例之和不能超过 100%（当前=" + sum + "%）");
        }
        if (r.getPriority() == null) r.setPriority(0);
        if (r.getStatus() == null) r.setStatus(1);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Map<String, Object> toMap(MultiAccountSplitRecord r) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("storeId", r.getStoreId() == null ? 0L : r.getStoreId());
        m.put("orderId", r.getOrderId());
        m.put("orderNo", r.getOrderNo() == null ? "" : r.getOrderNo());
        m.put("ruleName", r.getRuleName() == null ? "" : r.getRuleName());
        m.put("orderAmount", r.getOrderAmount() == null ? BigDecimal.ZERO : r.getOrderAmount());
        m.put("feeAmount", r.getFeeAmount() == null ? BigDecimal.ZERO : r.getFeeAmount());
        m.put("splitTotal", r.getSplitTotal() == null ? BigDecimal.ZERO : r.getSplitTotal());
        m.put("status", r.getStatus() == null ? 1 : r.getStatus());
        m.put("errorMsg", r.getErrorMsg() == null ? "" : r.getErrorMsg());
        m.put("operatorName", r.getOperatorName() == null ? "" : r.getOperatorName());
        m.put("splitTime", r.getSplitTime() == null ? "" : r.getSplitTime().toString());
        return m;
    }
}
