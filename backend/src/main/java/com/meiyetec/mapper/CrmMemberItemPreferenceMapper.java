package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CrmMemberItemPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * M15 项目偏好 Mapper
 *
 * <p>主键：(member_id, item_type, item_id, tenant_id) — 用唯一索引保证。
 */
@Mapper
public interface CrmMemberItemPreferenceMapper extends BaseMapper<CrmMemberItemPreference> {

    /**
     * 按会员 ID 查询偏好列表（按分数降序）
     */
    @Select("SELECT * FROM crm_member_item_preference " +
            "WHERE member_id = #{memberId} AND tenant_id = #{tenantId} AND deleted = 0 " +
            "ORDER BY preference_score DESC LIMIT #{limit}")
    java.util.List<CrmMemberItemPreference> selectTopByMember(
            @Param("memberId") Long memberId,
            @Param("tenantId") Long tenantId,
            @Param("limit") int limit);

    /**
     * 按会员 ID + 类型查询偏好
     */
    @Select("SELECT * FROM crm_member_item_preference " +
            "WHERE member_id = #{memberId} AND tenant_id = #{tenantId} " +
            "  AND item_type = #{itemType} AND deleted = 0 " +
            "ORDER BY preference_score DESC")
    java.util.List<CrmMemberItemPreference> selectByMemberAndType(
            @Param("memberId") Long memberId,
            @Param("tenantId") Long tenantId,
            @Param("itemType") String itemType);
}
