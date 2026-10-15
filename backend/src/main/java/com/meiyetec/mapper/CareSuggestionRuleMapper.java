package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CareSuggestionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CareSuggestionRuleMapper extends BaseMapper<CareSuggestionRule> {

    @Select("SELECT r.*, t.template_name " +
            "FROM biz_care_suggestion_rule r " +
            "LEFT JOIN biz_care_suggestion_template t ON r.template_id = t.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = #{tenantId} AND r.status = 1 " +
            "ORDER BY r.priority DESC")
    List<CareSuggestionRule> findActiveRules(@Param("tenantId") Long tenantId);

    @Select("SELECT r.*, t.template_name " +
            "FROM biz_care_suggestion_rule r " +
            "LEFT JOIN biz_care_suggestion_template t ON r.template_id = t.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = #{tenantId} " +
            "ORDER BY r.priority DESC, r.id DESC")
    List<CareSuggestionRule> findAllWithTemplate(@Param("tenantId") Long tenantId);

    @Select("SELECT * FROM biz_care_suggestion_rule " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND condition_type = #{type} AND status = 1 " +
            "ORDER BY priority DESC")
    List<CareSuggestionRule> findByConditionType(@Param("tenantId") Long tenantId,
                                                @Param("type") String type);
}
