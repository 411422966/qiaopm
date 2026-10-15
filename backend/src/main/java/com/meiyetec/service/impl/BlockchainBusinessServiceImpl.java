package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.config.BlockchainChainConfig;
import com.meiyetec.entity.BlockchainChainBlock;
import com.meiyetec.entity.BlockchainRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BlockchainChainBlockMapper;
import com.meiyetec.mapper.BlockchainRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BlockchainBusinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainBusinessServiceImpl implements BlockchainBusinessService {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String CHAIN_MAIN = "meiyetec-main";
    public static final String CHAIN_COSMETIC = "meiyetec-cosmetic";
    public static final String CHAIN_DRUG = "meiyetec-drug";
    public static final String CHAIN_DEVICE = "meiyetec-device";
    public static final int DEFAULT_DIFFICULTY = 2;

    private final BlockchainRecordMapper recordMapper;
    private final BlockchainChainBlockMapper blockMapper;
    private final BlockchainChainConfig chainConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockchainRecord recordProduct(Long productId, String productNo, String productName,
                                         String batchNo, String manufacturer, BigDecimal price,
                                         String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId);
        payload.put("productNo", productNo);
        payload.put("productName", productName);
        payload.put("batchNo", batchNo);
        payload.put("manufacturer", manufacturer);
        payload.put("price", price);
        payload.put("sealedAt", LocalDateTime.now().toString());
        return recordWithChain("PRODUCT", productId, productNo, payload, CHAIN_COSMETIC, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockchainRecord recordDrug(Long drugId, String drugNo, String drugName,
                                      String batchNo, String approvalNo, String expiryDate,
                                      String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("drugId", drugId);
        payload.put("drugNo", drugNo);
        payload.put("drugName", drugName);
        payload.put("batchNo", batchNo);
        payload.put("approvalNo", approvalNo);
        payload.put("expiryDate", expiryDate);
        payload.put("sealedAt", LocalDateTime.now().toString());
        return recordWithChain("DRUG", drugId, drugNo, payload, CHAIN_DRUG, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockchainRecord recordDevice(Long deviceId, String deviceNo, String deviceName,
                                         String registrationNo, String serialNo, String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("deviceNo", deviceNo);
        payload.put("deviceName", deviceName);
        payload.put("registrationNo", registrationNo);
        payload.put("serialNo", serialNo);
        payload.put("sealedAt", LocalDateTime.now().toString());
        return recordWithChain("DEVICE", deviceId, deviceNo, payload, CHAIN_DEVICE, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockchainRecord recordOrder(Long orderId, String orderNo, BigDecimal amount,
                                        String paymentMethod, String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("amount", amount);
        payload.put("paymentMethod", paymentMethod);
        payload.put("sealedAt", LocalDateTime.now().toString());
        return recordWithChain("ORDER", orderId, orderNo, payload, CHAIN_MAIN, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockchainRecord recordConsent(Long consentId, String consentNo, String consentType,
                                          Long memberId, String memberName, String operatorName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consentId", consentId);
        payload.put("consentNo", consentNo);
        payload.put("consentType", consentType);
        payload.put("memberId", memberId);
        payload.put("memberName", memberName);
        payload.put("sealedAt", LocalDateTime.now().toString());
        return recordWithChain("CONSENT", consentId, consentNo, payload, CHAIN_MAIN, operatorName);
    }

    @Override
    public BlockchainRecord record(String recordType, Long bizId, String bizNo, String payload,
                                   String chainId, Integer difficulty, String operatorName) {
        if (recordType == null || bizId == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "recordType / bizId 必填");
        }
        String cid = chainId != null ? chainId : chainConfig.getRecordTypeChainMap()
                .getOrDefault(recordType, CHAIN_MAIN);
        Integer diff = difficulty == null ? chainConfig.getChainDifficulty()
                .getOrDefault(cid, chainConfig.getDefaultDifficulty()) : difficulty;
        return recordWithChain(recordType, bizId, bizNo, toPayloadMap(payload), cid, diff, operatorName);
    }

    private BlockchainRecord recordWithChain(String recordType, Long bizId, String bizNo,
                                            Map<String, Object> payload, String chainId,
                                            String operatorName) {
        int diff = chainConfig.getChainDifficulty().getOrDefault(chainId, DEFAULT_DIFFICULTY);
        return recordWithChain(recordType, bizId, bizNo, payload, chainId, diff, operatorName);
    }

    private BlockchainRecord recordWithChain(String recordType, Long bizId, String bizNo,
                                            Map<String, Object> payload, String chainId,
                                            int difficulty, String operatorName) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BlockchainRecord exist = recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
        if (exist != null) {
            log.info("[Blockchain] 幂等命中 type={} bizId={} -> recordId={}", recordType, bizId, exist.getId());
            return exist;
        }
        BlockchainChainBlock lastBlock = blockMapper.selectOne(
                new LambdaQueryWrapper<BlockchainChainBlock>()
                        .eq(BlockchainChainBlock::getChainId, chainId)
                        .orderByDesc(BlockchainChainBlock::getBlockIndex)
                        .last("LIMIT 1"));
        long blockIndex = lastBlock == null ? 0 : lastBlock.getBlockIndex() + 1;
        String prevHash = lastBlock == null ? "0".repeat(64) : lastBlock.getBlockHash();

        String payloadJson = toJson(payload);
        String payloadHash = sha256Hex(payloadJson);

        long nonce = 0;
        String blockHash;
        String prefix = "0".repeat(difficulty);
        while (true) {
            String concat = prevHash + ":" + blockIndex + ":" + payloadHash + ":" + nonce;
            blockHash = sha256Hex(concat);
            if (blockHash.startsWith(prefix)) break;
            nonce++;
            if (nonce > 10_000_000) {
                throw new BusinessException(BizCode.CONFLICT, "PoW 挖矿失败（nonce 溢出）");
            }
        }

        BlockchainChainBlock block = new BlockchainChainBlock();
        block.setChainId(chainId);
        block.setBlockIndex(blockIndex);
        block.setBlockHash(blockHash);
        block.setPrevHash(prevHash);
        block.setMerkleRoot(payloadHash);
        block.setTxCount(1);
        block.setSizeBytes(payloadJson.length() + 64);
        block.setDifficulty(difficulty);
        block.setNonce(nonce);
        block.setMiner("system");
        blockMapper.insert(block);

        BlockchainRecord rec = new BlockchainRecord();
        rec.setTenantId(tenantId);
        rec.setRecordType(recordType);
        rec.setBizId(bizId);
        rec.setBizNo(bizNo);
        rec.setBlockHash(blockHash);
        rec.setPrevHash(prevHash);
        rec.setBlockIndex(blockIndex);
        rec.setMerkleRoot(payloadHash);
        rec.setPayload(payloadJson);
        rec.setPayloadHash(payloadHash);
        rec.setNonce(nonce);
        rec.setDifficulty(difficulty);
        rec.setMiner("system");
        rec.setConsensus("POW");
        rec.setVerified(1);
        rec.setVerifyCount(1);
        rec.setOperatorName(operatorName == null ? "system" : operatorName);
        recordMapper.insert(rec);
        log.info("[Blockchain] 存证成功 chain={} block=#{} type={} bizId={} nonce={}", chainId, blockIndex, recordType, bizId, nonce);
        return rec;
    }

    @Override
    public Map<String, Object> verifyChain(String chainId) {
        String cid = chainId == null ? CHAIN_MAIN : chainId;
        List<BlockchainChainBlock> blocks = blockMapper.selectList(
                new LambdaQueryWrapper<BlockchainChainBlock>()
                        .eq(BlockchainChainBlock::getChainId, cid)
                        .orderByAsc(BlockchainChainBlock::getBlockIndex));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chainId", cid);
        data.put("blockCount", blocks.size());
        if (blocks.isEmpty()) {
            data.put("valid", true);
            data.put("message", "链为空（尚未存证）");
            return data;
        }
        List<String> broken = new ArrayList<>();
        String expectedPrev = "0".repeat(64);
        for (BlockchainChainBlock b : blocks) {
            if (!Objects.equals(b.getPrevHash(), expectedPrev)) {
                broken.add("Block #" + b.getBlockIndex() + " prevHash 不匹配");
            }
            String pref = "0".repeat(b.getDifficulty());
            if (!b.getBlockHash().startsWith(pref)) {
                broken.add("Block #" + b.getBlockIndex() + " hash 不满足难度前缀");
            }
            String recomputed = sha256Hex(b.getPrevHash() + ":" + b.getBlockIndex() + ":" + b.getMerkleRoot() + ":" + b.getNonce());
            if (!recomputed.equals(b.getBlockHash())) {
                broken.add("Block #" + b.getBlockIndex() + " hash 校验失败（已被篡改）");
            }
            expectedPrev = b.getBlockHash();
        }
        data.put("valid", broken.isEmpty());
        data.put("broken", broken);
        data.put("lastBlockIndex", blocks.get(blocks.size() - 1).getBlockIndex());
        data.put("lastBlockHash", blocks.get(blocks.size() - 1).getBlockHash());
        return data;
    }

    @Override
    public Map<String, Object> chainInfo(String chainId) {
        String cid = chainId == null ? CHAIN_MAIN : chainId;
        Long count = blockMapper.selectCount(new LambdaQueryWrapper<BlockchainChainBlock>().eq(BlockchainChainBlock::getChainId, cid));
        BlockchainChainBlock last = blockMapper.selectOne(
                new LambdaQueryWrapper<BlockchainChainBlock>()
                        .eq(BlockchainChainBlock::getChainId, cid)
                        .orderByDesc(BlockchainChainBlock::getBlockIndex).last("LIMIT 1"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chainId", cid);
        data.put("blockCount", count == null ? 0 : count);
        data.put("latestBlockIndex", last == null ? -1 : last.getBlockIndex());
        data.put("latestBlockHash", last == null ? null : last.getBlockHash());
        return data;
    }

    @Override
    public Map<String, Object> trace(String recordType, Long bizId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BlockchainRecord rec = recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
        if (rec == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "存证不存在");
        }
        BlockchainChainBlock prev = null;
        if (rec.getBlockIndex() != null && rec.getBlockIndex() > 0) {
            String resolvedChain = chainIdFor(recordType);
            if (resolvedChain == null) {
                resolvedChain = CHAIN_MAIN;
            }
            prev = blockMapper.selectOne(
                    new LambdaQueryWrapper<BlockchainChainBlock>()
                            .eq(BlockchainChainBlock::getChainId, resolvedChain)
                            .eq(BlockchainChainBlock::getBlockIndex, rec.getBlockIndex() - 1));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", rec);
        data.put("prevBlock", prev);
        data.put("verified", rec.getVerified() != null && rec.getVerified() == 1);
        data.put("tracePath", buildTracePath(rec));
        return data;
    }

    private String chainIdFor(String recordType) {
        if (recordType == null) return CHAIN_MAIN;
        switch (recordType) {
            case "DRUG": return CHAIN_DRUG;
            case "DEVICE": return CHAIN_DEVICE;
            case "PRODUCT": return CHAIN_COSMETIC;
            default: return CHAIN_MAIN;
        }
    }

    @Override
    public BlockchainRecord getRecord(String recordType, Long bizId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
    }

    @Override
    public Map<String, Object> pageRecords(int pageNum, int pageSize, String recordType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<BlockchainRecord> countQ = new LambdaQueryWrapper<BlockchainRecord>()
                .eq(BlockchainRecord::getTenantId, tenantId);
        LambdaQueryWrapper<BlockchainRecord> listQ = new LambdaQueryWrapper<BlockchainRecord>()
                .eq(BlockchainRecord::getTenantId, tenantId)
                .orderByDesc(BlockchainRecord::getId);
        if (recordType != null && !recordType.isEmpty()) {
            countQ.eq(BlockchainRecord::getRecordType, recordType);
            listQ.eq(BlockchainRecord::getRecordType, recordType);
        }
        long total = recordMapper.selectCount(countQ);
        List<BlockchainRecord> rows = recordMapper.selectList(
                listQ.last("LIMIT " + pageSize + " OFFSET " + ((pageNum - 1) * pageSize)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        data.put("records", rows);
        return data;
    }

    @Override
    public String sha256Hex(String input) {
        return sha256(input);
    }

    public static String sha256(String input) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256")
                    .digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            log.error("sha256Hex failed", e);
            return Integer.toHexString(Objects.hashCode(input));
        }
    }

    @Override
    public List<Map<String, Object>> chainStats() {
        String[] chains = {CHAIN_MAIN, CHAIN_COSMETIC, CHAIN_DRUG, CHAIN_DEVICE};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String c : chains) {
            Map<String, Object> base = chainInfo(c);
            Long recentCount = recordMapper.selectCount(
                    new LambdaQueryWrapper<BlockchainRecord>()
                            .eq(BlockchainRecord::getTenantId, TenantContextHolder.get().getTenantId())
                            .apply("create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)"));
            base.put("recent24h", recentCount == null ? 0 : recentCount);
            rows.add(base);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> recordTypeStats() {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] types = {"PRODUCT", "DRUG", "DEVICE", "ORDER", "CONSENT"};
        Long tenantId = TenantContextHolder.get().getTenantId();
        for (String t : types) {
            Long count = recordMapper.selectCount(
                    new LambdaQueryWrapper<BlockchainRecord>()
                            .eq(BlockchainRecord::getTenantId, tenantId)
                            .eq(BlockchainRecord::getRecordType, t));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordType", t);
            m.put("count", count == null ? 0 : count);
            rows.add(m);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> dailyTrend(int days) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<BlockchainRecord> all = recordMapper.selectList(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .orderByAsc(BlockchainRecord::getCreateTime));
        Map<String, Long> bucket = new java.util.TreeMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String day = java.time.LocalDate.now().minusDays(i).toString();
            bucket.put(day, 0L);
        }
        for (BlockchainRecord r : all) {
            if (r.getCreateTime() == null) continue;
            String day = r.getCreateTime().toLocalDate().toString();
            if (bucket.containsKey(day)) {
                bucket.merge(day, 1L, Long::sum);
            }
        }
        for (Map.Entry<String, Long> e : bucket.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.put("count", e.getValue());
            rows.add(m);
        }
        return rows;
    }

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chains", chainStats());
        data.put("recordTypes", recordTypeStats());
        data.put("trend7d", dailyTrend(7));
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long total = recordMapper.selectCount(
                new LambdaQueryWrapper<BlockchainRecord>().eq(BlockchainRecord::getTenantId, tenantId));
        data.put("totalRecords", total == null ? 0 : total);
        return data;
    }

    private Map<String, Object> toPayloadMap(String payload) {
        if (payload == null || payload.isEmpty()) return new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JSON.readValue(payload, Map.class);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("raw", payload);
            return m;
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("toJson failed: {}", e.getMessage());
            return "{}";
        }
    }

    private List<Map<String, Object>> buildTracePath(BlockchainRecord rec) {
        List<Map<String, Object>> path = new ArrayList<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("blockIndex", rec.getBlockIndex());
        entry.put("blockHash", rec.getBlockHash());
        entry.put("payloadHash", rec.getPayloadHash());
        entry.put("timestamp", rec.getCreateTime());
        path.add(entry);
        return path;
    }
}
