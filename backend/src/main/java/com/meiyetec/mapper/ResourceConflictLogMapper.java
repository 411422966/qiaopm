package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ResourceConflictLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceConflictLogMapper extends BaseMapper<ResourceConflictLog> {

    @Select("SELECT * FROM biz_resource_conflict_log " +
            "WHERE deleted = 0 " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{conflictType} IS NULL OR conflict_type = #{conflictType}) " +
            "ORDER BY detect_time DESC")
    List<ResourceConflictLog> queryList(@Param("storeId") Long storeId,
                                        @Param("conflictType") String conflictType);
}
