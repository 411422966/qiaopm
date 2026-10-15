package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RFM 批量/示例任务执行日志（轻量版）
 *  - 不替代 sys_log / async_task，仅用于「RFM 页面历史表格」
 *  - 写时机：每次 batchCompute / seed 完成后插入一条
 */
@Data
@TableName("rfm_batch_log")
public class RfmBatchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** BATCH / SEED */
    private String action;
    private Long brandId;
    private Integer total;
    private Integer success;
    private Integer fail;
    private Integer durationMs;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}