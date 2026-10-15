package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.Booking;
import com.meiyetec.util.Result;

/**
 * P0-TEST-001：把预约创建逻辑从 Controller 下沉到 Service，
 * 保证"员工冲突检查 + 房间冲突检查 + INSERT"在同一事务内原子完成。
 */
public interface BookingService extends IService<Booking> {

    /**
     * 创建预约（含原子冲突检查）。
     *
     * <p>与 BookingController.create 的区别：
     * <ul>
     *   <li>整段逻辑 @Transactional（事务内 SELECT + INSERT）</li>
     *   <li>冲突时抛 BusinessException(BizCode.BOOKING_CONFLICT)，code=5401</li>
     *   <li>支持 P0-TEST-001 的并发场景（同一员工/房间同时段）</li>
     * </ul>
     *
     * @return 成功返回 Booking（含主键）
     * @throws com.meiyetec.exception.BusinessException BizCode.BOOKING_CONFLICT(5401) 冲突时
     */
    Result<Booking> createWithConflictCheck(Booking b);
}