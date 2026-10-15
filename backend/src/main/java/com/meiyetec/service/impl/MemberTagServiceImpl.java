package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MemberTag;
import com.meiyetec.mapper.MemberTagMapper;
import com.meiyetec.service.MemberTagService;
import org.springframework.stereotype.Service;

@Service
public class MemberTagServiceImpl extends ServiceImpl<MemberTagMapper, MemberTag> implements MemberTagService {
}
