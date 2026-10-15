package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CareSuggestionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CareSuggestionLogMapper extends BaseMapper<CareSuggestionLog> {

    @Select("SELECT * FROM biz_care_suggestion_log "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{memberId} IS NULL OR member_id = #{memberId}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{suggestionType} IS NULL OR suggestion_type = #{suggestionType}) "
            + "ORDER BY create_time DESC")
    List<CareSuggestionLog> queryList(@Param("tenantId") Long tenantId,
                                      @Param("memberId") Long memberId,
                                      @Param("status") String status,
                                      @Param("suggestionType") String suggestionType);

    @Select("SELECT * FROM biz_care_suggestion_log "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} AND rule_id = #{ruleId} "
            + "ORDER BY create_time DESC LIMIT 1")
    CareSuggestionLog findRecentByRule(@Param("tenantId") Long tenantId,
                                       @Param("memberId") Long memberId,
                                       @Param("ruleId") Long ruleId);

    @Update("UPDATE biz_care_suggestion_log SET status = #{status}, update_time = NOW() WHERE id = #{id} AND deleted = 0 AND tenant_id = #{tenantId}")
    int updateStatus(@Param("tenantId") Long tenantId,
                     @Param("id") Long id,
                     @Param("status") String status);
}
