package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SkinDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SkinDetectionMapper extends BaseMapper<SkinDetection> {

    @Select("SELECT * FROM biz_skin_detection " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{memberId} IS NULL OR member_id = #{memberId}) " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{skinType} IS NULL OR skin_type = #{skinType}) " +
            "ORDER BY detection_date DESC, create_time DESC")
    List<SkinDetection> queryList(@Param("tenantId") Long tenantId,
                                  @Param("memberId") Long memberId,
                                  @Param("storeId") Long storeId,
                                  @Param("skinType") String skinType);

    @Select("SELECT * FROM biz_skin_detection " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} " +
            "ORDER BY detection_date DESC LIMIT 1")
    SkinDetection findLatestByMember(@Param("tenantId") Long tenantId,
                                    @Param("memberId") Long memberId);

    @Select("SELECT skin_type, COUNT(*) AS count " +
            "FROM biz_skin_detection " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "GROUP BY skin_type")
    List<Map<String, Object>> statisticsByType(@Param("tenantId") Long tenantId,
                                               @Param("storeId") Long storeId);
}
