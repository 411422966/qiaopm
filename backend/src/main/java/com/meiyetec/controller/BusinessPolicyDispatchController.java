package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.BusinessPolicyDispatchLog;
import com.meiyetec.entity.MktDispatch;
import com.meiyetec.entity.MktDispatchTarget;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BusinessPolicyDispatchLogMapper;
import com.meiyetec.mapper.MktDispatchTargetMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.impl.BusinessPolicyDispatchServiceImpl;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * M12-05 业务策略统一下发 Controller
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /m12/dispatch                  创建 DRAFT</li>
 *   <li>POST   /m12/dispatch/{id}/publish     执行下发（同步）</li>
 *   <li>POST   /m12/dispatch/{id}/publish-async 异步下发（>50 门店自动切）</li>
 *   <li>POST   /m12/dispatch/{id}/cancel      取消</li>
 *   <li>GET    /m12/dispatch/page             下发单分页</li>
 *   <li>GET    /m12/dispatch/{id}             下发单详情</li>
 *   <li>GET    /m12/dispatch/{id}/targets     目标门店列表</li>
 *   <li>GET    /m12/dispatch/{id}/logs        执行日志</li>
 *   <li>GET    /m12/dispatch/stats            看板统计</li>
 * </ul>
 */
@RestController
@RequestMapping("/m12/dispatch")
@RequiredArgsConstructor
public class BusinessPolicyDispatchController {

    private final BusinessPolicyDispatchServiceImpl dispatchService;
    private final MktDispatchTargetMapper dispatchTargetMapper;
    private final BusinessPolicyDispatchLogMapper dispatchLogMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    // ==================== 创建 + 发布 ====================

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        Long t = tid();
        MktDispatch d = new MktDispatch();
        d.setTenantId(t);
        d.setDispatchType((String) body.get("dispatchType"));
        d.setDispatchSubtype((String) body.get("dispatchSubtype"));
        d.setSourceId(body.get("sourceId") == null ? null : ((Number) body.get("sourceId")).longValue());
        d.setSourceVersion((String) body.get("sourceVersion"));
        d.setTitle((String) body.get("title"));
        d.setPayload((String) body.get("payload"));
        d.setTargetType((String) body.getOrDefault("targetType", "ALL"));
        d.setTargetStoreIds((String) body.get("targetStoreIds"));
        d.setPlanStartTime(body.get("planStartTime") == null ? null
                : LocalDateTime.parse((String) body.get("planStartTime")));
        d.setPlanEndTime(body.get("planEndTime") == null ? null
                : LocalDateTime.parse((String) body.get("planEndTime")));
        d.setBrandId(body.get("brandId") == null ? null : ((Number) body.get("brandId")).longValue());
        d.setRemark((String) body.get("remark"));
        d.setOperatorId(TenantContextHolder.get().getUserId());
        d.setOperatorName(TenantContextHolder.get().getUsername());

        @SuppressWarnings("unchecked")
        List<Number> explicitIds = (List<Number>) body.get("storeIds");
        List<Long> storeIds = explicitIds == null ? null
                : explicitIds.stream().map(Number::longValue).toList();

        Long id = dispatchService.createDispatch(d, storeIds);
        return Result.success("下发单创建成功（状态=DRAFT）", id);
    }

    @PostMapping("/{id:\\d+}/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long id) {
        ensureTenantOwnership(id);
        Long userId = TenantContextHolder.get().getUserId();
        String userName = TenantContextHolder.get().getUsername();
        Long opId = userId == null ? 0L : userId;
        String opName = userName == null ? "SYSTEM" : userName;
        // > 50 门店自动切异步
        long count = dispatchTargetMapper.selectCount(new LambdaQueryWrapper<MktDispatchTarget>()
                .eq(MktDispatchTarget::getDispatchId, id));
        if (count > 50) {
            dispatchService.publishAndDispatchAsync(id, opId, opName);
            return Result.success("下发任务已提交（异步执行，门店数=" + count + "）", null);
        }
        Map<String, Object> result = dispatchService.publishAndDispatch(id, opId, opName);
        return Result.success("下发完成", result);
    }

    @PostMapping("/{id:\\d+}/publish-async")
    public Result<Void> publishAsync(@PathVariable Long id) {
        ensureTenantOwnership(id);
        Long userId = TenantContextHolder.get().getUserId();
        String userName = TenantContextHolder.get().getUsername();
        Long opId = userId == null ? 0L : userId;
        String opName = userName == null ? "SYSTEM" : userName;
        dispatchService.publishAndDispatchAsync(id, opId, opName);
        return Result.success("异步下发任务已提交", null);
    }

    @PostMapping("/{id:\\d+}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        ensureTenantOwnership(id);
        Long userId = TenantContextHolder.get().getUserId();
        String userName = TenantContextHolder.get().getUsername();
        dispatchService.cancelDispatch(id, userId, userName);
        return Result.success("下发单已取消", null);
    }

    // ==================== 查询 ====================

    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String dispatchType,
            @RequestParam(required = false) String dispatchSubtype,
            @RequestParam(required = false) String status) {
        Long t = tid();
        LambdaQueryWrapper<MktDispatch> q = new LambdaQueryWrapper<>();
        q.eq(MktDispatch::getTenantId, t);
        if (dispatchType != null && !dispatchType.isBlank()) q.eq(MktDispatch::getDispatchType, dispatchType);
        if (dispatchSubtype != null && !dispatchSubtype.isBlank()) q.eq(MktDispatch::getDispatchSubtype, dispatchSubtype);
        if (status != null && !status.isBlank()) q.eq(MktDispatch::getStatus, status);
        q.orderByDesc(MktDispatch::getCreateTime);
        Page<MktDispatch> pg = new Page<>(pageNum, Math.min(pageSize, 200));
        Page<MktDispatch> result = dispatchService.page(pg, q);
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(this::toMap).toList());
        return Result.success(mapPage);
    }

    @GetMapping("/{id:\\d+}")
    public Result<Map<String, Object>> getById(@PathVariable Long id) {
        ensureTenantOwnership(id);
        MktDispatch d = dispatchService.getById(id);
        return Result.success(toMap(d));
    }

    @GetMapping("/{id:\\d+}/targets")
    public Result<List<Map<String, Object>>> getTargets(@PathVariable Long id) {
        ensureTenantOwnership(id);
        List<MktDispatchTarget> list = dispatchTargetMapper.selectList(
                new LambdaQueryWrapper<MktDispatchTarget>()
                        .eq(MktDispatchTarget::getDispatchId, id)
                        .orderByAsc(MktDispatchTarget::getStoreId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MktDispatchTarget t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("storeId", t.getStoreId());
            m.put("storeName", t.getStoreName());
            m.put("status", t.getStatus());
            m.put("acceptedAt", t.getAcceptedAt() == null ? "" : t.getAcceptedAt().toString());
            m.put("rejectedReason", t.getRejectedReason() == null ? "" : t.getRejectedReason());
            m.put("executedAt", t.getExecutedAt() == null ? "" : t.getExecutedAt().toString());
            rows.add(m);
        }
        return Result.success(rows);
    }

    @GetMapping("/{id:\\d+}/logs")
    public Result<List<BusinessPolicyDispatchLog>> getLogs(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        ensureTenantOwnership(id);
        LambdaQueryWrapper<BusinessPolicyDispatchLog> q = new LambdaQueryWrapper<>();
        q.eq(BusinessPolicyDispatchLog::getDispatchId, id);
        if (status != null && !status.isBlank()) q.eq(BusinessPolicyDispatchLog::getStatus, status);
        q.orderByAsc(BusinessPolicyDispatchLog::getStoreId);
        return Result.success(dispatchLogMapper.selectList(q));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) String month) {
        Long t = tid();
        LambdaQueryWrapper<MktDispatch> q = new LambdaQueryWrapper<>();
        q.eq(MktDispatch::getTenantId, t);
        if (month != null && !month.isBlank()) {
            q.apply("DATE_FORMAT(create_time, '%Y-%m') = {0}", month);
        }
        List<MktDispatch> all = dispatchService.list(q);
        long total = all.size();
        long success = all.stream().filter(x -> x.getSuccessCount() != null && x.getSuccessCount() > 0).count();
        long failed = all.stream().filter(x -> x.getFailCount() != null && x.getFailCount() > 0).count();
        long done = all.stream().filter(x -> "DONE".equals(x.getStatus())).count();
        long draft = all.stream().filter(x -> "DRAFT".equals(x.getStatus())).count();
        long cancelled = all.stream().filter(x -> "CANCELLED".equals(x.getStatus())).count();
        // 按类型分组
        Map<String, Long> byType = new LinkedHashMap<>();
        for (MktDispatch d : all) {
            String key = d.getDispatchType() == null ? "UNKNOWN" : d.getDispatchType();
            byType.merge(key, 1L, Long::sum);
        }
        Map<String, Long> bySubtype = new LinkedHashMap<>();
        for (MktDispatch d : all) {
            String key = d.getDispatchSubtype() == null ? "NULL" : d.getDispatchSubtype();
            bySubtype.merge(key, 1L, Long::sum);
        }
        return Result.success(Map.of(
                "totalDispatches", total,
                "successfulDispatches", success,
                "failedDispatches", failed,
                "doneCount", done,
                "draftCount", draft,
                "cancelledCount", cancelled,
                "byType", byType,
                "bySubtype", bySubtype
        ));
    }

    // ==================== 辅助 ====================

    private void ensureTenantOwnership(Long dispatchId) {
        Long t = tid();
        MktDispatch d = dispatchService.getById(dispatchId);
        if (d == null) throw new BusinessException(BizCode.NOT_FOUND, "下发单不存在");
        if (!t.equals(d.getTenantId())) throw new BusinessException(BizCode.FORBIDDEN, "跨租户访问被拒绝");
    }

    private Map<String, Object> toMap(MktDispatch d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("dispatchNo", d.getDispatchNo());
        m.put("dispatchType", d.getDispatchType());
        m.put("dispatchSubtype", d.getDispatchSubtype());
        m.put("sourceId", d.getSourceId());
        m.put("sourceVersion", d.getSourceVersion() == null ? "" : d.getSourceVersion());
        m.put("publishedVersion", d.getPublishedVersion() == null ? 1 : d.getPublishedVersion());
        m.put("title", d.getTitle());
        m.put("targetType", d.getTargetType());
        m.put("targetStoreIds", d.getTargetStoreIds() == null ? "" : d.getTargetStoreIds());
        m.put("status", d.getStatus());
        m.put("publishedAt", d.getPublishedAt() == null ? "" : d.getPublishedAt().toString());
        m.put("operatorName", d.getOperatorName() == null ? "" : d.getOperatorName());
        m.put("successCount", d.getSuccessCount() == null ? 0 : d.getSuccessCount());
        m.put("failCount", d.getFailCount() == null ? 0 : d.getFailCount());
        m.put("totalCount", d.getTotalCount() == null ? 0 : d.getTotalCount());
        m.put("remark", d.getRemark() == null ? "" : d.getRemark());
        m.put("createTime", d.getCreateTime() == null ? "" : d.getCreateTime().toString());
        return m;
    }
}
