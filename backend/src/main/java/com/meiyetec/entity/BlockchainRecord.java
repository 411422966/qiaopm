package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blockchain_record")
public class BlockchainRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** PRODUCT / DRUG / DEVICE / ORDER / CONSENT */
    private String recordType;
    private Long bizId;
    private String bizNo;
    private String blockHash;
    private String prevHash;
    private Long blockIndex;
    private String merkleRoot;
    private String payload;
    private String payloadHash;
    private Long nonce;
    private Integer difficulty;
    private String miner;
    /** POW / POS / DPOS */
    private String consensus;
    private String signature;
    private Integer verified;
    private Integer verifyCount;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}