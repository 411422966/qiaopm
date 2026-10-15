package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：固件升级任务
 *
 * <p>记录每个设备的 OTA 升级进度，支持断点续传与失败回滚。
 */
@Data
@TableName("biz_dcep_firmware_upgrade_task")
public class DcepFirmwareUpgradeTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long packageId;
    private Long deviceId;
    private String deviceSerial;
    /** 任务状态：pending / downloading / verifying / installing / success / failed / cancelled */
    private String status;
    /** 已下载字节 */
    private Long downloadedBytes;
    /** 总字节 */
    private Long totalBytes;
    /** 安装进度（0~100） */
    private Integer progressPercent;
    /** 错误码（失败时） */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 重试次数 */
    private Integer retryCount;
    /** 计划升级时间 */
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
