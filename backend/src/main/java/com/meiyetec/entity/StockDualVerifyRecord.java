package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P2-11 IND-05 双人核销记录实体
 *
 * <p>追踪盘点 / 出库 / 消耗 链路的高值双人复核签名。
 * dual_verify_status 状态机：
 *   NOT_REQUIRED → 无需复核（金额 ≤ 阈值）
 *   PENDING      → 待复核（金额 > 阈值，已扣库存）
 *   VERIFIED     → 已复核（复核人已完成签名）
 *   REJECTED     → 已驳回（复核人不通过，需走冲正）
 */
@Data
@TableName("stock_dual_verify_record")
public class StockDualVerifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;

    /** 核销类型：CHECK=盘点 OUTBOUND=出库 CONSUMPTION=消耗 */
    private String verifyType;

    /** 对应 stock_record.type：3=盘点 4=领用 2=出库 5=报损 */
    private String recordType;

    /** 关联的 stock_record.id（出库/消耗场景） */
    private Long stockRecordId;

    /** 关联的 stock_check.id（盘点场景） */
    private Long stockCheckId;

    // ---- 主核销人 ----
    private Long mainVerifierId;
    private String mainVerifierName;

    // ---- 复核人 ----
    private Long reviewerId;
    private String reviewerName;
    /** 复核人GPS/指纹 JSON: {"lat":,"lng":,"fingerprint":,"ts":} */
    private String reviewerGps;
    private LocalDateTime reviewTime;

    /** 核销状态：PENDING / VERIFIED / REJECTED */
    private String verifyStatus;

    /** 核销金额（阈值判断快照） */
    private BigDecimal totalAmount;

    /** 当时阈值（快照） */
    private BigDecimal threshold;

    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
