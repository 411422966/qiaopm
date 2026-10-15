package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MaterialPackageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialPackageItemMapper extends BaseMapper<MaterialPackageItem> {

    @Select("SELECT * FROM biz_material_package_item "
            + "WHERE deleted = 0 AND package_id = #{packageId} ORDER BY sort_order ASC, id ASC")
    List<MaterialPackageItem> findByPackageId(@Param("packageId") Long packageId);
}
