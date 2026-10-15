package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.LeaveRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
public interface LeaveRequestService extends IService<LeaveRequest> {
    /**
     * 提交请假申请
     */
    LeaveRequest submit(Long tenantId, Long employeeId, String leaveType, LocalDate startDate, LocalDate endDate, String reason, Long storeId);
    /**
     * 审批请假
     */
    LeaveRequest approve(Long tenantId, Long requestId, Long approverId, String remark, boolean approved);
    /**
     * 取消请假申请
     */
    boolean cancel(Long tenantId, Long requestId, Long employeeId);
    /**
     * 获取员工的请假记录
     */
    List<LeaveRequest> getByEmployee(Long tenantId, Long employeeId);
    /**
     * 查询请假记录(带员工信息)
     */
    List<Map<String, Object>> queryRequests(Long tenantId, Long employeeId, Long storeId, String status, LocalDate startDate, LocalDate endDate);
    /**
     * 获取待审批数量
     */
    int getPendingCount(Long tenantId, Long storeId);
    /**
     * 检查日期是否与已有请假重叠
     */
    boolean hasOverlap(Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate);
}