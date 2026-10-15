package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BlockchainChainBlock;
import com.meiyetec.entity.BlockchainRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BlockchainChainBlockMapper;
import com.meiyetec.mapper.BlockchainRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * R3.0-H P2.5：区块链溯源服务
 *
 * <p>简易 PoW 共识区块链实现：
 * <ul>
 *   <li>支持多链（meiyetec-main / meiyetec-drug / meiyetec-device）</li>
 *   <li>PoW 难度可调（默认 2）</li>
 *   <li>链式校验：每块含 prevHash → 支持完整性校验</li>
 *   <li>支持产品 / 药品 / 器械 / 订单 / 同意书 5 类存证</li>
 * </ul>
 *
 * <p>生产应替换为 Hyperledger Fabric / FISCO BCOS / 蚂蚁链。
 */
@Slf4j
@RequiredArgsConstructor
public class BlockchainService {

    private final BlockchainRecordMapper recordMapper;
    private final BlockchainChainBlockMapper blockMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 链配置
    private static final String DEFAULT_CHAIN = "meiyetec-main";
    private static final int DEFAULT_DIFFICULTY = 2;     // 难度 = hash 前缀 0 的个数

    // ====================================================================
    // 1. 存证（核心：写入区块链）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<BlockchainRecord> record(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String recordType = (String) body.get("recordType");
        Long bizId = body.get("bizId") == null ? null : ((Number) body.get("bizId")).longValue();
        String bizNo = (String) body.get("bizNo");
        String payload = body.get("payload") == null ? null : body.get("payload").toString();
        String chainId = (String) body.getOrDefault("chainId", DEFAULT_CHAIN);
        Integer difficulty = body.get("difficulty") == null ? DEFAULT_DIFFICULTY : ((Number) body.get("difficulty")).intValue();
        if (recordType == null || bizId == null)
            return Result.error(BizCode.BAD_REQUEST, "recordType / bizId 必填");

        // 幂等：同一 bizId+recordType 不重复存证
        BlockchainRecord exist = recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
        if (exist != null) return Result.success("已存证（幂等）", exist);

        // 取上一个区块
        BlockchainChainBlock lastBlock = blockMapper.selectOne(
                new LambdaQueryWrapper<BlockchainChainBlock>()
                        .eq(BlockchainChainBlock::getChainId, chainId)
                        .orderByDesc(BlockchainChainBlock::getBlockIndex).last("LIMIT 1"));
        long blockIndex = lastBlock == null ? 0 : lastBlock.getBlockIndex() + 1;
        String prevHash = lastBlock == null ? "0".repeat(64) : lastBlock.getBlockHash();

        // 计算 payload 哈希
        String payloadHash = sha256Hex(payload == null ? "" : payload);

        // PoW 挖矿
        long nonce = 0;
        String blockHash;
        String prefix = "0".repeat(difficulty);
        while (true) {
            String concat = prevHash + ":" + blockIndex + ":" + payloadHash + ":" + nonce;
            blockHash = sha256Hex(concat);
            if (blockHash.startsWith(prefix)) break;
            nonce++;
            if (nonce > 10_000_000) {
                return Result.error(BizCode.CONFLICT, "挖矿失败");
            }
        }

        // 写入 Block
        BlockchainChainBlock block = new BlockchainChainBlock();
        block.setChainId(chainId);
        block.setBlockIndex(blockIndex);
        block.setBlockHash(blockHash);
        block.setPrevHash(prevHash);
        block.setMerkleRoot(payloadHash);
        block.setTxCount(1);
        block.setSizeBytes((payload == null ? 0 : payload.length()) + 64);
        block.setDifficulty(difficulty);
        block.setNonce(nonce);
        block.setMiner("system");
        blockMapper.insert(block);

        // 写入 Record
        BlockchainRecord rec = new BlockchainRecord();
        rec.setTenantId(tenantId);
        rec.setRecordType(recordType);
        rec.setBizId(bizId);
        rec.setBizNo(bizNo);
        rec.setBlockHash(blockHash);
        rec.setPrevHash(prevHash);
        rec.setBlockIndex(blockIndex);
        rec.setMerkleRoot(payloadHash);
        rec.setPayload(payload);
        rec.setPayloadHash(payloadHash);
        rec.setNonce(nonce);
        rec.setDifficulty(difficulty);
        rec.setMiner("system");
        rec.setConsensus("POW");
        rec.setVerified(1);
        rec.setVerifyCount(1);
        rec.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        recordMapper.insert(rec);

        return Result.success("已存证", rec);
    }

    // ====================================================================
    // 2. 存证查询
    // ====================================================================

    public Result<BlockchainRecord> getRecord(String recordType, Long bizId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BlockchainRecord rec = recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
        if (rec == null) return Result.error(BizCode.NOT_FOUND, "存证不存在");
        return Result.success(rec);
    }

    public Result<Map<String, Object>> pageRecords(int pageNum, int pageSize, String recordType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, record_type, biz_id, biz_no, block_hash, prev_hash, block_index, " +
            "       payload_hash, nonce, difficulty, miner, consensus, verified, " +
            "       operator_name, create_time " +
            "FROM blockchain_record WHERE tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (recordType != null) { sql.append("AND record_type = ? "); args.add(recordType); }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM blockchain_record WHERE tenant_id = ? ");
        List<Object> cargs = new ArrayList<>(args.subList(0, args.size() - 2));
        if (recordType != null) csql.append("AND record_type = ? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return Result.success(data);
    }

    // ====================================================================
    // 3. 链完整性校验
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> verifyChain(String chainId) {
        String cid = chainId == null ? DEFAULT_CHAIN : chainId;
        List<BlockchainChainBlock> blocks = blockMapper.selectList(
                new LambdaQueryWrapper<BlockchainChainBlock>()
                        .eq(BlockchainChainBlock::getChainId, cid)
                        .orderByAsc(BlockchainChainBlock::getBlockIndex));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chainId", cid);
        data.put("blockCount", blocks.size());

        if (blocks.isEmpty()) {
            data.put("valid", true);
            data.put("message", "链为空");
            return Result.success(data);
        }

        List<String> broken = new ArrayList<>();
        String expectedPrev = "0".repeat(64);
        for (int i = 0; i < blocks.size(); i++) {
            BlockchainChainBlock b = blocks.get(i);
            // 校验 prevHash
            if (!b.getPrevHash().equals(expectedPrev)) {
                broken.add("Block #" + b.getBlockIndex() + " prevHash 不匹配");
            }
            // 校验 hash 前缀
            String prefix = "0".repeat(b.getDifficulty());
            if (!b.getBlockHash().startsWith(prefix)) {
                broken.add("Block #" + b.getBlockIndex() + " hash 不满足难度");
            }
            // 校验 hash 正确性
            String recomputed = sha256Hex(b.getPrevHash() + ":" + b.getBlockIndex() + ":" + b.getMerkleRoot() + ":" + b.getNonce());
            if (!recomputed.equals(b.getBlockHash())) {
                broken.add("Block #" + b.getBlockIndex() + " hash 校验失败");
            }
            expectedPrev = b.getBlockHash();
        }

        data.put("valid", broken.isEmpty());
        data.put("broken", broken);
        data.put("lastBlockIndex", blocks.get(blocks.size() - 1).getBlockIndex());
        data.put("lastBlockHash", blocks.get(blocks.size() - 1).getBlockHash());
        return Result.success(data);
    }

    public Result<Map<String, Object>> chainInfo(String chainId) {
        String cid = chainId == null ? DEFAULT_CHAIN : chainId;
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM blockchain_chain_block WHERE chain_id = ?",
            Long.class, cid);
        Long latest = jdbc.queryForObject(
            "SELECT IFNULL(MAX(block_index), -1) FROM blockchain_chain_block WHERE chain_id = ?",
            Long.class, cid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chainId", cid);
        data.put("blockCount", count == null ? 0 : count);
        data.put("latestBlockIndex", latest == null ? -1 : latest);
        return Result.success(data);
    }

    // ====================================================================
    // 4. 溯源查询（按产品/药品/器械 ID）
    // ====================================================================

    public Result<Map<String, Object>> trace(String recordType, Long bizId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BlockchainRecord rec = recordMapper.selectOne(
                new LambdaQueryWrapper<BlockchainRecord>()
                        .eq(BlockchainRecord::getTenantId, tenantId)
                        .eq(BlockchainRecord::getRecordType, recordType)
                        .eq(BlockchainRecord::getBizId, bizId));
        if (rec == null) return Result.error(BizCode.NOT_FOUND, "存证不存在");

        // 沿链向前溯源：取上一个区块
        BlockchainChainBlock prev = null;
        if (rec.getBlockIndex() > 0) {
            prev = blockMapper.selectOne(
                    new LambdaQueryWrapper<BlockchainChainBlock>()
                            .eq(BlockchainChainBlock::getChainId, "meiyetec-main")
                            .eq(BlockchainChainBlock::getBlockIndex, rec.getBlockIndex() - 1));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", rec);
        data.put("prevBlock", prev);
        data.put("verified", rec.getVerified() == 1);
        data.put("tracePath", buildTracePath(rec));
        return Result.success(data);
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

    // ====================================================================
    // 5. 工具：SHA-256
    // ====================================================================

    public static String sha256Hex(String input) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public Result<Map<String, Object>> hash(String text) {
        String sha = sha256Hex(text == null ? "" : text);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", text);
        data.put("sha256", sha);
        data.put("length", sha.length());
        return Result.success(data);
    }
}
