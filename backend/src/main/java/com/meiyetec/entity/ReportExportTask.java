package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("report_export_task")
public class ReportExportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String taskNo;
    private String reportCode;
    private String reportName;
    private LocalDate dateRangeStart;
    private LocalDate dateRangeEnd;
    private String filterJson;
    /** 1待处理 2处理中 3成功 4失败 5已取消 */
    private Integer status;
    private Integer progress;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private Integer rowCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Long operatorId;
    private String operatorName;
    private String errorMsg;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
