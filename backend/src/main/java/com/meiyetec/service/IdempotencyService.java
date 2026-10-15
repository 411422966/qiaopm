package com.meiyetec.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.SysIdempotencyRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.SysIdempotencyRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.ObjectMapperHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * R2.0-B T-B03：幂等执行器。
 *
 * <p>典型用法：</p>
 * <pre>
 *   return idem.execute("wallet.recharge", idempotencyKey, payload, () -> doRecharge(dto));
 * </pre>
 *
 * <p>策略：</p>
 * <ol>
 *   <li>key+biz_type+tenant 唯一；首次请求 → INSERT PROCESSING → 执行 supplier → 落 response；</li>
 *   <li>重复请求：相同 key + 相同 request_hash → 直接返回上次结果（IDEMPOTENT_REPLAY）；</li>
 *   <li>重复请求：相同 key + 不同 request_hash → 409 IDEMPOTENT_KEY_CONFLICT；</li>
 *   <li>PROCESSING 状态的孤儿记录 → 视 createdTime 距今 &gt; retention 默认 5 分钟触发抢占；</li>
 *   <li>retention 配置在 sys_config.idempotency.retention-hours，过期后 key 复用。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final SysIdempotencyRecordMapper mapper;
    private final ObjectMapper objectMapper = ObjectMapperHolder.MAPPER;

    @Value("${idempotency.retention-hours:24}")
    private int defaultRetentionHours;

    /** 孤儿记录抢占等待秒数 */
    private static final int HOLDING_SECONDS = 300;

    /**
     * 入口：对指定 idempotencyKey 提供幂等执行。
     *
     * @param bizType  业务类型：wallet.recharge / pos.pay 等
     * @param key      幂等键（前端从 UUID 生成；不传则自动生成，但失去跨实例去重能力）
     * @param payload  会参与 request_hash 的请求体（任意可序列化对象）；传 null 时仍去重
     * @param supplier 真实业务执行体；返回 Result.data 的对象
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public <T> IdempotencyResult<T> execute(String bizType, String key, Object payload, Supplier<com.meiyetec.util.Result<T>> supplier) {
        if (key == null || key.isEmpty()) {
            // 没有幂等键时，直接执行（不挡业务），但日志告警
            log.warn("[idempotency] missing key for bizType={}", bizType);
            com.meiyetec.util.Result<T> r = supplier.get();
            return new IdempotencyResult<>(true, r);
        }
        String hash = sha256(payload);
        Long tenantId = TenantContextHolder.get().getTenantId();
        SysIdempotencyRecord existing = findExisting(tenantId, key, bizType);
        if (existing != null) {
            return resolveExisting(existing, hash, supplier);
        }

        SysIdempotencyRecord rec = new SysIdempotencyRecord();
        rec.setTenantId(tenantId);
        rec.setIdempotencyKey(key);
        rec.setBizType(bizType);
        rec.setRequestHash(hash);
        rec.setStatus("PROCESSING");
        rec.setRetryCount(0);
        rec.setExpiredAt(LocalDateTime.now().plusHours(defaultRetentionHours));
        try {
            mapper.insert(rec);
        } catch (DuplicateKeyException dup) {
            // 并发抢占：再查一次
            SysIdempotencyRecord re = findExisting(tenantId, key, bizType);
            if (re == null) {
                throw new BusinessException(BizCode.SYSTEM_ERROR, "幂等写入冲突且重查为空");
            }
            return resolveExisting(re, hash, supplier);
        }

        try {
            com.meiyetec.util.Result<T> real = supplier.get();
            persistResult(rec, real);
            return new IdempotencyResult<>(true, real);
        } catch (RuntimeException biz) {
            persistFailure(rec, biz);
            throw biz;
        }
    }

    private <T> IdempotencyResult<T> resolveExisting(SysIdempotencyRecord existing, String hash,
                                                      Supplier<com.meiyetec.util.Result<T>> supplier) {
        if (existing.getRequestHash() != null && !existing.getRequestHash().equals(hash)) {
            throw new BusinessException(BizCode.IDEMPOTENT_KEY_CONFLICT,
                    "幂等键已被使用且请求体不同");
        }
        if ("SUCCESS".equals(existing.getStatus())) {
            log.info("[idempotency] replay bizType={} key={} bizId={}",
                    existing.getBizType(), existing.getIdempotencyKey(), existing.getBizId());
            T data = readData(existing.getResponseData());
            Result<T> replay = Result.success(
                    existing.getResponseMessage() == null ? "已返回上次结果" : existing.getResponseMessage(),
                    data);
            replay.setCode(existing.getResponseCode() == null ? 200 : existing.getResponseCode());
            return new IdempotencyResult<>(false, replay);
        }
        if ("FAILED".equals(existing.getStatus())) {
            // 上次失败，本次允许重新执行（删除旧记录，给新记录让路）
            mapper.deleteById(existing.getId());
            return execute(existing.getBizType(), existing.getIdempotencyKey(),
                    readPayload(existing.getResponseData()), supplier);
        }
        // PROCESSING：可能是并发或上轮挂死
        if (existing.getCreateTime() != null
                && existing.getCreateTime().isBefore(LocalDateTime.now().minusSeconds(HOLDING_SECONDS))) {
            log.warn("[idempotency] 抢占孤儿记录 id={} key={}", existing.getId(), existing.getIdempotencyKey());
            mapper.deleteById(existing.getId());
            return execute(existing.getBizType(), existing.getIdempotencyKey(),
                    readPayload(existing.getResponseData()), supplier);
        }
        throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "相同幂等键正在处理中，请稍后再试");
    }

    private <T> void persistResult(SysIdempotencyRecord rec, Result<T> real) {
        try {
            String data = real.getData() == null ? null : objectMapper.writeValueAsString(real.getData());
            mapper.finish(rec.getId(), "SUCCESS", null,
                    real.getCode() == 0 ? 200 : real.getCode(),
                    real.getMessage(), data);
        } catch (Exception e) {
            log.warn("[idempotency] persist response failed key={}", rec.getIdempotencyKey(), e);
        }
    }

    private void persistFailure(SysIdempotencyRecord rec, RuntimeException biz) {
        try {
            int code = biz instanceof BusinessException be ? be.getCode() : BizCode.SYSTEM_ERROR.code;
            mapper.finish(rec.getId(), "FAILED", null, code, biz.getMessage(), null);
        } catch (Exception e) {
            log.warn("[idempotency] persist failure failed key={}", rec.getIdempotencyKey(), e);
        }
    }

    private SysIdempotencyRecord findExisting(Long tenantId, String key, String bizType) {
        return mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysIdempotencyRecord>()
                        .eq("tenant_id", tenantId)
                        .eq("idempotency_key", key)
                        .eq("biz_type", bizType)
                        .last("LIMIT 1"));
    }

    private String sha256(Object payload) {
        if (payload == null) return sha256("null");
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return sha256(String.valueOf(payload));
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "0";
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T readData(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return (T) objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Object readPayload(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static class IdempotencyResult<T> {
        public final boolean firstTime;
        public final Result<T> result;

        public IdempotencyResult(boolean firstTime, Result<T> result) {
            this.firstTime = firstTime;
            this.result = result;
        }
    }
}
