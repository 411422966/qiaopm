package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.Booking;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BookingMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BookingService;
import com.meiyetec.util.Result;
import com.meiyetec.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl extends ServiceImpl<BookingMapper, Booking> implements BookingService {

    private static final String BOOKING_NO_PREFIX = "BK";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final IdGenerator idGenerator;

    /**
     * P0-TEST-001 · 原子创建预约
     *
     * <p>事务边界：
     * <pre>
     *   BEGIN
     *     SELECT COUNT(*) FROM biz_booking WHERE employee_id = ? AND 时间段冲突 FOR UPDATE  -- 行锁/next-key lock
     *     SELECT COUNT(*) FROM biz_booking WHERE room_id    = ? AND 时间段冲突 FOR UPDATE
     *     INSERT INTO biz_booking(...)
     *   COMMIT
     * </pre>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Booking> createWithConflictCheck(Booking b) {
        // 基本校验
        if (b.getMemberId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "会员不能为空");
        }
        if (b.getStartTime() == null || b.getEndTime() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "开始/结束时间必填");
        }
        if (b.getEndTime().isBefore(b.getStartTime()) || b.getEndTime().isEqual(b.getStartTime())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }

        // P2-13 IND-15：30 分钟精度校验（开始/结束时间分钟必须为 0 或 30）
        int startMin = b.getStartTime().getMinute();
        int endMin = b.getEndTime().getMinute();
        if (startMin % 30 != 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "开始时间必须为整点或半点（如 09:00、09:30）");
        }
        if (endMin % 30 != 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "结束时间必须为整点或半点（如 10:00、10:30）");
        }

        // 员工冲突检查（同事务内 SELECT FOR UPDATE — 悲观锁）
        // M01-04：跨店不算冲突，必须传入 storeId 强制过滤
        if (b.getEmployeeId() != null && b.getStoreId() != null) {
            Long conflict = baseMapper.countEmployeeConflictForUpdate(
                    b.getEmployeeId(), b.getStoreId(), b.getStartTime(), b.getEndTime());
            if (conflict != null && conflict > 0) {
                throw new BusinessException(BizCode.BOOKING_CONFLICT,
                        "该技师该时段已有预约，请重新选择");
            }
        }

        // 房间冲突检查（M01-04：同样按 storeId 过滤）
        if (b.getRoomId() != null && b.getStoreId() != null) {
            Long conflict = baseMapper.countRoomConflictForUpdate(
                    b.getRoomId(), b.getStoreId(), b.getStartTime(), b.getEndTime());
            if (conflict != null && conflict > 0) {
                throw new BusinessException(BizCode.BOOKING_CONFLICT,
                        "该房间该时段已被占用，请重新选择");
            }
        }

        // 设置默认值
        if (b.getBookingNo() == null || b.getBookingNo().isEmpty()) {
            // P0-MONEY-003：雪花算法 ID
            b.setBookingNo(BOOKING_NO_PREFIX + idGenerator.nextIdStr());
        }
        // T07-D2：Service 不再兜底 tenantId=1L；
        // 由 Caller（Controller / 内部 Job 等）负责从 TenantContextHolder 注入 tenantId 到 Booking 实体；
        // 若调用者未注入且 TenantContextHolder 也没有租户，则抛 UNAUTHORIZED
        Long ctxTenantId = TenantContextHolder.getTenantIdOrNull();
        if (b.getTenantId() == null) {
            if (ctxTenantId == null) {
                throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
            }
            b.setTenantId(ctxTenantId);
        }
        // M01-04：storeId 兜底注入（前端漏传时从 TenantContext 取，否则冲突检查会拿到 null 抛 NPE）
        if (b.getStoreId() == null) {
            Long ctxStoreId = TenantContextHolder.getStoreIdOrNull();
            if (ctxStoreId == null) {
                throw new BusinessException(BizCode.BAD_REQUEST, "门店不能为空");
            }
            b.setStoreId(ctxStoreId);
        }
        if (b.getStatus() == null) {
            b.setStatus(1);  // 默认待确认
        }

        boolean ok = this.save(b);
        if (!ok) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, "保存预约失败");
        }
        return Result.success("创建预约成功", b);
    }
}