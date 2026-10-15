package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blockchain_chain_block")
public class BlockchainChainBlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chainId;
    private Long blockIndex;
    private String blockHash;
    private String prevHash;
    private String merkleRoot;
    private Integer txCount;
    private Integer sizeBytes;
    private Integer difficulty;
    private Long nonce;
    private String miner;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime sealedAt;
}