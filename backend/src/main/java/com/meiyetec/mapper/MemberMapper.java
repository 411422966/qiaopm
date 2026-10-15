package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {

    /**
     * 积分 CAS（条件更新版）：仅做 balance >= 0 检查，无版本控制。
     * 由 addPoints / consumePoints 中的重试循环调用。
     */
    @Update("UPDATE crm_member " +
            "SET points = points + #{delta}, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND points + #{delta} >= 0")
    int casPoints(@Param("id") Long id, @Param("delta") Integer delta);

    /**
     * 积分 CAS（乐观锁版）：version 条件 + 余额 >= 0。
     * 优先使用此方法，出现 CONCURRENCY_CONFLICT 时重试。
     */
    @Update("UPDATE crm_member " +
            "SET points = points + #{delta}, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND points + #{delta} >= 0")
    int casPointsWithVersion(@Param("id") Long id, @Param("delta") Integer delta,
                             @Param("version") Integer version);

    /**
     * 钱包余额 CAS：余额（balance 字段）
     */
    @Update("UPDATE crm_member " +
            "SET balance = balance + #{delta}, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND balance + #{delta} >= 0")
    int casBalance(@Param("id") Long id, @Param("delta") java.math.BigDecimal delta);
}
