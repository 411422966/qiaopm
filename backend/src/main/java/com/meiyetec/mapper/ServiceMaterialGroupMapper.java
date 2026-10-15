package com.meiyetec.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ServiceMaterialGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;@Mapper
public interface ServiceMaterialGroupMapper extends BaseMapper<ServiceMaterialGroup> {    @Select("SELECT smg.*, bs.service_name, " +            "(SELECT COUNT(*) FROM biz_service_material sm WHERE sm.service_id = smg.service_id AND sm.deleted = 0) AS item_count " +            "FROM biz_service_material_group smg " +            "LEFT JOIN biz_service bs ON smg.service_id = bs.id " +            "WHERE smg.deleted = 0 AND (#{serviceId} IS NULL OR smg.service_id = #{serviceId}) " +            "ORDER BY smg.priority ASC")    List<ServiceMaterialGroup> findWithDetails(@Param("serviceId") Long serviceId);}
