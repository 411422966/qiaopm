package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_export_log")
public class ReportExportLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID */
    private Long tenantId;

    private Long storeId;
    private String name;
    private Integer type;
    private String dateRange;
    private String fileUrl;
    private Long fileSize;
    private Integer rowCount;
    private Integer status;        // 1处理中 2成功 3失败
    private String errorMsg;
    private Long exportBy;
    private LocalDateTime finishedAt;
    private String filePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}