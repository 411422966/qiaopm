package com.meiyetec.service;

import com.meiyetec.entity.CrmMemberItemPreference;
import java.util.List;
import java.util.Map;

/**
 * M15 项目偏好 Service 接口
 */
public interface ItemPreferenceService {

    /**
     * 计算并更新指定会员的偏好数据
     *
     * <p>逻辑：
     * <ol>
     *   <li>从 pos_order + pos_order_item 聚合该会员最近 N 天的服务消费</li>
     *   <li>按 serviceId 分桶，统计次数 + 金额</li>
     *   <li>归一化到 0-100 分，写入 crm_member_item_preference</li>
     *   <li>自动为 null / 已删除的偏好打分 0</li>
     * </ol>
     *
     * @param memberId 会员 ID
     * @param windowDays 统计窗口天数（默认 365）
     * @return 写入的偏好记录数
     */
    int computeForMember(Long memberId, int windowDays);

    /**
     * 批量计算（推荐在后台 Job 中调用）
     *
     * @param memberIds 会员 ID 列表
     * @param windowDays 统计窗口天数
     * @return 成功计算的会员数
     */
    int batchComputeForMembers(List<Long> memberIds, int windowDays);

    /**
     * 获取会员偏好列表（Top N，按分数降序）
     */
    List<CrmMemberItemPreference> getTopPreferences(Long memberId, Long tenantId, int limit);

    /**
     * 获取会员指定类型的偏好列表
     */
    List<CrmMemberItemPreference> getPreferencesByType(Long memberId, Long tenantId, String itemType);

    /**
     * 推荐：基于偏好返回会员最可能感兴趣的服务 ID 列表
     */
    List<Long> recommendServiceIds(Long memberId, Long tenantId, int topN);

    /**
     * 偏好分析摘要（dashboard 用）
     */
    Map<String, Object> summary(Long memberId, Long tenantId);
}
