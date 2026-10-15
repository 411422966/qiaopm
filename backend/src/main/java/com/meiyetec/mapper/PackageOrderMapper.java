package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PackageOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PackageOrderMapper extends BaseMapper<PackageOrder> {

    /**
     * 套餐次数 CAS 扣减（version + remainingTimes >= times）
     */
    @Update("UPDATE crm_package_order " +
            "SET remaining_times = remaining_times - #{times}, " +
            "    version = version + 1, " +
            "    status = CASE WHEN remaining_times - #{times} <= 0 THEN 2 ELSE status END, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND remaining_times >= #{times} " +
            "  AND status NOT IN (3,4,5,6)")
    int casRedeem(@Param("id") Long id,
                  @Param("version") Integer version,
                  @Param("times") Integer times);

    /**
     * 套餐转赠 CAS —— 三重乐观锁守卫
     *
     * <p>三重条件必须同时成立：
     * <ul>
     *   <li>{@code version} 匹配 → 防止两个并发 transfer 同时读到 remaining=5</li>
     *   <li>{@code status = 1 (ORDER_NORMAL)} → 防止转赠已退款 / 已冻结 / 已转赠的卡</li>
     *   <li>{@code remaining_times > 0} → 防止转赠空卡</li>
     * </ul>
     *
     * <p>成功时把 status 切到 6 (ORDER_TRANSFER)、remaining 清零、version +1。
     *
     * @param id      套餐订单 ID
     * @param version 当前版本号（乐观锁）
     * @return 影响行数：1 = 成功；0 = 条件不匹配（已被并发转走 / 状态变更 / 次数清零）
     */
    @Update("UPDATE crm_package_order " +
            "SET status = 6, " +
            "    remaining_times = 0, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND status = 1 " +
            "  AND remaining_times > 0 " +
            "  AND deleted = 0")
    int casTransfer(@Param("id") Long id,
                    @Param("version") Integer version);

    /**
     * 套餐次数回退（退款冲正，乐观锁版本）。
     * 防止并发退款把同一份套餐加多次。
     */
    @Update("UPDATE crm_package_order " +
            "SET remaining_times = remaining_times + #{times}, " +
            "    version = version + 1, " +
            "    status = CASE WHEN remaining_times + #{times} > 0 THEN 1 ELSE status END, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND deleted = 0")
    int refundAddTimes(@Param("id") Long id,
                       @Param("version") Integer version,
                       @Param("times") Integer times);

    // ==================== M04-04 套餐冻结/挂起 CAS ====================

    /**
     * M04-04 · 套餐冻结 CAS（乐观锁）
     *
     * <p>四重守卫：
     * <ul>
     *   <li>{@code id} 匹配</li>
     *   <li>{@code version} 匹配（乐观锁）</li>
     *   <li>{@code status = 1 (正常)} → 防止对已冻结/已挂起/已用完/已退款的订单重复冻结</li>
     *   <li>{@code deleted = 0}</li>
     * </ul>
     *
     * <p>成功：{@code status=4}（已冻结），{@code version+1}
     */
    @Update("UPDATE crm_package_order " +
            "SET status = 4, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND status = 1 " +
            "  AND deleted = 0")
    int casFreeze(@Param("id") Long id,
                  @Param("version") Integer version);

    /**
     * M04-04 · 套餐解冻 CAS
     *
     * <p>要求当前状态为 4（已冻结），解冻后回到 1（正常）。
     */
    @Update("UPDATE crm_package_order " +
            "SET status = 1, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND status = 4 " +
            "  AND deleted = 0")
    int casUnfreeze(@Param("id") Long id,
                    @Param("version") Integer version);

    /**
     * M04-04 · 套餐挂起 CAS（"暂停使用"语义，区别于冻结的"停止使用"）
     *
     * <p>四重守卫：
     * <ul>
     *   <li>{@code id} 匹配</li>
     *   <li>{@code version} 匹配</li>
     *   <li>{@code status = 1 (正常)} → 已冻结/已挂起/已退款不能再挂起</li>
     *   <li>{@code deleted = 0}</li>
     * </ul>
     *
     * <p>成功：{@code status=8}（已挂起），{@code version+1}
     * <p>挂起期间：
     * <ul>
     *   <li>❌ 不可核销</li>
     *   <li>❌ 不可转让（转让路径会先校验 status=1）</li>
     *   <li>❌ 不可退款</li>
     *   <li>✅ 可恢复（resume 接口）</li>
     * </ul>
     */
    @Update("UPDATE crm_package_order " +
            "SET status = 8, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND status = 1 " +
            "  AND deleted = 0")
    int casSuspend(@Param("id") Long id,
                   @Param("version") Integer version);

    /**
     * M04-04 · 套餐恢复挂起 CAS
     *
     * <p>要求 {@code status = 8}（已挂起），恢复后回到 1（正常）。
     */
    @Update("UPDATE crm_package_order " +
            "SET status = 1, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND status = 8 " +
            "  AND deleted = 0")
    int casResume(@Param("id") Long id,
                  @Param("version") Integer version);
}
