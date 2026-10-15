package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.Booking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface BookingMapper extends BaseMapper<Booking> {

    /**
     * P0-TEST-001 · 悲观锁 SELECT FOR UPDATE — 防止并发预约双重插入
     *
     * <p>MyBatis-Plus 的 LambdaQueryWrapper 生成的 SELECT 是快照读（REPEATABLE READ），
     * <p>在并发场景下多个事务会同时看到 0 条冲突 → 双重插入。
     * <p>改为 FOR UPDATE 当前读，事务 B 阻塞等待事务 A 提交后再判断。
     *
     * <p>M01-04 变更：WHERE 子句增加 {@code storeId} 条件，
     * 确保跨店不算冲突（只有同一门店内的员工时间重叠才算冲突）。
     */
    Long countEmployeeConflictForUpdate(@Param("employeeId") Long employeeId,
                                         @Param("storeId") Long storeId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * M01-04 · 房间冲突检查（悲观锁 FOR UPDATE）
     *
     * <p>同样增加 {@code storeId} 条件：只有同一门店内的房间时间重叠才算冲突。
     */
    Long countRoomConflictForUpdate(@Param("roomId") Long roomId,
                                     @Param("storeId") Long storeId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
}