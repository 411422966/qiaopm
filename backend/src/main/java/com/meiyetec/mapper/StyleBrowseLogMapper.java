package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.StyleBrowseLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StyleBrowseLogMapper extends BaseMapper<StyleBrowseLog> {

    @Select("SELECT style_id AS styleId, COUNT(*) AS view_count, MAX(action_time) AS last_view "
            + "FROM biz_style_browse_log "
            + "WHERE deleted = 0 AND member_id = #{memberId} "
            + "AND action_type IN ('view','favorite','order') "
            + "GROUP BY style_id "
            + "ORDER BY view_count DESC LIMIT ${limit}")
    List<Map<String, Object>> topBrowsedStyles(@Param("memberId") Long memberId,
                                               @Param("limit") Integer limit);

    @Select("SELECT action_type AS actionType, COUNT(*) AS count FROM biz_style_browse_log "
            + "WHERE deleted = 0 AND style_id = #{styleId} "
            + "GROUP BY action_type")
    List<Map<String, Object>> statisticsByStyle(@Param("styleId") Long styleId);
}
