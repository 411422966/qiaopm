package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.AttendanceRecord;
import com.meiyetec.entity.LeaveRequest;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.LeaveRequestMapper;
import com.meiyetec.service.AttendanceRecordService;
import com.meiyetec.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl extends ServiceImpl<LeaveRequestMapper, LeaveRequest>
        implements LeaveRequestService {

    private final LeaveRequestMapper leaveRequestMapper;
    private final AttendanceRecordService attendanceRecordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest submit(Long tenantId, Long employeeId, String leaveType, LocalDate startDate, LocalDate endDate, String reason, Long storeId) {
        if (hasOverlap(tenantId, employeeId, startDate, endDate)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "该时间段已有请假申请");
        }
        LeaveRequest request = new LeaveRequest();
        request.setTenantId(tenantId);
        request.setEmployeeId(employeeId);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setLeaveDays((int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
        request.setReason(reason);
        request.setStoreId(storeId);
        request.setStatus("pending");
        save(request);
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest approve(Long tenantId, Long requestId, Long approverId, String remark, boolean approved) {
        LeaveRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "请假申请不存在");
        }
        if (!tenantId.equals(request.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "请假申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "该申请已处理,无法重复审批");
        }
        request.setApproverId(approverId);
        request.setApproveTime(LocalDateTime.now());
        request.setApproveRemark(remark);
        request.setStatus(approved ? "approved" : "rejected");
        updateById(request);
        if (approved) {
            markLeaveDays(tenantId, request.getEmployeeId(), request.getStartDate(), request.getEndDate());
        }
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long tenantId, Long requestId, Long employeeId) {
        LeaveRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "请假申请不存在");
        }
        if (!tenantId.equals(request.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "请假申请不存在");
        }
        if (!request.getEmployeeId().equals(employeeId)) {
            throw new BusinessException(BizCode.FORBIDDEN, "无权取消他人的申请");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "该申请已处理,无法取消");
        }
        request.setStatus("cancelled");
        return updateById(request);
    }

    @Override
    public List<LeaveRequest> getByEmployee(Long tenantId, Long employeeId) {
        return leaveRequestMapper.findByEmployeeId(tenantId, employeeId);
    }

    @Override
    public List<Map<String, Object>> queryRequests(Long tenantId, Long employeeId, Long storeId, String status, LocalDate startDate, LocalDate endDate) {
        return leaveRequestMapper.queryRequests(tenantId, storeId, employeeId, status, startDate, endDate);
    }

    @Override
    public int getPendingCount(Long tenantId, Long storeId) {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LeaveRequest::getTenantId, tenantId);
        wrapper.eq(LeaveRequest::getStoreId, storeId);
        wrapper.eq(LeaveRequest::getStatus, "pending");
        return (int) count(wrapper);
    }

    @Override
    public boolean hasOverlap(Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate) {
        Integer count = leaveRequestMapper.countOverlapLeave(tenantId, employeeId, startDate, endDate);
        return count != null && count > 0;
    }

    private void markLeaveDays(Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            AttendanceRecord record = attendanceRecordService.getByEmployeeAndDate(tenantId, employeeId, current);
            if (record == null) {
                record = new AttendanceRecord();
                record.setTenantId(tenantId);
                record.setEmployeeId(employeeId);
                record.setCheckDate(current);
                record.setIsAbsence(0);
                record.setStoreId(1L);
            }
            current = current.plusDays(1);
        }
    }
}