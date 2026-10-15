package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.PointRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PointRecordMapper extends BaseMapper<PointRecord> {

    IPage<PointRecord> selectMemberPoints(Page<?> page, @Param("memberId") Long memberId);

    IPage<PointRecord> selectMemberPointsByTenant(Page<?> page, @Param("memberId") Long memberId,
                                                  @Param("tenantId") Long tenantId);

    Long sumPoints(@Param("memberId") Long memberId,
                   @Param("tenantId") Long tenantId,
                   @Param("type") Integer type);
}
