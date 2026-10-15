package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.AggregateRoot;
import com.meiyetec.entity.BoundedContext;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AggregateRootMapper;
import com.meiyetec.mapper.BoundedContextMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * R3.0-H P2.8：DDD 限界上下文 + 聚合根
 *
 * <p>提供：
 * <ul>
 *   <li>限界上下文（3 层：CORE / SUPPORTING / GENERIC）</li>
 *   <li>聚合根管理（不变式 + 事件 + 版本）</li>
 *   <li>上下文映射（上游 / 下游）</li>
 *   <li>依赖分析</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ddd")
@RequiredArgsConstructor
public class DddModelingService {

    private final BoundedContextMapper contextMapper;
    private final AggregateRootMapper aggMapper;

    // ====================================================================
    // 1. 限界上下文
    // ====================================================================

    @PostMapping("/context")
    @Transactional(rollbackFor = Exception.class)
    public Result<BoundedContext> upsertContext(@RequestBody BoundedContext c) {
        if (c.getContextCode() == null) return Result.error(BizCode.BAD_REQUEST, "contextCode 必填");
        BoundedContext exist = contextMapper.selectOne(
                new LambdaQueryWrapper<BoundedContext>().eq(BoundedContext::getContextCode, c.getContextCode()));
        if (exist != null) {
            exist.setContextName(c.getContextName());
            exist.setDescription(c.getDescription());
            exist.setDomainLayer(c.getDomainLayer());
            exist.setUpstreamCtx(c.getUpstreamCtx());
            exist.setDownstreamCtx(c.getDownstreamCtx());
            exist.setTeamOwner(c.getTeamOwner());
            exist.setStatus(c.getStatus());
            contextMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (c.getDomainLayer() == null) c.setDomainLayer("CORE");
        if (c.getStatus() == null) c.setStatus("ACTIVE");
        contextMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/context/list")
    public Result<List<BoundedContext>> listContexts(@RequestParam(required = false) String domainLayer,
                                                       @RequestParam(required = false) String status) {
        LambdaQueryWrapper<BoundedContext> qw = new LambdaQueryWrapper<>();
        if (domainLayer != null) qw.eq(BoundedContext::getDomainLayer, domainLayer);
        if (status != null) qw.eq(BoundedContext::getStatus, status);
        qw.orderByAsc(BoundedContext::getDomainLayer);
        return Result.success(contextMapper.selectList(qw));
    }

    @GetMapping("/context/{code}")
    public Result<BoundedContext> getContext(@PathVariable String code) {
        return Result.success(contextMapper.selectOne(
                new LambdaQueryWrapper<BoundedContext>().eq(BoundedContext::getContextCode, code)));
    }

    @GetMapping("/context/map")
    public Result<Map<String, Object>> contextMap() {
        List<BoundedContext> all = contextMapper.selectList(null);
        Map<String, List<String>> upstream = new HashMap<>();
        Map<String, List<String>> downstream = new HashMap<>();
        for (BoundedContext c : all) {
            if (c.getUpstreamCtx() != null) upstream.put(c.getContextCode(), Arrays.asList(c.getUpstreamCtx().split(",")));
            if (c.getDownstreamCtx() != null) downstream.put(c.getContextCode(), Arrays.asList(c.getDownstreamCtx().split(",")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contexts", all);
        data.put("upstream", upstream);
        data.put("downstream", downstream);
        return Result.success(data);
    }

    // ====================================================================
    // 2. 聚合根
    // ====================================================================

    @PostMapping("/aggregate")
    @Transactional(rollbackFor = Exception.class)
    public Result<AggregateRoot> upsertAggregate(@RequestBody AggregateRoot a) {
        if (a.getContextCode() == null || a.getAggregateCode() == null)
            return Result.error(BizCode.BAD_REQUEST, "contextCode / aggregateCode 必填");
        AggregateRoot exist = aggMapper.selectOne(
                new LambdaQueryWrapper<AggregateRoot>()
                        .eq(AggregateRoot::getContextCode, a.getContextCode())
                        .eq(AggregateRoot::getAggregateCode, a.getAggregateCode()));
        if (exist != null) {
            exist.setAggregateName(a.getAggregateName());
            exist.setDescription(a.getDescription());
            exist.setRootTable(a.getRootTable());
            exist.setRootIdField(a.getRootIdField());
            exist.setInvariantCount(a.getInvariantCount());
            exist.setEventCount(a.getEventCount());
            exist.setVersion(a.getVersion());
            aggMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        if (a.getRootIdField() == null) a.setRootIdField("id");
        if (a.getVersion() == null) a.setVersion("1.0.0");
        aggMapper.insert(a);
        return Result.success("已创建", a);
    }

    @GetMapping("/aggregate/list")
    public Result<List<AggregateRoot>> listAggregates(@RequestParam(required = false) String contextCode) {
        LambdaQueryWrapper<AggregateRoot> qw = new LambdaQueryWrapper<>();
        if (contextCode != null) qw.eq(AggregateRoot::getContextCode, contextCode);
        qw.orderByAsc(AggregateRoot::getContextCode);
        return Result.success(aggMapper.selectList(qw));
    }

    @GetMapping("/aggregate/{context}/{code}")
    public Result<AggregateRoot> getAggregate(@PathVariable String context,
                                                  @PathVariable String code) {
        return Result.success(aggMapper.selectOne(
                new LambdaQueryWrapper<AggregateRoot>()
                        .eq(AggregateRoot::getContextCode, context)
                        .eq(AggregateRoot::getAggregateCode, code)));
    }

    // ====================================================================
    // 3. DDD 矩阵分析
    // ====================================================================

    @GetMapping("/context-matrix")
    public Result<Map<String, Object>> contextMatrix() {
        List<BoundedContext> all = contextMapper.selectList(null);
        Map<String, List<AggregateRoot>> byContext = new HashMap<>();
        for (BoundedContext c : all) {
            List<AggregateRoot> aggs = aggMapper.selectList(
                    new LambdaQueryWrapper<AggregateRoot>()
                            .eq(AggregateRoot::getContextCode, c.getContextCode()));
            byContext.put(c.getContextCode(), aggs);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contexts", all);
        data.put("aggregatesByContext", byContext);
        int totalAgg = byContext.values().stream().mapToInt(List::size).sum();
        data.put("totalAggregate", totalAgg);
        return Result.success(data);
    }
}