package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MemberStylePreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberStylePreferenceMapper extends BaseMapper<MemberStylePreference> {

    @Select("SELECT * FROM biz_member_style_preference WHERE deleted = 0 AND member_id = #{memberId} LIMIT 1")
    MemberStylePreference findByMember(@Param("memberId") Long memberId);
}
