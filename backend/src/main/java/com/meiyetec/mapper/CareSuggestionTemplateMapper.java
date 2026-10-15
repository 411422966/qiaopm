package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CareSuggestionTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CareSuggestionTemplateMapper extends BaseMapper<CareSuggestionTemplate> {

    @Select("SELECT * FROM biz_care_suggestion_template WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{templateType} IS NULL OR template_type = #{templateType}) " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY priority DESC, id DESC")
    List<CareSuggestionTemplate> queryList(@Param("tenantId") Long tenantId,
                                          @Param("templateType") String templateType,
                                          @Param("status") Integer status);

    @Select("SELECT * FROM biz_care_suggestion_template WHERE template_code = #{code} AND tenant_id = #{tenantId} AND deleted = 0 LIMIT 1")
    CareSuggestionTemplate findByCode(@Param("tenantId") Long tenantId,
                                      @Param("code") String code);
}
