package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MemberProfile;
import com.meiyetec.mapper.MemberProfileMapper;
import com.meiyetec.service.MemberProfileService;
import org.springframework.stereotype.Service;

@Service
public class MemberProfileServiceImpl extends ServiceImpl<MemberProfileMapper, MemberProfile> implements MemberProfileService {
}
