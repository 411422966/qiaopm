package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.HairDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface HairDetectionMapper extends BaseMapper<HairDetection> {

    @Select("SELECT * FROM biz_hair_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{memberId} IS NULL OR member_id = #{memberId}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND (#{hairType} IS NULL OR hair_type = #{hairType}) "
            + "ORDER BY detection_date DESC, create_time DESC")
    List<HairDetection> queryList(@Param("tenantId") Long tenantId,
                                  @Param("memberId") Long memberId,
                                  @Param("storeId") Long storeId,
                                  @Param("hairType") String hairType);

    @Select("SELECT * FROM biz_hair_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} "
            + "ORDER BY detection_date DESC LIMIT 1")
    HairDetection findLatestByMember(@Param("tenantId") Long tenantId,
                                     @Param("memberId") Long memberId);

    @Select("SELECT hair_type AS hairType, COUNT(*) AS count FROM biz_hair_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "GROUP BY hair_type")
    List<Map<String, Object>> statisticsByType(@Param("tenantId") Long tenantId,
                                               @Param("storeId") Long storeId);
}
