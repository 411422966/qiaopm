package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_backup_record")
public class DrBackupRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteId;
    /** FULL / INCREMENTAL / DIFFERENTIAL / SNAPSHOT */
    private String backupType;
    /** DB / FILES / CONFIG */
    private String backupTarget;
    private String fileUrl;
    private Long fileSizeBytes;
    private String fileHash;
    /** SUCCESS / FAILED / VERIFYING */
    private String status;
    private Integer durationSeconds;
    private String operatorName;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}