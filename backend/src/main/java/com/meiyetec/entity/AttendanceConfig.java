package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalTime;
import java.time.LocalDateTime;
/**
 * 考勤规则配置
*/
@Data
@TableName("attendance_config")
public class AttendanceConfig {
    @TableId(type = IdType.AUTO)    private Long id;
    /** 租户ID */
    private Long tenantId;
    /** 门店ID, null表示通用配置 */
    private Long storeId;
    /** 配置名称 */
    private String configName;
    /** 上班时间 */
    private LocalTime workStartTime;
    /** 下班时间 */
    private LocalTime workEndTime;
    /** 允许迟到分钟数 */
    private Integer allowLateMinutes;
    /** 是否启用弹性打卡: 0-否, 1-是 */
    private Integer flexibleEnabled;
    /** 弹性上班开始时间(与workStartTime组合) */
    private LocalTime flexibleStartTime;
    /** 弹性上班结束时间 */
    private LocalTime flexibleEndTime;
    /** 早退警告分钟数(提前N分钟算早退) */
    private Integer earlyLeaveMinutes;
    /** 是否启用位置打卡: 0-否, 1-是 */
    private Integer locationEnabled;
    /** 打卡位置(JSON格式: [{name: \"门店\", lat: xx, lng: xx, radius: 50}]) */
    private String locationConfig;
    /** 是否启用人脸识别: 0-否, 1-是 */
    private Integer faceEnabled;
    /** 状态: 0-停用, 1-启用 */
    private Integer status;
    /** 是否删除: 0-否, 1-是 */
    @TableLogic    private Integer deleted;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;
}