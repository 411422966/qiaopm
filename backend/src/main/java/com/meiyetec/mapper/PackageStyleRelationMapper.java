package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PackageStyleRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PackageStyleRelationMapper extends BaseMapper<PackageStyleRelation> {

    @Select("SELECT r.*, sl.main_image AS style_image " +
            "FROM biz_package_style_relation r " +
            "LEFT JOIN biz_style_library sl ON r.style_id = sl.id " +
            "WHERE r.deleted = 0 AND r.package_id = #{packageId} " +
            "ORDER BY r.priority DESC, r.id ASC")
    List<PackageStyleRelation> findByPackageId(@Param("packageId") Long packageId);

    @Select("SELECT r.*, mp.package_name, sl.main_image AS style_image " +
            "FROM biz_package_style_relation r " +
            "LEFT JOIN biz_material_package mp ON r.package_id = mp.id " +
            "LEFT JOIN biz_style_library sl ON r.style_id = sl.id " +
            "WHERE r.deleted = 0 AND r.style_id = #{styleId}")
    List<PackageStyleRelation> findByStyleId(@Param("styleId") Long styleId);
}
