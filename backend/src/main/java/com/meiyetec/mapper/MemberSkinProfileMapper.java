package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MemberSkinProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberSkinProfileMapper extends BaseMapper<MemberSkinProfile> {

    @Select("SELECT * FROM biz_member_skin_profile WHERE deleted = 0 AND member_id = #{memberId} "
            + "ORDER BY last_check_time DESC LIMIT 1")
    MemberSkinProfile findLatestByMember(@Param("memberId") Long memberId);
}
