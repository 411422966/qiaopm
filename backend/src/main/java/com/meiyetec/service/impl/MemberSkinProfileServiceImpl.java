package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MemberSkinProfile;
import com.meiyetec.mapper.MemberSkinProfileMapper;
import com.meiyetec.service.MemberSkinProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberSkinProfileServiceImpl extends ServiceImpl<MemberSkinProfileMapper, MemberSkinProfile>
        implements MemberSkinProfileService {

    private final MemberSkinProfileMapper memberSkinProfileMapper;

    @Override
    public MemberSkinProfile getLatestByMember(Long memberId) {
        return memberSkinProfileMapper.findLatestByMember(memberId);
    }

    @Override
    public boolean saveOrUpdate(MemberSkinProfile profile) {
        MemberSkinProfile existing = getLatestByMember(profile.getMemberId());
        if (existing != null) {
            profile.setId(existing.getId());
            return updateById(profile);
        }
        return save(profile);
    }
}
