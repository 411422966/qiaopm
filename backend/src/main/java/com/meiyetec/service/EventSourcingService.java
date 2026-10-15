package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CqrsQueryModel;
import com.meiyetec.entity.DomainEventStore;
import com.meiyetec.entity.EventProjection;
import com.meiyetec.entity.EventSnapshot;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CqrsQueryModelMapper;
import com.meiyetec.mapper.DomainEventStoreMapper;
import com.meiyetec.mapper.EventProjectionMapper;
import com.meiyetec.mapper.EventSnapshotMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R3.0-H P2.8：Event Sourcing + CQRS（简化版）
 *
 * <p>提供：
 * <ul>
 *   <li>领域事件存储（append-only / sequence）</li>
 *   <li>事件回放（replay）</li>
 *   <li>快照（snapshot）</li>
 *   <li>投影（projection）进度管理</li>
 *   <li>CQRS 读模型定义</li>
 * </ul>
 *
 * <p>生产应替换为 Axon / EventStoreDB。
 */
@Slf4j
@RestController
@RequestMapping("/es")
@RequiredArgsConstructor
public class EventSourcingService {

    private final DomainEventStoreMapper eventMapper;
    private final EventSnapshotMapper snapshotMapper;
    private final EventProjectionMapper projectionMapper;
    private final CqrsQueryModelMapper queryModelMapper;

    // 聚合内序号缓存
    private static final Map<String, AtomicLong> SEQ_CACHE = new ConcurrentHashMap<>();

    // ====================================================================
    // 1. 事件追加
    // ====================================================================

    @PostMapping("/event/append")
    @Transactional(rollbackFor = Exception.class)
    public Result<DomainEventStore> appendEvent(@RequestBody DomainEventStore e) {
        if (e.getEventId() == null) e.setEventId(UUID.randomUUID().toString());
        if (e.getOccurredAt() == null) e.setOccurredAt(LocalDateTime.now());
        if (e.getEventVersion() == null) e.setEventVersion(1);
        // 聚合内序号
        String key = e.getAggregateType() + ":" + e.getAggregateId();
        long nextSeq = SEQ_CACHE.computeIfAbsent(key, k -> new AtomicLong(getMaxSeq(e.getAggregateType(), e.getAggregateId())))
                .incrementAndGet();
        e.setSequence(nextSeq);
        eventMapper.insert(e);
        return Result.success("已追加", e);
    }

    private long getMaxSeq(String aggType, String aggId) {
        DomainEventStore last = eventMapper.selectOne(
                new LambdaQueryWrapper<DomainEventStore>()
                        .eq(DomainEventStore::getAggregateType, aggType)
                        .eq(DomainEventStore::getAggregateId, aggId)
                        .orderByDesc(DomainEventStore::getSequence)
                        .last("LIMIT 1"));
        return last == null || last.getSequence() == null ? 0L : last.getSequence();
    }

    /**
     * 批量追加（事务）
     */
    @PostMapping("/event/append-batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<DomainEventStore>> appendBatch(@RequestBody List<DomainEventStore> events) {
        for (DomainEventStore e : events) {
            appendEvent(e);
        }
        return Result.success("已追加 " + events.size() + " 个", events);
    }

    /**
     * 事件回放（按聚合）
     */
    @GetMapping("/event/replay")
    public Result<List<DomainEventStore>> replay(@RequestParam String aggregateType,
                                                  @RequestParam String aggregateId,
                                                  @RequestParam(required = false) Long fromSeq) {
        LambdaQueryWrapper<DomainEventStore> qw = new LambdaQueryWrapper<>();
        qw.eq(DomainEventStore::getAggregateType, aggregateType)
                .eq(DomainEventStore::getAggregateId, aggregateId);
        if (fromSeq != null) qw.ge(DomainEventStore::getSequence, fromSeq);
        qw.orderByAsc(DomainEventStore::getSequence);
        return Result.success(eventMapper.selectList(qw));
    }

    @GetMapping("/event/by-correlation/{cid}")
    public Result<List<DomainEventStore>> byCorrelation(@PathVariable String cid) {
        return Result.success(eventMapper.selectList(
                new LambdaQueryWrapper<DomainEventStore>()
                        .eq(DomainEventStore::getCorrelationId, cid)
                        .orderByAsc(DomainEventStore::getSequence)));
    }

    // ====================================================================
    // 2. 快照
    // ====================================================================

    @PostMapping("/snapshot/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<EventSnapshot> saveSnapshot(@RequestBody EventSnapshot s) {
        if (s.getVersion() == null) s.setVersion("1.0.0");
        if (s.getCreatedAt() == null) s.setCreatedAt(LocalDateTime.now());
        // upsert
        EventSnapshot exist = snapshotMapper.selectOne(
                new LambdaQueryWrapper<EventSnapshot>()
                        .eq(EventSnapshot::getAggregateType, s.getAggregateType())
                        .eq(EventSnapshot::getAggregateId, s.getAggregateId())
                        .eq(EventSnapshot::getSequence, s.getSequence()));
        if (exist != null) {
            exist.setState(s.getState());
            exist.setVersion(s.getVersion());
            snapshotMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        snapshotMapper.insert(s);
        return Result.success("已保存", s);
    }

    @GetMapping("/snapshot/latest")
    public Result<EventSnapshot> latestSnapshot(@RequestParam String aggregateType,
                                                  @RequestParam String aggregateId) {
        return Result.success(snapshotMapper.selectOne(
                new LambdaQueryWrapper<EventSnapshot>()
                        .eq(EventSnapshot::getAggregateType, aggregateType)
                        .eq(EventSnapshot::getAggregateId, aggregateId)
                        .orderByDesc(EventSnapshot::getSequence)
                        .last("LIMIT 1")));
    }

    /**
     * 重建状态：从 latest snapshot + replay after snapshot
     */
    @PostMapping("/snapshot/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestBody Map<String, Object> body) {
        String aggregateType = (String) body.get("aggregateType");
        String aggregateId = (String) body.get("aggregateId");
        EventSnapshot snap = latestSnapshot(aggregateType, aggregateId).getData();
        long fromSeq = snap == null ? 0 : (snap.getSequence() == null ? 0 : snap.getSequence());

        List<DomainEventStore> events = eventMapper.selectList(
                new LambdaQueryWrapper<DomainEventStore>()
                        .eq(DomainEventStore::getAggregateType, aggregateType)
                        .eq(DomainEventStore::getAggregateId, aggregateId)
                        .ge(DomainEventStore::getSequence, fromSeq + 1)
                        .orderByAsc(DomainEventStore::getSequence));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseSnapshot", snap);
        data.put("replayEvents", events);
        data.put("replayCount", events.size());
        data.put("fromSeq", fromSeq + 1);
        return Result.success(data);
    }

    // ====================================================================
    // 3. 投影
    // ====================================================================

    @PostMapping("/projection/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<EventProjection> registerProjection(@RequestBody EventProjection p) {
        if (p.getProjectionName() == null || p.getAggregateType() == null)
            return Result.error(BizCode.BAD_REQUEST, "projectionName / aggregateType 必填");
        if (p.getLastSequence() == null) p.setLastSequence(0L);
        if (p.getEventCount() == null) p.setEventCount(0L);
        if (p.getStatus() == null) p.setStatus("RUNNING");

        EventProjection exist = projectionMapper.selectOne(
                new LambdaQueryWrapper<EventProjection>()
                        .eq(EventProjection::getProjectionName, p.getProjectionName())
                        .eq(EventProjection::getAggregateType, p.getAggregateType()));
        if (exist != null) {
            exist.setStatus(p.getStatus());
            exist.setErrorMessage(p.getErrorMessage());
            projectionMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        projectionMapper.insert(p);
        return Result.success("已注册", p);
    }

    @PostMapping("/projection/{name}/advance")
    @Transactional(rollbackFor = Exception.class)
    public Result<EventProjection> advanceProjection(@PathVariable String name,
                                                       @RequestBody Map<String, Object> body) {
        String aggType = (String) body.get("aggregateType");
        if (aggType == null) return Result.error(BizCode.BAD_REQUEST, "aggregateType 必填");

        EventProjection p = projectionMapper.selectOne(
                new LambdaQueryWrapper<EventProjection>()
                        .eq(EventProjection::getProjectionName, name)
                        .eq(EventProjection::getAggregateType, aggType));
        if (p == null) return Result.error(BizCode.NOT_FOUND, "投影不存在");

        long seq = ((Number) body.getOrDefault("sequence", 0)).longValue();
        p.setLastSequence(seq);
        p.setEventCount(p.getEventCount() + 1);
        p.setLastEventAt(LocalDateTime.now());
        projectionMapper.updateById(p);
        return Result.success("已推进", p);
    }

    @GetMapping("/projection/list")
    public Result<List<EventProjection>> listProjections() {
        return Result.success(projectionMapper.selectList(null));
    }

    // ====================================================================
    // 4. CQRS 读模型
    // ====================================================================

    @PostMapping("/cqrs/model")
    @Transactional(rollbackFor = Exception.class)
    public Result<CqrsQueryModel> upsertQueryModel(@RequestBody CqrsQueryModel m) {
        if (m.getModelName() == null) return Result.error(BizCode.BAD_REQUEST, "modelName 必填");
        if (m.getRefreshIntervalSec() == null) m.setRefreshIntervalSec(60);
        if (m.getEnabled() == null) m.setEnabled(1);

        CqrsQueryModel exist = queryModelMapper.selectOne(
                new LambdaQueryWrapper<CqrsQueryModel>().eq(CqrsQueryModel::getModelName, m.getModelName()));
        if (exist != null) {
            exist.setSourceAggregate(m.getSourceAggregate());
            exist.setDescription(m.getDescription());
            exist.setSchemaSql(m.getSchemaSql());
            exist.setRefreshIntervalSec(m.getRefreshIntervalSec());
            exist.setEnabled(m.getEnabled());
            queryModelMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        queryModelMapper.insert(m);
        return Result.success("已创建", m);
    }

    @GetMapping("/cqrs/model/list")
    public Result<List<CqrsQueryModel>> listQueryModels() {
        return Result.success(queryModelMapper.selectList(
                new LambdaQueryWrapper<CqrsQueryModel>().eq(CqrsQueryModel::getEnabled, 1)));
    }

    @PostMapping("/cqrs/model/{name}/refresh")
    @Transactional(rollbackFor = Exception.class)
    public Result<CqrsQueryModel> refreshModel(@PathVariable String name) {
        CqrsQueryModel m = queryModelMapper.selectOne(
                new LambdaQueryWrapper<CqrsQueryModel>().eq(CqrsQueryModel::getModelName, name));
        if (m == null) return Result.error(BizCode.NOT_FOUND, "读模型不存在");
        m.setLastRefreshedAt(LocalDateTime.now());
        queryModelMapper.updateById(m);
        return Result.success("已刷新", m);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    public static String eventId() {
        return UUID.randomUUID().toString();
    }

    public static String sha256(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}