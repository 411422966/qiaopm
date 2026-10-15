package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.TreatmentReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@link TreatmentReport} 的 MyBatis-Plus BaseMapper。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link #listByMember(Long, Long)} — 按会员历史快照倒序，给"复盘"页用</li>
 *   <li>{@link #aggregateCompletedSessionCount} — 直接用 SQL 聚合，避免 service 层
 *       selectList + size 计数的 O(N) 全表扫描</li>
 * </ul>
 */
@Mapper
public interface TreatmentReportMapper extends BaseMapper<TreatmentReport> {

    /**
     * 某会员在该租户下的历史报告，倒序按生成时间。
     */
    default List<TreatmentReport> listByMember(Long tenantId, Long memberId) {
        return selectList(new LambdaQueryWrapper<TreatmentReport>()
                .eq(TreatmentReport::getTenantId, tenantId)
                .eq(TreatmentReport::getMemberId, memberId)
                .orderByDesc(TreatmentReport::getGeneratedAt));
    }

    /**
     * 某个 progress（含 planId 过滤）下，{@code MemberTreatmentSession} 的非软删条数 = 已完成次数。
     *
     * <p>注：当前链路里 session 一旦写入即视为"完成"，没有"取消打卡"概念；如果后续引入
     * session.status 字段，本方法需追加 status='DONE' 过滤。
     */
    @Select("SELECT COUNT(*) FROM member_treatment_session " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND progress_id = #{progressId} " +
            "AND plan_id = #{planId}")
    long aggregateCompletedSessionCount(@Param("tenantId") Long tenantId,
                                        @Param("progressId") Long progressId,
                                        @Param("planId") Long planId);

    /**
     * 同一个 (tenantId, memberId) 上该 plan 的进行中 progress 数：1 表示唯一进行中疗程；0 表示未开疗程或已停用。
     *
     * <p>{@code status=1} 含义见 {@code MemberTreatmentProgress.status}（1=进行中）。
     */
    @Select("SELECT COUNT(*) FROM member_treatment_progress " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} " +
            "AND plan_id = #{planId} AND status = 1")
    long countActiveProgress(@Param("tenantId") Long tenantId,
                             @Param("memberId") Long memberId,
                             @Param("planId") Long planId);

    /**
     * 该会员下所有 progress 的"已完成 session 总数"，跨 progress 聚合。
     * 用于 effects 报告生成时不需要按 progress 拆分 session 的快速计算。
     */
    @Select("SELECT COUNT(*) FROM member_treatment_session " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} " +
            "AND plan_id = #{planId}")
    long aggregateCompletedByMemberAndPlan(@Param("tenantId") Long tenantId,
                                           @Param("memberId") Long memberId,
                                           @Param("planId") Long planId);

    /**
     * 按 planId 列出全部阶段（升序）。
     */
    @Select("SELECT * FROM biz_treatment_phase " +
            "WHERE deleted = 0 AND plan_id = #{planId} AND tenant_id = #{tenantId} " +
            "ORDER BY phase_no ASC")
    List<com.meiyetec.entity.BizTreatmentPhase> listPhasesByPlan(@Param("tenantId") Long tenantId,
                                                                  @Param("planId") Long planId);

    /**
     * 某 progress 在某阶段已服务次数。
     */
    @Select("SELECT COUNT(*) FROM member_treatment_session " +
            "WHERE deleted = 0 AND progress_id = #{progressId} AND phase_no = #{phaseNo}")
    long countSessionsInPhase(@Param("progressId") Long progressId,
                              @Param("phaseNo") Integer phaseNo);
}
