package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DetectionSolution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DetectionSolutionMapper extends BaseMapper<DetectionSolution> {

    @Select("SELECT * FROM biz_detection_solution WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{applyType} IS NULL OR apply_type = #{applyType}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "ORDER BY priority DESC, id DESC")
    List<DetectionSolution> queryList(@Param("tenantId") Long tenantId,
                                       @Param("applyType") String applyType,
                                       @Param("status") Integer status);

    @Select("SELECT * FROM biz_detection_solution WHERE deleted = 0 AND tenant_id = #{tenantId} AND status = 1 "
            + "AND (#{applyType} IS NULL OR apply_type = #{applyType}) "
            + "AND score_threshold <= #{score} "
            + "ORDER BY priority DESC")
    List<DetectionSolution> matchByScore(@Param("tenantId") Long tenantId,
                                          @Param("applyType") String applyType,
                                          @Param("score") Integer score);
}
