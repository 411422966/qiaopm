package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 员工考勤打卡记录
 * 记录员工每日上下班打卡信息
*/
@Data
@TableName("attendance_record")
public class AttendanceRecord {
    @TableId(type = IdType.AUTO)    private Long id;
    /** 租户ID */
    private Long tenantId;
    /** 员工ID */
    private Long employeeId;
    /** 打卡日期 */
    private LocalDate checkDate;
    /** 上班打卡时间 */
    private LocalDateTime checkInTime;
    /** 下班打卡时间 */
    private LocalDateTime checkOutTime;
    /** 上班打卡方式: app/pad/face(人脸) */
    private String checkInMethod;
    /** 下班打卡方式 */
    private String checkOutMethod;
    /** 上班打卡地点 */
    private String checkInLocation;
    /** 下班打卡地点 */
    private String checkOutLocation;
    /** 上班打卡状态: normal/late/early/absence */
    private String checkInStatus;
    /** 下班打卡状态: normal/early/absence */
    private String checkOutStatus;
    /** 是否迟到: 0-否, 1-是 */
    private Integer isLate;
    /** 迟到分钟数 */
    private Integer lateMinutes;
    /** 是否早退: 0-否, 1-是 */
    private Integer isEarlyLeave;
    /** 早退分钟数 */
    private Integer earlyLeaveMinutes;
    /** 是否旷工: 0-否, 1-是 */
    private Integer isAbsence;
    /** 出勤时长(分钟) */
    private Integer workMinutes;
    /** 备注 */
    private String remark;
    /** 考勤配置ID */
    private Long configId;
    /** 门店ID */
    private Long storeId;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;
    @TableField(exist = false)
    private String employeeName;
    @TableField(exist = false)
    private String position;
    @TableField(exist = false)
    private String storeName;
}