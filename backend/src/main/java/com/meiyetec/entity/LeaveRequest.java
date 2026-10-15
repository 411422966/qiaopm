package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 员工请假申请记录
*/
@Data
@TableName("attendance_leave_request")
public class LeaveRequest {
    @TableId(type = IdType.AUTO)    private Long id;
    /** 租户ID */
    private Long tenantId;
    /** 员工ID */
    private Long employeeId;
    /** 请假类型: annual(年假)/sick(病假)/personal(事假)/marriage(婚假)/maternity(产假)/funeral(丧假)/other(其他) */
    private String leaveType;
    /** 开始日期 */
    private LocalDate startDate;
    /** 结束日期 */
    private LocalDate endDate;
    /** 请假时长(天数) */
    private Integer leaveDays;
    /** 请假原因 */
    private String reason;
    /** 申请状态: pending/approved/rejected/cancelled */
    private String status;
    /** 审批人ID */
    private Long approverId;
    /** 审批时间 */
    private LocalDateTime approveTime;
    /** 审批意见 */
    private String approveRemark;
    /** 申请门店ID */
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
    private String approverName;
    @TableField(exist = false)
    private String storeName;
    @TableField(exist = false)
    private String leaveTypeName;
}